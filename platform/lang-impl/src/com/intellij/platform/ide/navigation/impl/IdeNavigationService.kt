// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.Utils.isAsyncDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.navigateAndSelectEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.INativeFileType
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
import com.intellij.util.containers.sequenceOfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext

private class IdeNavigationService(private val project: Project) : NavigationService {
  /**
   * - `permits = 1` means at any given time only one request is being handled.
   * - [BufferOverflow.DROP_OLDEST] makes each new navigation request cancel the previous one.
   */
  private val semaphore: OverflowSemaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_OLDEST)

  override suspend fun navigate(dataContext: DataContext, options: NavigationOptions) {
    if (!isAsyncDataContext(dataContext)) {
      LOG.error("Expected async context, got: $dataContext")
      val asyncContext = withContext(Dispatchers.EDT) {
        // hope that context component is still available
        Utils.createAsyncDataContext(dataContext)
      }
      navigate(asyncContext, options)
    }
    return semaphore.withPermit {
      val navigatables = readAction {
        dataContext.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
      }
      if (!navigatables.isNullOrEmpty()) {
        doNavigate(navigatables = navigatables.toList(), options = options, dataContext = dataContext)
      }
    }
  }

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean {
    return semaphore.withPermit {
      doNavigate(navigatables, options, dataContext = null)
    }
  }

  private suspend fun doNavigate(navigatables: List<Navigatable>, options: NavigationOptions, dataContext: DataContext?): Boolean {
    val requests = navigatables.mapWithProgress {
      readAction {
        it.navigationRequest()
      }
    }.filterNotNull()
    return navigate(project = project, requests = requests, options = options, dataContext = dataContext)
  }

  override suspend fun navigate(navigatable: Navigatable, options: NavigationOptions): Boolean {
    return semaphore.withPermit {
      val request = readAction {
        navigatable.navigationRequest()
      } ?: return@withPermit false
      navigate(project = project, requests = listOf(request), options = options, dataContext = null)
    }
  }

  override suspend fun navigate(request: NavigationRequest, options: NavigationOptions) {
    if (request is SourceNavigationRequest) {
      navigateToSource(project = project, request = request, options = options as NavigationOptions.Impl, dataContext = null)
    }
    else {
      navigate(project = project, requests = listOf(request), options = options, dataContext = null)
    }
  }
}

private val LOG: Logger = Logger.getInstance("#com.intellij.platform.ide.navigation.impl")

/**
 * Navigates to all sources from [requests], or navigates to first non-source request.
 */
private suspend fun navigate(project: Project, requests: List<NavigationRequest>, options: NavigationOptions, dataContext: DataContext?): Boolean {
  val maxSourceRequests = if (requests.size == 1) Int.MAX_VALUE else Registry.intValue("ide.source.file.navigation.limit", 100)
  var nonSourceRequest: NavigationRequest? = null

  options as NavigationOptions.Impl
  var navigatedSourcesCounter = 0
  for (requestFromNavigatable in requests) {
    if (maxSourceRequests in 1..navigatedSourcesCounter) {
      break
    }
    if (navigateToSource(project = project, request = requestFromNavigatable, options = options, dataContext = dataContext)) {
      navigatedSourcesCounter++
    }
    else if (nonSourceRequest == null) {
      nonSourceRequest = requestFromNavigatable
    }
  }

  if (navigatedSourcesCounter > 0) {
    return true
  }
  if (nonSourceRequest == null || options.sourceNavigationOnly) {
    return false
  }

  navigateNonSource(project = project, request = nonSourceRequest, options = options)
  return true
}

private suspend fun navigateToSource(
  project: Project,
  request: NavigationRequest,
  options: NavigationOptions.Impl,
  dataContext: DataContext?,
): Boolean {
  when (request) {
    is SourceNavigationRequest -> {
      navigateToSource(
        request = request,
        options = options,
        project = project,
        dataContext = dataContext,
      )
      return true
    }
    is DirectoryNavigationRequest -> {
      return false
    }
    is RawNavigationRequest -> {
      if (request.canNavigateToSource) {
        project.serviceAsync<IdeNavigationServiceExecutor>().navigate(request = request, requestFocus = options.requestFocus)
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

private suspend fun navigateNonSource(project: Project, request: NavigationRequest, options: NavigationOptions.Impl) {
  return when (request) {
    is DirectoryNavigationRequest -> {
      withContext(Dispatchers.EDT) {
        blockingContext {
          PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, options.requestFocus)
        }
      }
    }
    is RawNavigationRequest -> {
      check(!request.canNavigateToSource)
      project.serviceAsync<IdeNavigationServiceExecutor>().navigate(request, options.requestFocus)
    }
    else -> {
      error("Non-source request expected here, got: $request")
    }
  }
}

private suspend fun navigateToSource(
  options: NavigationOptions.Impl,
  request: SourceNavigationRequest,
  project: Project,
  dataContext: DataContext?,
) {
  val file = request.file
  val type = if (file.isDirectory) null else FileTypeManager.getInstance().getKnownFileTypeOrAssociate(file, project)
  if (type != null && file.isValid) {
    if (type is INativeFileType) {
      if (blockingContext { type.openFileInAssociatedApplication(project, file) }) {
        return
      }
    }
    else {
      if (dataContext != null) {
        val descriptor = OpenFileDescriptor(project, request.file, request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: -1)
        descriptor.isUseCurrentWindow = true
        if (UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")) {
          descriptor.isUsePreviewTab = true
        }

        val fileNavigator = serviceAsync<FileNavigator>()
        if (fileNavigator is FileNavigatorImpl && fileNavigator.navigateInRequestedEditorAsync(descriptor, dataContext)) {
          return
        }
      }

      if (openFile(request = request, project = project, options = options)) {
        return
      }
    }
  }

  navigateInProjectView(file = file, requestFocus = options.requestFocus, project = project)
}

private suspend fun openFile(
  options: NavigationOptions.Impl,
  project: Project,
  request: SourceNavigationRequest,
): Boolean {
  var offset = request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: -1
  val originalFile = request.file
  var file = originalFile

  val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerEx
  if (originalFile is VirtualFileWindow) {
    readAction {
      offset = originalFile.documentWindow.injectedToHost(offset)
      file = originalFile.delegate
    }
  }

  val composite = fileEditorManager.openFile(
    file = file,
    options = FileEditorOpenOptions(
      reuseOpen = true,
      requestFocus = options.requestFocus,
      openMode = if (options.openInRightSplit) FileEditorManagerImpl.OpenMode.RIGHT_SPLIT else FileEditorManagerImpl.OpenMode.DEFAULT,
    ),
  )

  val fileEditors = composite.allEditors
  if (fileEditors.isEmpty()) {
    return false
  }

  val elementRange = if (options.preserveCaret) request.elementRangeMarker?.takeIf { it.isValid }?.textRange else null
  if (elementRange != null) {
    for (editor in fileEditors) {
      if (editor is TextEditor) {
        val text = editor.editor
        if (elementRange.containsOffset(readAction { text.caretModel.offset })) {
          return true
        }
      }
    }
  }


  if (offset == -1) {
    return true
  }

  val descriptor = OpenFileDescriptor(project, file, offset)
  suspend fun tryNavigate(fileEditors: Sequence<FileEditor>): Boolean {
    for (editor in fileEditors) {
      // try to navigate opened editor
      if (editor is NavigatableFileEditor) {
        val navigated = withContext(Dispatchers.EDT) {
          //todo: try read action only
          writeIntentReadAction {
            navigateAndSelectEditor(editor = editor, descriptor = descriptor, composite = composite as? EditorComposite)
          }
        }
        if (navigated) {
          return true
        }
      }
    }
    return false
  }

  val selected = (composite as? EditorComposite)?.selectedWithProvider?.fileEditor
  return tryNavigate(sequenceOfNotNull(selected)) || tryNavigate(fileEditors.asSequence().filter { it != selected })
}