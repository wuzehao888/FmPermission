package com.wzh.fmpermission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * @Description:    权限
 * @Author:         Wzh
 * @CreateDate:     2021/3/18 10:19
 */
class FmPermission(private val callback: Callback) {

    companion object {
        /**
         * 检测权限
         */
        fun checkSelfPermission(context: Context, permissions: Array<out String>): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (permissions.find { it.isEmpty() } != null) return false
                var isApplySuccess = true
                kotlin.run check@{
                    permissions.forEach {
                        val result = ContextCompat.checkSelfPermission(context, it)
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            isApplySuccess = false
                            return@check
                        }
                    }
                }
                return isApplySuccess
            } else {
                return true
            }
        }
    }

    private var activity: Activity? = null

    //存放进入设置修改权限
    private val settingRequestMap by lazy { mutableMapOf<Int, Array<out String>>() }

    /**
     * 授权不再询问
     */
    fun authorizeNoAsk(context: Activity, permissions: Array<out String>, requestCode: Int) {
        settingRequestMap[requestCode] = permissions
        //app详情意图
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        //判断是否有这个意图
        val pm = context.packageManager
        val resolveInfos =
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfos.size > 0) {//表示有这个意图
            context.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * 申请权限
     */
    fun applyPermission(activity: Activity, permissions: Array<out String>, requestCode: Int) {
        if (checkSelfPermission(activity, permissions)) {
            callback.permissionGrant(requestCode)
        } else {
            this.activity = activity
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        }
    }

    /**
     * 申请悬浮权限
     */
    fun applyOverlayPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.activity = activity
            if (Settings.canDrawOverlays(activity)) {
                callback.permissionGrant(requestCode)
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:${activity.packageName}")
                val resolveInfos =
                    activity.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )
                if (resolveInfos.size > 0) {
                    settingRequestMap[requestCode] =
                        arrayOf(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    activity.startActivityForResult(intent, requestCode)
                }
            }
        } else {
            callback.permissionGrant(requestCode)
        }
    }

    /**
     * 请求权限回调的结果
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val deniedPermissions = mutableListOf<String>()
        //遍历权限是否被授予
        permissions.forEachIndexed { index, permission ->
            if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {//权限不通过
                deniedPermissions.add(permission)
            }
        }
        //判断是否有权限不被授予
        if (deniedPermissions.isEmpty()) {//权限全部通过
            callback.permissionGrant(requestCode)
        } else {
            //获取不再询问的权限
            val rationalPermissions = mutableListOf<String>()
            deniedPermissions.forEach { permission ->
                activity?.let {
                    val rationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
                    if (!rationale) {//不再询问
                        rationalPermissions.add(permission)
                    }
                }
            }
            if (rationalPermissions.isEmpty()) {
                callback.permissionDenied(requestCode, deniedPermissions.toTypedArray(), false)
            } else {
                callback.permissionDenied(requestCode, rationalPermissions.toTypedArray(), true)
            }
        }
    }

    /**
     * 设置后回调
     */
    fun onActivityResult(requestCode: Int) {
        if (settingRequestMap.isEmpty()) return
        //请求码不在集合中
        if (requestCode !in settingRequestMap.keys) return

        val permissions = settingRequestMap[requestCode]
        if (permissions != null && permissions.isNotEmpty() && activity != null) {
            if (permissions[0] == Settings.ACTION_MANAGE_OVERLAY_PERMISSION) {//是否是悬浮窗权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(activity)) {
                        callback.permissionGrant(requestCode)
                    }
                } else {
                    callback.permissionGrant(requestCode)
                }
            } else {
                //判断权限是否通过
                if (checkSelfPermission(activity!!, permissions)) {
                    //权限通过
                    callback.permissionGrant(requestCode)
                }
            }
        }
    }

    /**
     * 销毁
     */
    fun onDestroy() {
        activity = null
        settingRequestMap.clear()
    }

    interface Callback {
        /**
         * 权限申请成功
         */
        fun permissionGrant(requestCode: Int)

        /**
         * 权限被拒绝
         * @param   permissions 权限
         * @param   noAsk   是否不再询问，true表示不再询问
         */
        fun permissionDenied(requestCode: Int, permissions: Array<out String>, noAsk: Boolean)
    }
}