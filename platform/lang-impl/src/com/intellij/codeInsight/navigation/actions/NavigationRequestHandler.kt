// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.annotations.ApiStatus.Internal


@Internal
interface NavigationRequestHandler {
  // TODO add comment and suspend
  fun navigateRequest(project: Project, request: NavigationRequest)
  fun navigate(usage: Object)
  suspend fun navigate(project: Project, navigatable: Navigatable, options: NavigationOptions = NavigationOptions.defaultOptions())
  companion object {
    @JvmField
    val DEFAULT: NavigationRequestHandler = DefaultNavigationRequestHandler()
  }
}

@Internal
internal class DefaultNavigationRequestHandler : NavigationRequestHandler {
  override fun navigateRequest(project: Project, request: NavigationRequest) {
    when (request) {
      is SourceNavigationRequest -> {
        // TODO support pure source request without OpenFileDescriptor
        val offset = request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: -1
        val openFileDescriptor = OpenFileDescriptor(project, request.file, offset)
        if (UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")) {
          openFileDescriptor.isUsePreviewTab = true
        }
        FileNavigator.getInstance().navigate(openFileDescriptor, true)
      }
      is DirectoryNavigationRequest -> {
        PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, true)
      }
      is RawNavigationRequest -> {
        request.navigatable.navigate(true)
      }
      else -> {
        error("unsupported request ${request.javaClass.name}")
      }
    }
  }

  override fun navigate(usage: Object) {
    when (usage) {
      is UsageInfo -> {
        UsageViewUtil.navigateTo(usage, true)
      }
      is Navigatable -> {
        usage.navigate(true)
      }
      else -> {
        error("unsupported usage navigation ${usage}")
      }
    }
  }

  override suspend fun navigate(project: Project, usage: Navigatable, options: NavigationOptions) {
    NavigationService.getInstance(project).navigate(usage, options)
  }
}