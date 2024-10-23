// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.diagnostic.PluginException
import com.intellij.lang.FileASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.text.BlockSupport
import com.intellij.util.SmartList
import com.intellij.util.concurrency.createBoundedTaskExecutor
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.lang.ref.Reference
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.function.BooleanSupplier

private val LOG = logger<DocumentCommitThread>()

@ApiStatus.Internal
class DocumentCommitThread internal constructor(coroutineScope: CoroutineScope) : DocumentCommitProcessor {
  private val executor = createBoundedTaskExecutor("Document Commit Pool", coroutineScope)

  @Volatile
  private var isDisposed = false

  companion object {
    @JvmStatic
    fun getInstance(): DocumentCommitThread = service<DocumentCommitProcessor>() as DocumentCommitThread
  }

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      isDisposed = true
    }
  }

  override fun commitAsynchronously(
    project: Project,
    documentManager: PsiDocumentManagerBase,
    document: Document,
    reason: @NonNls Any,
    modality: ModalityState,
    cachedViewProvider: FileViewProvider,
  ) {
    assert(!isDisposed) { "already disposed" }
    if (!project.isInitialized()) {
      return
    }

    require(documentManager.myProject === project) { "Wrong project: $project; expected: ${documentManager.myProject}" }

    assert(cachedViewProvider.isEventSystemEnabled()) {
      "Asynchronous commit is only supported for physical PSI, " +
      "document=$document, cachedViewProvider=$cachedViewProvider (${cachedViewProvider.javaClass})"
    }
    TransactionGuard.getInstance().assertWriteSafeContext(modality)

    val task = CommitTask(
      project = project,
      document = document,
      reason = reason,
      myCreationModality = modality,
      myLastCommittedText = documentManager.getLastCommittedText(document),
      cachedViewProvider = cachedViewProvider,
    )
    ReadAction
      .nonBlocking(Callable { commitUnderProgress(task = task, synchronously = false, documentManager = documentManager) })
      .expireWhen(
        BooleanSupplier { project.isDisposed() || isDisposed || !documentManager.isInUncommittedSet(document) || !task.isStillValid }
      )
      .coalesceBy(task)
      .finishOnUiThread(modality) { it.run() }
      .submit(executor)
  }

  override fun commitSynchronously(document: Document, project: Project, psiFile: PsiFile) {
    assert(!isDisposed)

    require(!(!project.isInitialized() && !project.isDefault)) {
      "Must not call sync commit with unopened project: " + project + "; Disposed: " + project.isDisposed() + "; Open: " + project.isOpen()
    }

    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    val task = CommitTask(project, document, "Sync commit", ModalityState.defaultModalityState(),
                          documentManager.getLastCommittedText(document), psiFile.getViewProvider())

    commitUnderProgress(task, true, documentManager).run()
  }

  // returns finish commit Runnable (to be invoked later in EDT) or null on failure
  private fun commitUnderProgress(task: CommitTask, synchronously: Boolean, documentManager: PsiDocumentManagerBase): Runnable {
    val document = task.document
    val project = task.project
    val finishProcessors = SmartList<BooleanRunnable>()
    val reparseInjectedProcessors = SmartList<BooleanRunnable>()

    val viewProvider = documentManager.getCachedViewProvider(document)
    if (viewProvider == null) {
      finishProcessors.add(handleCommitWithoutPsi(task, documentManager))
    }
    else {
      // While we were messing around transferring things to background thread, the ViewProvider can become obsolete
      // when, e.g., a virtual file was renamed.
      // Store new provider to retain it from GC
      task.cachedViewProvider = viewProvider

      for (file in viewProvider.getAllFiles()) {
        val oldFileNode = file.getNode()
        if (oldFileNode == null) {
          throw AssertionError("No node for " + file.javaClass + " in " + file.getViewProvider().javaClass +
                               " of size " + StringUtil.formatFileSize(document.textLength.toLong()) +
                               " (is too large = " + SingleRootFileViewProvider
                                 .isTooLargeForIntelligence(viewProvider.getVirtualFile(), document.textLength.toLong()) + ")")
        }
        val changedPsiRange = ChangedPsiRangeUtil.getChangedPsiRange(
          file,
          document,
          task.myLastCommittedText,
          document.getImmutableCharSequence(),
        )
        if (changedPsiRange != null) {
          val finishProcessor = doCommit(
            task = task,
            file = file,
            oldFileNode = oldFileNode,
            changedPsiRange = changedPsiRange,
            outReparseInjectedProcessors = reparseInjectedProcessors,
            documentManager = documentManager,
          )
          finishProcessors.add(finishProcessor)
        }
      }
    }

    return Runnable {
      if (project.isDisposed()) {
        return@Runnable
      }
      val success = documentManager.finishCommit(document, finishProcessors, reparseInjectedProcessors, synchronously, task.reason)
      if (synchronously) {
        assert(success)
      }
      if (synchronously || success) {
        assert(!documentManager.isInUncommittedSet(document))
      }
      if (!success && viewProvider != null && viewProvider.isEventSystemEnabled()) {
        // add a document back to the queue
        commitAsynchronously(
          project = project,
          documentManager = documentManager,
          document = document,
          reason = "Re-added back",
          modality = task.myCreationModality,
          cachedViewProvider = viewProvider,
        )
      }
    }
  }

  override fun toString(): String = "Document commit thread; application: ${ApplicationManager.getApplication()}; isDisposed: $isDisposed"

  // NB: failures applying EDT tasks are not handled - i.e., failed documents are added back to the queue and the method returns
  @TestOnly
  fun waitForAllCommits(timeout: Long, timeUnit: TimeUnit) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      while (!executor.isEmpty()) {
        executor.waitAllTasksExecuted(timeout, timeUnit)
      }
      return
    }
    assert(!ApplicationManager.getApplication().isWriteAccessAllowed())

    EDT.dispatchAllInvocationEvents()
    while (!executor.isEmpty()) {
      executor.waitAllTasksExecuted(timeout, timeUnit)
      EDT.dispatchAllInvocationEvents()
    }
  }
}

private class CommitTask(
  @JvmField val project: Project,
  @JvmField val document: Document,
  @JvmField val reason: @NonNls Any,
  @JvmField val myCreationModality: ModalityState,
  @JvmField val myLastCommittedText: CharSequence,
  // to retain viewProvider to avoid surprising getCachedProvider() == null half-way through commit
  @JvmField @field:Volatile var cachedViewProvider: FileViewProvider,
) {
  // store initial document modification sequence here to check if it changed later before commit in EDT
  private val modificationSequence = (document as DocumentEx).modificationSequence

  override fun toString(): @NonNls String {
    val reasonInfo = " task reason: " + StringUtil.first(reason.toString(), 180, true) +
                     (if (isStillValid) "" else "; changed: old seq=$modificationSequence, new seq=${(document as DocumentEx).modificationSequence}")
    val contextInfo = " modality: $myCreationModality"
    return System.identityHashCode(this).toString() + "; " + contextInfo + reasonInfo
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is CommitTask) return false

    return document == o.document && project == o.project
  }

  override fun hashCode(): Int = 31 * document.hashCode() + project.hashCode()

  val isStillValid: Boolean
    get() = (document as DocumentEx).modificationSequence == modificationSequence
}

private fun handleCommitWithoutPsi(
  task: CommitTask,
  documentManager: PsiDocumentManagerBase,
): BooleanRunnable {
  return BooleanRunnable {
    if (task.isStillValid && documentManager.getCachedViewProvider(task.document) == null) {
      documentManager.handleCommitWithoutPsi(task.document)
      true
    }
    else {
      false
    }
  }
}

// returns runnable to execute under the write action in AWT to finish the commit
private fun doCommit(
  task: CommitTask,
  file: PsiFile,
  oldFileNode: FileASTNode,
  changedPsiRange: ProperTextRange,
  outReparseInjectedProcessors: MutableList<BooleanRunnable>,
  documentManager: PsiDocumentManagerBase,
): BooleanRunnable {
  val document = task.document
  val newDocumentText = document.getImmutableCharSequence()

  val data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY)
  if (data != null) {
    document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null)
    file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data)
  }

  val diffLog: DiffLog
  val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
  try {
    val result = BlockSupportImpl.reparse(file, oldFileNode, changedPsiRange, newDocumentText, indicator, task.myLastCommittedText)
    diffLog = result.log

    val injectedRunnables = documentManager.reparseChangedInjectedFragments(
      document,
      file,
      changedPsiRange,
      indicator,
      result.oldRoot,
      result.newRoot,
    )
    outReparseInjectedProcessors.addAll(injectedRunnables)
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error(e)
    return BooleanRunnable {
      documentManager.forceReload(file.getViewProvider().getVirtualFile(), file.getViewProvider())
      true
    }
  }

  return BooleanRunnable {
    val viewProvider = file.getViewProvider()
    if (!task.isStillValid || documentManager.getCachedViewProvider(document) !== viewProvider) {
      // optimistic locking failed
      return@BooleanRunnable false
    }

    if (!ApplicationManager.getApplication().isWriteAccessAllowed() && documentManager.isEventSystemEnabled(document)) {
      val vFile = viewProvider.getVirtualFile()
      LOG.error("Write action expected" +
                "; document=" + document +
                "; file=" + file + " of " + file.javaClass +
                "; file.valid=" + file.isValid() +
                "; file.eventSystemEnabled=" + viewProvider.isEventSystemEnabled() +
                "; viewProvider=" + viewProvider + " of " + viewProvider.javaClass +
                "; language=" + file.getLanguage() +
                "; vFile=" + vFile + " of " + vFile.javaClass +
                "; free-threaded=" + AbstractFileViewProvider.isFreeThreaded(viewProvider))
    }

    diffLog.doActualPsiChange(file)

    assertAfterCommit(document, file, oldFileNode)
    // just to make an impression the field is used
    Reference.reachabilityFence(task.cachedViewProvider)
    true
  }
}

private fun assertAfterCommit(document: Document, file: PsiFile, oldFileNode: FileASTNode) {
  if (oldFileNode.getTextLength() == document.textLength) {
    return
  }

  val documentText = document.text
  val fileText = file.getText()
  val sameText = fileText == documentText
  val errorMessage = "commitDocument() left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                     "; node.length=" + oldFileNode.getTextLength() +
                     "; doc.text" + (if (sameText) "==" else "!=") + "file.text" +
                     "; file name:" + file.getName() +
                     "; type:" + file.getFileType() +
                     "; lang:" + file.getLanguage()
  PluginException.logPluginError(LOG, errorMessage, null, file.getLanguage().javaClass)

  file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, true)
  try {
    val blockSupport = BlockSupport.getInstance(file.getProject())
    val diffLog = blockSupport.reparseRange(
      file,
      file.getNode(),
      TextRange(0, documentText.length),
      documentText,
      StandardProgressIndicatorBase(),
      oldFileNode.getText(),
    )
    diffLog.doActualPsiChange(file)

    if (oldFileNode.getTextLength() != document.textLength) {
      PluginException.logPluginError(LOG, "PSI is broken beyond repair in: $file", null, file.getLanguage().javaClass)
    }
  }
  finally {
    file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null)
  }
}
