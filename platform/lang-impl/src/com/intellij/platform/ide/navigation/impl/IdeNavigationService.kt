// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.codeInsight.navigation.NavigationUtil.activateFileIfOpen
import com.intellij.codeInsight.navigation.NavigationUtil.shouldOpenAsNative
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils.isAsyncDataContext
import com.intellij.openapi.actionSystem.impl.Utils.wrapToAsyncDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.mapWithProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.pom.Navigatable
import com.intellij.util.OverflowSemaphore
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
internal class IdeNavigationService(private val project: Project) : NavigationService {

  /**
   * - `permits = 1` means at any given time only 1 request is being handled.
   * - [BufferOverflow.DROP_OLDEST] makes each new navigation request cancel the previous one.
   */
  private val semaphore: OverflowSemaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_OLDEST)

  override suspend fun navigate(ctx: DataContext, options: NavigationOptions) {
    if (!isAsyncDataContext(ctx)) {
      LOG.error("Expected async context, got: $ctx")
      val asyncContext = withContext(Dispatchers.EDT) {
        // hope that context component is still available
        wrapToAsyncDataContext(ctx)
      }
      navigate(asyncContext, options)
    }
    return semaphore.withPermit {
      val navigatables = readAction {
        ctx.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
      }
      if (!navigatables.isNullOrEmpty()) {
        doNavigate(navigatables.toList(), options)
      }
    }
  }

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean {
    return semaphore.withPermit {
      doNavigate(navigatables, options)
    }
  }

  private suspend fun doNavigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean {
    val requests = navigatables.mapWithProgress(concurrent = true) {
      readAction {
        it.navigationRequest()
      }
    }.filterNotNull()
    return withContext(Dispatchers.EDT) {
      blockingContext {
        navigate(project, requests, options)
      }
    }
  }

  override suspend fun navigate(navigatable: Navigatable, options: NavigationOptions): Boolean {
    return semaphore.withPermit {
      val request = readAction {
        navigatable.navigationRequest()
      }
      if (request == null) {
        false
      }
      else {
        withContext(Dispatchers.EDT) {
          blockingContext {
            navigateToSource(project, request, options as NavigationOptions.Impl)
          }
        }
      }
    }
  }
}

internal val LOG = Logger.getInstance("#com.intellij.platform.ide.navigation.impl")

/**
 * Navigates to all sources from [requests], or navigates to first non-source request.
 */
private fun navigate(project: Project, requests: List<NavigationRequest>, options: NavigationOptions): Boolean {
  EDT.assertIsEdt()

  val maxSourceRequests = Registry.intValue("ide.source.file.navigation.limit", 100)
  var nonSourceRequest: NavigationRequest? = null

  options as NavigationOptions.Impl
  var navigatedSourcesCounter = 0
  for (requestFromNavigatable in requests) {
    if (maxSourceRequests in 1..navigatedSourcesCounter) {
      break
    }
    if (navigateToSource(project, requestFromNavigatable, options)) {
      navigatedSourcesCounter++
    }
    else if (nonSourceRequest == null) {
      nonSourceRequest = requestFromNavigatable
    }
  }
  if (navigatedSourcesCounter > 0) {
    return true
  }
  if (nonSourceRequest == null) {
    return false
  }
  navigateNonSource(nonSourceRequest, options)
  return true
}

private fun navigateToSource(project: Project, request: NavigationRequest, options: NavigationOptions.Impl): Boolean {
  EDT.assertIsEdt()

  when (request) {
    is SourceNavigationRequest -> {
      navigateToSource(project, request, options)
      return true
    }
    is DirectoryNavigationRequest -> {
      return false
    }
    is RawNavigationRequest -> {
      if (request.canNavigateToSource) {
        request.navigatable.navigate(options.requestFocus)
        return true
      }
      else {
        return false
      }
    }
    else -> {
      error("Unsupported request: $request")
    }
  }
}

private fun navigateToSource(project: Project, request: SourceNavigationRequest, options: NavigationOptions.Impl) {
  EDT.assertIsEdt()

  if (tryActivateOpenFile(project, request, options)) {
    return
  }
  // TODO support pure source request without OpenFileDescriptor
  val offset = request.elementRangeMarker?.takeIf { it.isValid }?.startOffset ?: -1
  val openFileDescriptor = OpenFileDescriptor(project, request.file, offset)
  openFileDescriptor.isUseCurrentWindow = true
  if (UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")) {
    openFileDescriptor.isUsePreviewTab = true
  }
  openFileDescriptor.navigate(options.requestFocus)
}

private fun tryActivateOpenFile(
  project: Project,
  request: SourceNavigationRequest,
  options: NavigationOptions.Impl,
): Boolean {
  if (!options.preserveCaret && !options.requestFocus) {
    return false
  }
  if (shouldOpenAsNative(request.file)) {
    return false
  }
  val elementRangeMarker = request.elementRangeMarker
  if (elementRangeMarker == null || !elementRangeMarker.isValid) {
    return false
  }
  val elementRange = elementRangeMarker.textRange
  return activateFileIfOpen(project, request.file, elementRange, options.requestFocus, options.requestFocus)
}

private fun navigateNonSource(request: NavigationRequest, options: NavigationOptions.Impl) {
  EDT.assertIsEdt()

  return when (request) {
    is DirectoryNavigationRequest -> {
      PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, options.requestFocus)
    }
    is RawNavigationRequest -> {
      check(!request.canNavigateToSource)
      request.navigatable.navigate(options.requestFocus)
    }
    else -> {
      error("Non-source request expected here, got: $request")
    }
  }
}
