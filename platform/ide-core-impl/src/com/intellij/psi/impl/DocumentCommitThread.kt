// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.diagnostic.PluginException
import com.intellij.lang.FileASTNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.text.BlockSupport
import com.intellij.util.SmartList
import com.intellij.util.concurrency.BoundedTaskExecutor
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<DocumentCommitThread>()

@ApiStatus.Internal
class DocumentCommitThread : DocumentCommitProcessor, Disposable {
  @Volatile
  private var isDisposed = false
  private val myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Document Commit Pool")

  private val commitInProgressCounter = AtomicInteger()

  // it does not make sense to commit several documents in parallel, as they end in write action, so they'll invalidate each other
  private val commitDispatcher = Dispatchers.Default.limitedParallelism(1, "Document commit dispatcher")

  companion object {
    @JvmStatic
    fun getInstance(): DocumentCommitThread = service<DocumentCommitProcessor>() as DocumentCommitThread

    private fun canUseCoroutinesForDocumentCommit(): Boolean {
      return useBackgroundWriteAction && Registry.`is`("document.async.commit.with.coroutines")
    }
  }

  override fun dispose() {
    (myExecutor as BoundedTaskExecutor).clearAndCancelAll()
    isDisposed = true
  }

  override fun commitAsynchronously(
    project: Project,
    documentManager: PsiDocumentManagerBase,
    document: Document,
    reason: Any,
    modality: ModalityState,
  ) {
    assert(!isDisposed) { "already disposed" }
    if (!project.isInitialized()) {
      return
    }

    require(documentManager.myProject === project) { "Wrong project: $project; expected: ${documentManager.myProject}" }

    TransactionGuard.getInstance().assertWriteSafeContext(modality)

    val task = CommitTask(project, document, reason, modality)

    if (canUseCoroutinesForDocumentCommit()) {
      commitDocumentWithCoroutines(document, task, documentManager)
    }
    else {
      ReadAction
        .nonBlocking(Callable { commitUnderProgress(task, synchronously = false, documentManager) })
        .expireWhen { isExpired(task, documentManager) }
        .coalesceBy(task)
        .finishOnUiThread(modality) { it() }
        .submit(myExecutor)
    }
  }

  /**
   * Document commit is a per-project activity, i.e., there can be two simultaneous commits for the same document but different projects
   * So we run all commits on service's scope. In addition, we can now cancel all commits relevant to a particular project
   * By using a project-bound storage for commit coroutines we also avoid project leaks.
   */
  @Service(Service.Level.PROJECT)
  class PerProjectDocumentCommitRegistry(val scope: CoroutineScope) {
    val publishedDocumentCommitRequests: ConcurrentMap<Document, Job> = CollectionFactory.createConcurrentWeakMap()
  }

  private fun commitDocumentWithCoroutines(document: Document, task: CommitTask, documentManager: PsiDocumentManagerBase) {
    val service = task.myProject.service<PerProjectDocumentCommitRegistry>()
    val job = service.scope.launch(commitDispatcher, start = CoroutineStart.LAZY) {
      commitInProgressCounter.incrementAndGet()
      try {
        // one needs to treat modalities carefully with background write action
        // to be safer, we perform commit in background write action only if this is a non-modal commit
        if (task.myCreationModality == ModalityState.nonModal()) {
          readAndBackgroundWriteActionUndispatched {
            doCommitInReadAndWriteScope(task, documentManager)
          }
        }
        else {
          withContext(task.myCreationModality.asContextElement()) {
            readAndEdtWriteActionUndispatched {
              doCommitInReadAndWriteScope(task, documentManager)
            }
          }
        }
      }
      finally {
        commitInProgressCounter.decrementAndGet()
      }
    }
    job.invokeOnCompletion {
      // since this attempt to commit is over, we unblock the possibility to submit more requests to commit here
      task.myDocumentRef.get()?.let { service.publishedDocumentCommitRequests.remove(it, job) }
    }
    val existingJob = service.publishedDocumentCommitRequests.put(document, job)
    // following the logic of NBRA, we retain only the latest request to commit
    existingJob?.cancel()
    job.start()
  }

  private fun ReadAndWriteScope.doCommitInReadAndWriteScope(task: CommitTask, documentManager: PsiDocumentManagerBase): ReadResult<Unit> {
    if (isExpired(task, documentManager)) {
      return value(Unit)
    }
    val writeThreadCallback = commitUnderProgress(task, synchronously = false, documentManager)
    if (isExpired(task, documentManager)) {
      return value(Unit)
    }
    return writeAction {
      writeThreadCallback()
    }
  }

  private fun isExpired(task: CommitTask, docManager: PsiDocumentManagerBase): Boolean {
    return isDisposed || task.isExpired(docManager)
  }

  override fun commitSynchronously(document: Document, project: Project, psiFile: PsiFile) {
    assert(!isDisposed)

    require(!(!project.isInitialized() && !project.isDefault)) {
      "Must not call sync commit with unopened project: $project; Disposed: ${project.isDisposed()}; Open: ${project.isOpen()}"
    }

    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    val task = CommitTask(project, document, "Sync commit", ModalityState.defaultModalityState())

    commitUnderProgress(task, synchronously = true, documentManager)()
  }

  @RequiresReadLock
  // returns finish commit Runnable (to be invoked later in EDT) or null on failure
  private fun commitUnderProgress(task: CommitTask, synchronously: Boolean, documentManager: PsiDocumentManagerBase): () -> Unit {
    if (!synchronously) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val document = task.myDocumentRef.get()?: return {}
    val project = task.myProject
    val finishProcessors = SmartList<BooleanRunnable>()
    val reparseInjectedProcessors = SmartList<BooleanRunnable>()


    val psiManager = PsiManagerEx.getInstanceEx(project)
    val virtualFile = FileDocumentManager.getInstance().getFile(document)
    val viewProvider = if (virtualFile == null) null else psiManager.findViewProvider(virtualFile)
    if (viewProvider == null) {
      finishProcessors.add(handleCommitWithoutPsi(task, documentManager))
    }
    else {
      // While we were messing around transferring things to background thread, the ViewProvider can become obsolete
      // when, e.g., a virtual file was renamed.
      // Store new provider to retain it from GC
      task.cachedViewProvider = viewProvider

      // todo IJPL-339 check if this is correct
      for (psiFile in viewProvider.getAllFiles()) {
        val oldFileNode = psiFile.getNode()
            ?: throw AssertionError("No node for " + psiFile.javaClass + " in " + psiFile.getViewProvider().javaClass +
                                    " of size " + StringUtil.formatFileSize(document.textLength.toLong()) +
                                    " (is too large = " + SingleRootFileViewProvider
                                      .isTooLargeForIntelligence(viewProvider.getVirtualFile(), document.textLength.toLong()) + ")")
        val changedPsiRange = ChangedPsiRangeUtil.getChangedPsiRange(
          psiFile,
          document,
          task.myLastCommittedText,
          document.getImmutableCharSequence(),
        )
        if (changedPsiRange != null) {
          val finishProcessor = doCommit(task, synchronously, document, psiFile, oldFileNode, changedPsiRange, reparseInjectedProcessors, documentManager)
          finishProcessors.add(finishProcessor)
        }
      }
    }

    return task@ {
      if (project.isDisposed()) {
        return@task
      }

      // this document was not referenced by anyone, hence we don't need to perform a write action
      val document = task.myDocumentRef.get() ?: return@task

      val success = documentManager.finishCommit(document, finishProcessors, reparseInjectedProcessors, synchronously, task.myReason)
      if (synchronously) {
        assert(success)
      }
      if (synchronously || success) {
        assert(!documentManager.isInUncommittedSet(document))
      }
      if (!success && viewProvider?.isEventSystemEnabled() == true) {
        // add a document back to the queue
        commitAsynchronously(project, documentManager, document, "Re-added back", task.myCreationModality)
      }
    }
  }

  override fun toString(): String = "Document commit thread; application: ${ApplicationManager.getApplication()}; isDisposed: $isDisposed"


  fun Pair<Job, AtomicBoolean>.isReadPartInProgress(): Boolean {
    // the commit is in the read phase if its job is not canceled (by a similar task) or when it did not manage to set the boolean flag of read
    return !first.isCancelled && second.get()
  }

  // NB: failures applying EDT tasks are not handled - i.e., failed documents are added back to the queue and the method returns
  @TestOnly
  fun waitForAllCommits(timeout: Long, timeUnit: TimeUnit) {
    val boundedTaskExecutor = myExecutor as BoundedTaskExecutor
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      boundedTaskExecutor.waitAllTasksExecuted(timeout, timeUnit)
      while (commitInProgressCounter.get() > 0) {
        Thread.sleep(10)
      }
      return
    }

    assert(!ApplicationManager.getApplication().isWriteAccessAllowed())

    EDT.dispatchAllInvocationEvents()
    val deadLine = System.nanoTime() + timeUnit.toNanos(timeout)
    while (!boundedTaskExecutor.isEmpty || commitInProgressCounter.get() > 0) {
      try {
        boundedTaskExecutor.waitAllTasksExecuted(10, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
      }
      catch (e: TimeoutException) {
        if (System.nanoTime() > deadLine) {
          throw e
        }
      }
      EDT.dispatchAllInvocationEvents()
    }
  }

  private class CommitTask {
    val myProject: Project
    val myReason: @NonNls Any
    val myCreationModality: ModalityState
    val myDocumentRef: Reference<Document>
    val myLastCommittedText: CharSequence
    // store initial document modification sequence here to check if it changed later before commit in EDT
    private val myModificationSequence: Int
    @Volatile var cachedViewProvider: FileViewProvider? = null

    constructor(
      project: Project,
      document: Document,
      reason: @NonNls Any,
      creationModality: ModalityState,
    ) {
      myProject = project
      myReason = reason.toString() // convert to string to avoid leaking document in case somebody passed a Document or DocumentEvent here
      myCreationModality = creationModality
      myDocumentRef = WeakReference(document)
      myLastCommittedText = PsiDocumentManager.getInstance(project).getLastCommittedText(document)
      myModificationSequence = (document as DocumentEx).modificationSequence
    }
    override fun toString(): @NonNls String {
      val document = stillValidDocument()
      val reasonInfo = " task reason: " + StringUtil.first(myReason.toString(), 180, true) +
                       document + "; changed: old seq=$myModificationSequence, new seq=${(document as? DocumentEx)?.modificationSequence}"
      val contextInfo = " modality: $myCreationModality"
      return System.identityHashCode(this).toString() + "; " + contextInfo + reasonInfo
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is CommitTask) return false

      return myDocumentRef.get() == other.myDocumentRef.get() && myProject == other.myProject
    }

    override fun hashCode(): Int {
      return 31 * Objects.hashCode(myDocumentRef.get()) + myProject.hashCode()
    }

    // return null if the document is changed or gced
    fun stillValidDocument(): Document? {
      val document = myDocumentRef.get()
      return if (document is DocumentEx && document.modificationSequence == myModificationSequence) {
        document
      } else {
        null
      }
    }
    fun isExpired(documentManager: PsiDocumentManagerBase): Boolean {
      val document = stillValidDocument()
      val expired = myProject.isDisposed() ||
                    document == null ||
                    !documentManager.isInUncommittedSet(document) ||
                    FileDocumentManager.getInstance().getFile(document)?.isValid != true
      return expired
    }
  }

  // returns runnable to execute under the write action in AWT to finish the commit
  @RequiresReadLock
  private fun doCommit(
    task: CommitTask,
    synchronously: Boolean,
    document: Document,
    psiFile: PsiFile,
    oldFileNode: FileASTNode,
    changedPsiRange: ProperTextRange,
    outReparseInjectedProcessors: MutableList<BooleanRunnable>,
    documentManager: PsiDocumentManagerBase,
  ): BooleanRunnable {
    if (!synchronously) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val newDocumentText = document.getImmutableCharSequence()

    val data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY)
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null)
      psiFile.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data)
    }

    val diffLog: DiffLog
    val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
    try {
      val result = BlockSupportImpl.reparse(psiFile, oldFileNode, changedPsiRange, newDocumentText, indicator, task.myLastCommittedText)
      diffLog = result.log

      val injectedRunnables = documentManager.reparseChangedInjectedFragments(
        document,
        psiFile,
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
        documentManager.forceReload(psiFile.getViewProvider().getVirtualFile(), listOf(psiFile.getViewProvider()))
        true
      }
    }

    return BooleanRunnable {
      val document = task.myDocumentRef.get() ?: return@BooleanRunnable false
      val viewProvider = psiFile.getViewProvider() //todo IJPL-339 figure out correct check here
      if (task.stillValidDocument() == null || viewProvider !in documentManager.getCachedViewProviders(document)) { // optimistic locking failed
        return@BooleanRunnable false
      }

      if (!ApplicationManager.getApplication().isWriteAccessAllowed() && documentManager.isEventSystemEnabled(document)) {
        val vFile = viewProvider.getVirtualFile()
        LOG.error("Write action expected" + "; document=" + document + "; file=" + psiFile + " of " + psiFile.javaClass + "; file.valid=" + psiFile.isValid() + "; file.eventSystemEnabled=" + viewProvider.isEventSystemEnabled() + "; viewProvider=" + viewProvider + " of " + viewProvider.javaClass + "; language=" + psiFile.getLanguage() + "; vFile=" + vFile + " of " + vFile.javaClass + "; free-threaded=" + AbstractFileViewProvider.isFreeThreaded(viewProvider))
      }

      diffLog.doActualPsiChange(psiFile)

      assertAfterCommit(document, psiFile, oldFileNode) // just to make an impression the field is used
      Reference.reachabilityFence(task.cachedViewProvider)
      true
    }
  }

  private fun handleCommitWithoutPsi(
    task: CommitTask,
    documentManager: PsiDocumentManagerBase,
  ): BooleanRunnable {
    return BooleanRunnable {
      val document = task.myDocumentRef.get() ?: return@BooleanRunnable false
      if (task.stillValidDocument() != null && documentManager.getCachedViewProviders(document).isEmpty()) {
        documentManager.handleCommitWithoutPsi(document)
        true
      }
      else {
        false
      }
    }
  }

  private fun assertAfterCommit(document: Document, psiFile: PsiFile, oldFileNode: FileASTNode) {
    if (oldFileNode.getTextLength() == document.textLength) {
      return
    }

    val documentText = document.text
    val fileText = psiFile.getText()
    val sameText = fileText == documentText
    val errorMessage = "commitDocument() left PSI inconsistent: " + DebugUtil.diagnosePsiDocumentInconsistency(psiFile, document) +
                       "; node.length=" + oldFileNode.getTextLength() +
                       "; doc.text" + (if (sameText) "==" else "!=") + "file.text" +
                       "; file name:" + psiFile.getName() +
                       "; type:" + psiFile.getFileType() +
                       "; lang:" + psiFile.getLanguage()
    PluginException.logPluginError(LOG, errorMessage, null, psiFile.getLanguage().javaClass)

    psiFile.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, true)
    try {
      val blockSupport = BlockSupport.getInstance(psiFile.getProject())
      val diffLog = blockSupport.reparseRange(
        psiFile,
        psiFile.getNode(),
        TextRange(0, documentText.length),
        documentText,
        StandardProgressIndicatorBase(),
        oldFileNode.getText(),
      )
      diffLog.doActualPsiChange(psiFile)

      if (oldFileNode.getTextLength() != document.textLength) {
        PluginException.logPluginError(LOG, "PSI is broken beyond repair in: $psiFile", null, psiFile.getLanguage().javaClass)
      }
    }
    finally {
      psiFile.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null)
    }
  }
}