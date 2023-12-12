// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.codeInsight.navigation.activateFileIfOpen
import com.intellij.codeInsight.navigation.shouldOpenAsNative
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.Utils.isAsyncDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.pom.Navigatable
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
        Utils.createAsyncDataContext(ctx)
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
    return navigate(project = project, requests = requests, options = options)
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
        navigateToSource(project = project, request = request, options = options as NavigationOptions.Impl)
      }
    }
  }
}

internal val LOG: Logger = Logger.getInstance("#com.intellij.platform.ide.navigation.impl")

/**
 * Navigates to all sources from [requests], or navigates to first non-source request.
 */
private suspend fun navigate(project: Project, requests: List<NavigationRequest>, options: NavigationOptions): Boolean {
  val maxSourceRequests = Registry.intValue("ide.source.file.navigation.limit", 100)
  var nonSourceRequest: NavigationRequest? = null

  options as NavigationOptions.Impl
  var navigatedSourcesCounter = 0
  for (requestFromNavigatable in requests) {
    if (maxSourceRequests in 1..navigatedSourcesCounter) {
      break
    }
    if (navigateToSource(project = project, request = requestFromNavigatable, options = options)) {
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

  withContext(Dispatchers.EDT) {
    navigateNonSource(project = project, request = nonSourceRequest, options = options)
  }
  return true
}

private suspend fun navigateToSource(project: Project, request: NavigationRequest, options: NavigationOptions.Impl): Boolean {
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
        withContext(Dispatchers.EDT) {
          IdeNavigationServiceExecutor.getInstance(project).navigate(request, options.requestFocus)
        }
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

private suspend fun navigateToSource(project: Project, request: SourceNavigationRequest, options: NavigationOptions.Impl) {
  if (tryActivateOpenFile(project = project, request = request, options = options)) {
    return
  }

  // TODO support pure source request without OpenFileDescriptor
  val offset = request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: -1
  val openFileDescriptor = OpenFileDescriptor(project, request.file, offset)
  openFileDescriptor.isUseCurrentWindow = true
  if (UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")) {
    openFileDescriptor.isUsePreviewTab = true
  }

  withContext(Dispatchers.EDT) {
    blockingContext {
      openFileDescriptor.navigate(options.requestFocus)
    }
  }
}

private suspend fun tryActivateOpenFile(project: Project, request: SourceNavigationRequest, options: NavigationOptions.Impl): Boolean {
  if (!options.preserveCaret && !options.requestFocus) {
    return false
  }
  if (shouldOpenAsNative(request.file)) {
    return false
  }

  val elementRange = request.elementRangeMarker?.takeIf { it.isValid }?.textRange  ?: return false
  return activateFileIfOpen(project = project,
                            vFile = request.file,
                            range = elementRange,
                            openOptions = FileEditorOpenOptions(requestFocus = options.requestFocus, reuseOpen = options.requestFocus))
}

private suspend fun navigateNonSource(project: Project, request: NavigationRequest, options: NavigationOptions.Impl) {
  EDT.assertIsEdt()

  return when (request) {
    is DirectoryNavigationRequest -> {
      blockingContext {
        PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, options.requestFocus)
      }
    }
    is RawNavigationRequest -> {
      check(!request.canNavigateToSource)
      IdeNavigationServiceExecutor.getInstance(project).navigate(request, options.requestFocus)
    }
    else -> {
      error("Non-source request expected here, got: $request")
    }
  }
}
