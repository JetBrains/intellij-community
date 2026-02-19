// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.client

import com.intellij.compiler.cache.client.JpsServerAuthExtension.Companion.getInstance
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class CompilerCacheServerAuthService(private val project: Project, private val scope: CoroutineScope) : Disposable {
  fun getRequestHeaders(): Map<String, String> {
    return getRequestHeaders(false)
  }

  fun getRequestHeaders(force: Boolean): Map<String, String> {
    val authExtension = getInstance()
    if (authExtension == null) {
      ApplicationManager.getApplication().invokeLater {
        JpsServerAuthExtension.notifyMissingRequiredPlugin(project)
      }
      return emptyMap()
    }
    val authHeader = scope.async(Dispatchers.IO) {
      authExtension.getAuthHeaders(force)
    }.asCompletableFuture().get(10, TimeUnit.SECONDS)
    if (authHeader == null) {
      scope.launch {
        JpsServerAuthExtension.checkAuthenticated(getInstance(project), project) {
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

  override fun dispose() {
  }
}
