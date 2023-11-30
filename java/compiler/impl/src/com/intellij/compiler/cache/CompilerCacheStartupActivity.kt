// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache

import com.intellij.compiler.cache.client.CompilerCacheServerAuthService
import com.intellij.compiler.cache.client.JpsServerAuthExtension
import com.intellij.compiler.cache.git.GitRepositoryUtil
import com.intellij.compiler.cache.ui.CompilerCacheNotifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry

internal class CompilerCacheStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!Registry.`is`("compiler.process.use.portable.caches")) {
      thisLogger().debug("JPS Caches registry key is not enabled")
      return
    }
    if (!CompilerCacheConfigurator.isServerUrlConfigured(project)) {
      thisLogger().debug("Not an Intellij project, JPS Caches will not be available")
      return
    }
    JpsServerAuthExtension.checkAuthenticated(CompilerCacheServerAuthService.getInstance(project), project) {
      thisLogger().info("User authentication for JPS Cache download complete successfully")
    }
    checkWindowsCRLF(project)
  }

  companion object {
    @JvmStatic
    var isLineEndingsConfiguredCorrectly = true
      private set

    private fun checkWindowsCRLF(project: Project) {
      if (!SystemInfo.isWindows) {
        return
      }
      if (!GitRepositoryUtil.isAutoCrlfSetRight(project)) {
        isLineEndingsConfiguredCorrectly = false
        CompilerCacheNotifications.ATTENTION
          .createNotification(JavaCompilerBundle.message("notification.title.git.crlf.config"),
                              JavaCompilerBundle.message("notification.content.git.crlf.config", "git config --global core.autocrlf input"),
                              NotificationType.WARNING)
          .notify(project)
      }
    }
  }
}