// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileNavigator
import com.intellij.openapi.fileEditor.FileNavigatorImpl
import com.intellij.openapi.fileEditor.NavigatableFileEditor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.editorSuppressionCoroutineContext
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.navigateAndSelectEditor
import com.intellij.openapi.fileEditor.navigateInProjectView
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.ide.navigation.CaretPlacement
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.navigation.NavigationTaskCoordinator
import com.intellij.platform.ide.navigation.RequestedEditor
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.platform.util.progress.mapWithProgress
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.sequenceOfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
internal class IdeNavigationService(private val project: Project) : NavigationService {
  /**
   * - `permits = 1` means at any given time only one request is being handled.
   * - [BufferOverflow.DROP_OLDEST] makes each new navigation request cancel the previous one.
   */
  private val semaphore: OverflowSemaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.DROP_OLDEST)
  private val isInNavigation: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

  private val taskCoordinator: NavigationTaskCoordinator
    get() = NavigationTaskCoordinator.getInstance(project)

  override suspend fun navigateRequests(options: NavigationOptions, supplier: suspend () -> Collection<NavigationRequest>): Boolean {
    return doExclusively {
      val requests = withContext(Dispatchers.Default) { supplier() }
      requests.isNotEmpty() && withHistoryIfNeeded(options) {
        navigate(project = project, requests = requests, options = options)
      }
    }
  }

  override suspend fun navigate(options: NavigationOptions, supplier: suspend () -> Collection<Navigatable>): Boolean {
    return doExclusively {
      val navigatables = withContext(Dispatchers.Default) { supplier() }
      navigatables.isNotEmpty() && doNavigate(navigatables.toList(), options)
    }
  }

  override suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean {
    return doExclusively {
      doNavigate(navigatables, options)
    }
  }

  private suspend fun doNavigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean {
    val requests = navigatables.mapWithProgress {
      readAction {
        it.navigationRequest()
      }
    }.filterNotNull()
    return withHistoryIfNeeded(options) {
      navigate(project = project, requests = requests, options = options)
    }
  }

  override suspend fun navigate(request: NavigationRequest, options: NavigationOptions): Boolean {
    return navigate(listOf(request), options)
  }

  override suspend fun navigate(requests: Collection<NavigationRequest>, options: NavigationOptions): Boolean {
    return doExclusively {
      withHistoryIfNeeded(options) {
        navigate(project = project, requests = requests, options = options)
      }
    }
  }

  /**
   * Re-entering the permit would cancel the very navigation the caller is running inside and reset the options.
   */
  private suspend inline fun doExclusively(crossinline action: suspend () -> Boolean): Boolean {
    if (isInNavigation.get()) {
      LOG.error("Navigation is already running: use `NavigationRequest` instead of starting a navigation from `navigate()`")
      return false
    }
    return taskCoordinator.runWithTracking {
      semaphore.withPermit {
        withContext(isInNavigation.asContextElement(true)) {
          action()
        }
      }
    }
  }

  private suspend inline fun <T> withHistoryIfNeeded(options: NavigationOptions, crossinline action: suspend () -> T): T {
    options as NavigationOptions.Impl
    if (!options.recordAsBackHistory) {
      return action()
    }
    return performNavigationHistoryAware(project) { action() }
  }
}

private val LOG: Logger = Logger.getInstance("#com.intellij.platform.ide.navigation.impl")

private suspend fun navigate(project: Project, requests: Collection<NavigationRequest>, options: NavigationOptions): Boolean {
  options as NavigationOptions.Impl
  return if (options.requestedEditor != RequestedEditor.None) {
    doNavigate(project = project, requests = requests, options = options)
  } // navigating by itself cannot be told which editor to use, so the ambient one is ignored
  else withContext(editorSuppressionCoroutineContext()) {
    doNavigate(project = project, requests = requests, options = options)
  }
}

/**
 * Navigates to all sources from [requests], or navigates to first non-source request.
 */
private suspend fun doNavigate(project: Project, requests: Collection<NavigationRequest>, options: NavigationOptions): Boolean {
  val maxSourceRequests = if (requests.size == 1) Int.MAX_VALUE else Registry.intValue("ide.source.file.navigation.limit", 100)
  var nonSourceRequest: Pair<NavigationRequest, NavigationOptions.Impl>? = null

  options as NavigationOptions.Impl
  var navigatedSourcesCounter = 0
  for (requestFromNavigatable in requests) {
    if (maxSourceRequests in 1..navigatedSourcesCounter) {
      break
    }
    val requestOptions = if (navigatedSourcesCounter == 0 || !options.openInRightSplit) {
      options
    }
    else {
      options.openInRightSplit(false) as NavigationOptions.Impl
    }
    if (tryNavigateToSource(project = project, request = requestFromNavigatable, options = requestOptions)) {
      navigatedSourcesCounter++
    }
    else if (nonSourceRequest == null) {
      nonSourceRequest = requestFromNavigatable to requestOptions
    }
  }

  if (navigatedSourcesCounter > 0) {
    return true
  }
  if (nonSourceRequest == null || options.sourceNavigationOnly) {
    if (nonSourceRequest != null && LOG.isDebugEnabled) {
      LOG.debug("Skipping non-source request because of sourceNavigationOnly: ${nonSourceRequest.first}")
    }
    return false
  }

  navigateNonSource(project = project, request = nonSourceRequest.first, options = nonSourceRequest.second)
  return true
}

private suspend fun tryNavigateToSource(
  project: Project,
  request: NavigationRequest,
  options: NavigationOptions.Impl,
): Boolean {
  when (request) {
    is SourceNavigationRequest -> {
      val caretShift = caretShift(project = project, request = request, placement = options.caretPlacement)
      val knownType = request.file.knownFileType()
      withContext(Dispatchers.EDT) {
        navigateToSourceImpl(
          request = request,
          options = options,
          project = project,
          offset = request.targetOffset(caretShift),
          knownType = knownType,
        )
      }
      return true
    }
    is DirectoryNavigationRequest -> {
      return false
    }
    is RawNavigationRequest -> {
      if (request.canNavigateToSource) {
        val caretTarget = rawCaretTarget(project = project, request = request, placement = options.caretPlacement)
        project.serviceAsync<IdeNavigationServiceExecutor>().navigate(request = request, requestFocus = options.requestFocus)
        caretTarget?.let { adjustCaret(project = project, target = it) }
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
        PsiNavigationSupport.getInstance().navigateToDirectory(request.directory, options.requestFocus)
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

/**
 * Computes the distance from [SourceNavigationRequest.offsetMarker] to the offset [placement] asks for.
 *
 * A shift rather than a ready offset is computed here so that the target offset itself may be resolved from the marker
 * on the EDT: the document may change before the navigation gets there, and the marker follows such changes.
 *
 * @return `0` when the caret goes right to the target offset
 */
private suspend fun caretShift(project: Project, request: SourceNavigationRequest, placement: CaretPlacement): Int {
  if (placement == CaretPlacement.TARGET_OFFSET) {
    return 0
  }
  val offset = request.offsetMarker?.takeIf { it.isValid }?.startOffset ?: return 0
  val shift = readAction {
    val psiFile = request.file.findPsiFile(project) ?: return@readAction null
    psiFile.findLeafEndAtOffset(offset = offset)?.minus(offset)
  }
  return shift ?: 0
}

/**
 * @return the offset in the coordinates of [SourceNavigationRequest.file], or `-1` when the request carries no offset
 */
@RequiresEdt
private fun SourceNavigationRequest.targetOffset(caretShift: Int): Int {
  val marker = offsetMarker?.takeIf { it.isValid } ?: return -1
  return (marker.startOffset + caretShift).coerceAtMost(marker.document.textLength)
}

/**
 * A [RawNavigationRequest] runs the navigation code of the navigatable itself, so the platform is not told where the navigation lands
 * and [placement] may only be applied once that navigation is over.
 *
 * Only a navigatable which does tell its target upfront is supported, see [targetDescriptor]. For any other navigatable
 * the [placement] is ignored, because there is nothing to tell a successful navigation from one which did nothing.
 *
 * @return the caret target to apply after the navigation, or `null` when the [placement] cannot be honored
 */
private suspend fun rawCaretTarget(project: Project, request: RawNavigationRequest, placement: CaretPlacement): RawCaretTarget? {
  if (placement == CaretPlacement.TARGET_OFFSET) {
    return null
  }
  return readAction {
    val descriptor = request.navigatable.targetDescriptor() ?: return@readAction null
    val targetOffset = descriptor.offset.takeIf { it >= 0 } ?: return@readAction null
    val psiFile = descriptor.file.findPsiFile(project) ?: return@readAction null
    val caretOffset = psiFile.findLeafEndAtOffset(targetOffset) ?: return@readAction null
    RawCaretTarget(file = descriptor.file, targetOffset = targetOffset, caretOffset = caretOffset)
  }
}

/**
 * @return the descriptor this navigatable navigates to, or `null` when it does not tell its target
 */
private fun Navigatable.targetDescriptor(): OpenFileDescriptor? = when (this) {
  is OpenFileDescriptor -> this
  // a PSI element navigates to the descriptor built for it, see com.intellij.psi.impl.source.tree.CompositePsiElement.navigate
  is PsiElement -> PsiNavigationSupport.getInstance().getDescriptor(this) as? OpenFileDescriptor
  else -> null
}

private class RawCaretTarget(val file: VirtualFile, val targetOffset: Int, val caretOffset: Int)

/**
 * Moves the caret to [RawCaretTarget.caretOffset], but only while the selected editor is the one showing the navigation target:
 * the navigation may have done nothing at all, may have landed in another editor, or may have scheduled its own asynchronous work
 * and returned before reaching the target.
 */
private suspend fun adjustCaret(project: Project, target: RawCaretTarget) {
  val fileEditorManager = project.serviceAsync<FileEditorManager>()
  val fileDocumentManager = serviceAsync<FileDocumentManager>()
  withContext(Dispatchers.EDT) {
    val editor = fileEditorManager.selectedTextEditor ?: return@withContext
    if (fileDocumentManager.getFile(editor.document) == target.file && editor.caretModel.offset == target.targetOffset) {
      editor.caretModel.moveToOffset(target.caretOffset)
    }
  }
}

/**
 * @return the end offset of the token starting at [offset], or `null` when [offset] is not a token start
 */
private fun PsiFile.findLeafEndAtOffset(offset: Int): Int? {
  val leaf = findElementAt(offset) ?: return null
  return leaf.textRange.endOffset.takeIf { leaf.textRange.startOffset == offset }
}

/**
 * Resolves the type of the file without the EDT: an unknown type is resolved by the file content, which means reading the file,
 * and reading it on the EDT freezes the IDE, see IJPL-249536.
 *
 * @return the type of the file, or `null` for a directory and for a file whose type stays unknown: asking the user to associate
 * a type with it is only possible on the EDT
 */
private suspend fun VirtualFile.knownFileType(): FileType? {
  if (isDirectory) {
    return null
  }
  return readAction { fileType.takeIf { it != UnknownFileType.INSTANCE } }
}

private suspend fun navigateToSourceImpl(
  options: NavigationOptions.Impl,
  request: SourceNavigationRequest,
  project: Project,
  offset: Int,
  knownType: FileType?,
) {
  val file = request.file
  // the type resolved in the background is reused; the remaining case is a type unknown to the IDE, which the user is asked about
  val type = knownType ?: if (file.isDirectory) null else FileTypeManager.getInstance().getKnownFileTypeOrAssociate(file, project)
  if (type != null && file.isValid) {
    if (type is INativeFileType) {
      if (type.openFileInAssociatedApplication(project, file)) {
        return
      }
    }
    else {
      val requestedEditor = (options.requestedEditor as? RequestedEditor.Specific)?.editor
      if (requestedEditor != null) {
        val descriptor = OpenFileDescriptor(project, request.file, offset)
        val fileNavigator = serviceAsync<FileNavigator>()
        if (fileNavigator is FileNavigatorImpl &&
            fileNavigator.navigateInRequestedEditorAsync(descriptor, requestedEditor, options.requestFocus)) {
          return
        }
      }

      if (openFile(request = request, project = project, options = options, offset = offset)) {
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
  offset: Int,
): Boolean {
  var hostOffset = offset
  val originalFile = request.file
  var file = originalFile

  val fileEditorManager = project.serviceAsync<FileEditorManager>() as FileEditorManagerEx
  if (originalFile is VirtualFileWindow) {
    readAction {
      if (hostOffset != -1) {
        // injectedToHost does not preserve the "no offset" marker, it maps a negative offset into the first host range
        hostOffset = originalFile.documentWindow.injectedToHost(hostOffset)
      }
      file = originalFile.delegate
    }
  }

  val composite = fileEditorManager.openFile(
    file = file,
    options = FileEditorOpenOptions(
      reuseOpen = true,
      requestFocus = options.requestFocus,
      openMode = if (options.openInRightSplit) FileEditorManagerImpl.OpenMode.RIGHT_SPLIT else FileEditorManagerImpl.OpenMode.DEFAULT,
      forceFocus = options.forceFocus,
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


  if (hostOffset == -1) {
    return true
  }

  val descriptor = OpenFileDescriptor(project, file, hostOffset)
  val fileNavigator = serviceAsync<FileNavigator>()
  suspend fun tryNavigate(fileEditors: Sequence<FileEditor>): Boolean {
    for (editor in fileEditors) {
      // try to navigate opened editor
      if (editor is NavigatableFileEditor) {
        val navigated = withContext(Dispatchers.EDT) {
          //todo: try read action only
          writeIntentReadAction {
            // The current implementation of navigation depends on the type of the editor.
            // If the editor is a subtype of TextEditorImpl, it falls into the condition editor.canNavigateTo.
            // If the editor is a subtype of EditorWrapper, the second branch is tested. This scenario is possible
            // during running some tests but could potentially happen in production code as well.
            // More details: IJPL-184882 openFile doesn’t perform navigation for a SourceNavigationRequest
            when {
              editor.canNavigateTo(descriptor) -> {
                navigateAndSelectEditor(editor, descriptor, composite as? EditorComposite)
              }
              fileNavigator.canNavigate(descriptor) -> {
                // NAVIGATE_IN_EDITOR of the current data context is unrelated to this navigation
                fileNavigator.navigate(descriptor, options.requestFocus, requestedEditor = null)
                true
              }
              else -> false
            }
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
