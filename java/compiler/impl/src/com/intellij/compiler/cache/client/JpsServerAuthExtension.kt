// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.client

import com.intellij.compiler.cache.ui.CompilerCacheNotifications
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extension point which provides authentication data for requests to the JPS cache server
 */
interface JpsServerAuthExtension {
  /**
   * This method should check if the user was authenticated, if not it should do any needed actions to provide
   * auth token for further requests. This method will be called outside the EDT and should be asynchronous.
   * If the user was authenticated the callback should be invoked.
   *
   * @param presentableReason reason for the token request
   * @param parentDisposable controls the lifetime of the authentication
   * @param onAuthCompleted callback on authentication complete, if token already exists it also should be invoked
   */
  fun checkAuthenticated(presentableReason: String, parentDisposable: Disposable, onAuthCompleted: Runnable)

  /**
   * The method provides HTTP authentication headers for the requests to the server.
   * It will be called in the background thread. The assertion that thread isn't EDT can
   * be added to the implementation.
   * @return Map with header name as key and token. If it's not possible to get the authentication
   * headers, `null` will be returned.
   */
  fun getAuthHeader(force: Boolean): Map<String, String>?

  companion object {
    private val NOTIFICATION_SHOWN_KEY = Key.create<Boolean>("AUTH_NOTIFICATION_SHOWN")
    val EP_NAME = ExtensionPointName<JpsServerAuthExtension>("com.intellij.jpsServerAuthExtension")

    fun getInstance(): JpsServerAuthExtension? = EP_NAME.extensionList.firstOrNull()

    suspend fun checkAuthenticated(parentDisposable: Disposable, project: Project, onAuthCompleted: Runnable) {
      val disposable = Disposer.newDisposable()
      Disposer.register(parentDisposable, disposable)
      val authExtension = getInstance()
      if (authExtension == null) {
        val userData = project.getUserData(NOTIFICATION_SHOWN_KEY)
        if (userData == null) {
          project.putUserData(NOTIFICATION_SHOWN_KEY, java.lang.Boolean.TRUE)
          withContext(Dispatchers.EDT) {
            CompilerCacheNotifications.ATTENTION
              .createNotification(JavaCompilerBundle.message("notification.title.jps.caches.downloader"),
                                  JavaCompilerBundle.message(
                                    "notification.content.internal.authentication.plugin.required.for.correct.work"),
                                  NotificationType.WARNING)
              .setListener(NotificationListener.URL_OPENING_LISTENER)
              .notify(project)
          }
        }
        thisLogger().warn("JetBrains Internal Authentication plugin is required for the correct work. Please enable it.")
        return
      }
      withContext(Dispatchers.IO) {
        authExtension.checkAuthenticated("Jps Caches Downloader", disposable, Runnable {
          Disposer.dispose(disposable)
          onAuthCompleted.run()
        })
      }
    }
  }
}