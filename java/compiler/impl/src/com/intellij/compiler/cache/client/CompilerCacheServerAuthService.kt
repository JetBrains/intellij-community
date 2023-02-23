// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.client

import com.intellij.compiler.cache.client.JpsServerAuthExtension.Companion.getInstance
import com.intellij.compiler.cache.ui.CompilerCacheNotifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class CompilerCacheServerAuthService(private val project: Project, private val scope: CoroutineScope) {
  fun getRequestHeaders(): Map<String, String> {
    return getRequestHeaders(false)
  }

  fun getRequestHeaders(force: Boolean): Map<String, String> {
    val authExtension = getInstance()
    if (authExtension == null) {
      val message = JavaCompilerBundle.message("notification.content.internal.authentication.plugin.required.for.correct.work")
      ApplicationManager.getApplication().invokeLater {
        CompilerCacheNotifications.ATTENTION.createNotification(JavaCompilerBundle.message("notification.title.jps.caches.downloader"),
                                                                message, NotificationType.WARNING).notify(project)
      }
      return emptyMap()
    }
    val authHeader = authExtension.getAuthHeader(force)
    if (authHeader == null) {
      scope.launch {
        JpsServerAuthExtension.checkAuthenticated(project, project) {
          thisLogger().info("User authentication for JPS Cache download complete successfully")
        }
      }
      return emptyMap()
    }
    return authHeader
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CompilerCacheServerAuthService = project.service()
  }
}
