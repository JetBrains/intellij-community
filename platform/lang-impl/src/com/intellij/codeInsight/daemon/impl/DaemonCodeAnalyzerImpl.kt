// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.*
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.*
import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent
import com.intellij.codeInsight.intention.impl.IntentionHintComponent
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.JobLauncher
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.notebook.editor.BackedVirtualFileProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ClientFileEditorManager.Companion.getClientId
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.impl.FileManagerImpl
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiUtilBase
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.*
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.toArray
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.ui.EDT
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

private const val ANY_GROUP = -409423948

private val LOG = Logger.getInstance(DaemonCodeAnalyzerImpl::class.java)
private val FILE_LEVEL_HIGHLIGHTS = Key.create<MutableList<HighlightInfo>>("FILE_LEVEL_HIGHLIGHTS")
private val COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY = Key.create<Boolean>("COMPLETE_ESSENTIAL_HIGHLIGHTING")
private const val DISABLE_HINTS_TAG: @NonNls String = "disable_hints"
private const val FILE_TAG: @NonNls String = "file"
private const val URL_ATT: @NonNls String = "url"

@State(name = "DaemonCodeAnalyzer", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DaemonCodeAnalyzerImpl(project: Project) : DaemonCodeAnalyzerEx(), PersistentStateComponent<Element?>, Disposable {
  private val myProject: Project
  private val mySettings: DaemonCodeAnalyzerSettings
  private val myPsiDocumentManager: PsiDocumentManager
  private val fileEditorManager: Supplier<FileEditorManager>
  private val myUpdateProgress: MutableMap<FileEditor, DaemonProgressIndicator> = ConcurrentHashMap()
  private val myUpdateRunnable: UpdateRunnable

  @Volatile
  private var myUpdateRunnableFuture: Future<*> = CompletableFuture.completedFuture<Any?>(null)
  private var myUpdateByTimerEnabled = true // guarded by this
  private val myDisabledHintsFiles: MutableCollection<VirtualFile> = HashSet()
  private val myDisabledHighlightingFiles: MutableCollection<VirtualFile?> = HashSet()
  private val myFileStatusMap: FileStatusMap
  private var myLastSettings: DaemonCodeAnalyzerSettings?

  // the only possible transition: false -> true
  @Volatile
  private var isDisposed: Boolean
  private val myPassExecutorService: PassExecutorService

  // Timestamp of myUpdateRunnable which it's needed to start (in System.nanoTime() sense)
  // May be later than the actual ScheduledFuture sitting in the myAlarm queue.
  // When it happens that the future has started sooner than myScheduledUpdateStart, it will re-schedule itself for later.
  private var isScheduledUpdateTimestamp: Long = 0 // guarded by this

  @Volatile
  var isRestartToCompleteEssentialHighlightingRequested = false
    private set
  private val daemonCancelEventCount = AtomicInteger()
  private val daemonListenerPublisher: DaemonListener

  private val myDisableCount = AtomicInteger()

  init {
    // DependencyValidationManagerImpl adds scope listener, so we need to force service creation
    DependencyValidationManager.getInstance(project)
    myProject = project
    fileEditorManager = SynchronizedClearableLazy { FileEditorManager.getInstance(myProject) }
    mySettings = DaemonCodeAnalyzerSettings.getInstance()
    myPsiDocumentManager = PsiDocumentManager.getInstance(project)
    myLastSettings = (mySettings as DaemonCodeAnalyzerSettingsImpl).clone()
    myFileStatusMap = FileStatusMap(project)
    myPassExecutorService = PassExecutorService(project)
    Disposer.register(this, myPassExecutorService)
    Disposer.register(this, myFileStatusMap)
    @Suppress("TestOnlyProblems")
    DaemonProgressIndicator.setDebug(LOG.isDebugEnabled)
    Disposer.register(this, StatusBarUpdater(project))
    isDisposed = false
    myFileStatusMap.markAllFilesDirty("DaemonCodeAnalyzer init")
    myUpdateRunnable = UpdateRunnable(project)
    Disposer.register(this) {
      assert(!isDisposed) { "Double dispose" }
      myUpdateRunnable.clearFieldsOnDispose()
      stopProcess(false, "Dispose $project")
      isDisposed = true
      myLastSettings = null
    }
    daemonListenerPublisher = project.messageBus.syncPublisher(DAEMON_EVENT_TOPIC)
  }

  companion object {
    @TestOnly
    fun getHighlights(document: Document,
                      minSeverity: HighlightSeverity?,
                      project: Project): List<HighlightInfo> {
      val infos: List<HighlightInfo> = ArrayList()
      processHighlights(document, project, minSeverity, 0, document.textLength,
                        Processors.cancelableCollectProcessor(infos))
      return infos
    }

    fun processHighlightsNearOffset(document: Document,
                                    project: Project,
                                    minSeverity: HighlightSeverity,
                                    offset: Int,
                                    includeFixRange: Boolean,
                                    processor: Processor<HighlightInfo>): Boolean {
      return processHighlights(document, project, null, 0, document.textLength) { info ->
        if (!info.containsOffset(offset, includeFixRange)) {
          return@processHighlights true
        }

        val compare = info.severity.compareTo(minSeverity)
        compare < 0 || processor.process(info)
      }
    }

    @ApiStatus.Internal
    @ApiStatus.Experimental
    fun waitForUnresolvedReferencesQuickFixesUnderCaret(file: PsiFile, editor: Editor) {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      ApplicationManager.getApplication().assertReadAccessNotAllowed()
      val relevantInfos: MutableList<HighlightInfo> = ArrayList()
      val project = file.project
      ReadAction.run<RuntimeException> {
        PsiUtilBase.assertEditorAndProjectConsistent(project, editor)
        val caretModel = editor.caretModel
        val offset = caretModel.offset
        val document = editor.document
        val logicalLine = caretModel.logicalPosition.line
        processHighlights(document, project, null, 0, document.textLength) { info: HighlightInfo ->
          if (info.containsOffset(offset, true) && info.isUnresolvedReference) {
            relevantInfos.add(info)
            return@processHighlights true
          }
          // since we don't know fix ranges of potentially not-yet-added quick fixes, consider all HighlightInfos at the same line
          val atTheSameLine = editor.offsetToLogicalPosition(
            info.actualStartOffset).line <= logicalLine && logicalLine <= editor.offsetToLogicalPosition(info.actualEndOffset).line
          if (atTheSameLine && info.isUnresolvedReference) {
            relevantInfos.add(info)
          }
          true
        }
      }
      UnresolvedReferenceQuickFixUpdater.getInstance(project).waitQuickFixesSynchronously(file, editor, relevantInfos)
    }

    fun getLineMarkers(document: Document, project: Project): List<LineMarkerInfo<*>> {
      val result: List<LineMarkerInfo<*>> = ArrayList()
      LineMarkersUtil.processLineMarkers(project, document, TextRange(0, document.textLength), -1,
                                         CollectProcessor(result))
      return result
    }

    // made this class static and fields clearable to avoid leaks when this object stuck in invokeLater queue
    private class UpdateRunnable(project: Project) : Runnable {
      private var project: Project? = project

      override fun run() {
        runUpdate(project, this)
      }

      fun clearFieldsOnDispose() {
        project = null
      }
    }

    private fun runUpdate(project: Project?, updateRunnable: UpdateRunnable) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (project == null || project.isDefault || !project.isInitialized || project.isDisposed || LightEdit.owns(project)) {
        return
      }

      val dca = (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).takeIf { !it.isDisposed } ?: return

      if (PowerSaveMode.isEnabled()) {
        // to show the correct "power save" traffic light icon
        DaemonListeners.getInstance(project).repaintTrafficLightIconForAllEditors()
        return
      }

      synchronized(dca) {
        val actualDelay = dca.isScheduledUpdateTimestamp - System.nanoTime()
        if (actualDelay > 0) {
          // started too soon (there must've been some typings after we'd scheduled this; need to re-schedule)
          dca.scheduleUpdateRunnable(actualDelay)
          return
        }
      }

      val activeEditors = getSelectedEditors(project, dca.fileEditorManager)
      val updateByTimerEnabled = dca.isUpdateByTimerEnabled()
      if (PassExecutorService.LOG.isDebugEnabled) {
        PassExecutorService.log(null, null, "Update Runnable. myUpdateByTimerEnabled:",
                                updateByTimerEnabled, " something disposed:",
                                PowerSaveMode.isEnabled() || !project.isInitialized, " activeEditors:", activeEditors)
      }
      if (!updateByTimerEnabled || activeEditors.isEmpty()) {
        return
      }
      if (ApplicationManager.getApplication().isWriteAccessAllowed) {
        // makes no sense to start from within write action - will cancel anyway
        // we'll restart when the write action finish
        return
      }
      if (dca.myPsiDocumentManager.hasEventSystemEnabledUncommittedDocuments()) {
        // restart when everything committed
        dca.myPsiDocumentManager.performLaterWhenAllCommitted(updateRunnable)
        return
      }
      try {
        var submitted = false
        for (fileEditor in activeEditors) {
          val virtualFile = getVirtualFile(fileEditor) ?: continue
          val psiFile = findFileToHighlight(project = dca.myProject, virtualFile = virtualFile) ?: continue
          submitted = submitted or (dca.queuePassesCreation(fileEditor = fileEditor,
                                                            virtualFile = virtualFile,
                                                            psiFile = psiFile,
                                                            passesToIgnore = ArrayUtil.EMPTY_INT_ARRAY) != null)
        }
        if (!submitted) {
          // happens e.g., when we are trying to open a directory and there's a FileEditor supporting this
          dca.stopProcess(true, "Couldn't create session for $activeEditors")
        }
      }
      catch (ignored: ProcessCanceledException) {
      }
    }
  }

  @Synchronized
  override fun dispose() {
    clearReferences()
  }

  @Synchronized
  private fun clearReferences() {
    myUpdateProgress.values.forEach(Consumer { obj: DaemonProgressIndicator -> obj.cancel() })
    // avoid leak of highlight session via user data
    myUpdateProgress.clear()
    myUpdateRunnableFuture.cancel(true)
  }

  @Synchronized
  fun clearProgressIndicator() {
    myUpdateProgress.values.forEach(
      Consumer { indicator: DaemonProgressIndicator? ->
        HighlightingSessionImpl.clearProgressIndicator(
          indicator!!)
      })
  }

  @TestOnly
  fun getFileLevelHighlights(project: Project, file: PsiFile): List<HighlightInfo?> {
    assertMyFile(file.project, file)
    assertMyFile(project, file)
    val vFile = file.viewProvider.virtualFile
    return fileEditorManager.get().getAllEditors(vFile)
      .mapNotNull { it.getUserData(FILE_LEVEL_HIGHLIGHTS) }
      .flatten()
  }

  private fun assertMyFile(project: Project, file: PsiFile) {
    check(project === myProject) { "my project is $myProject but I was called with $project" }
    check(file.project === myProject) { "my project is " + myProject + " but I was called with file " + file + " from " + file.project }
  }

  override fun cleanFileLevelHighlights(group: Int, psiFile: PsiFile) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    assertMyFile(psiFile.project, psiFile)
    val vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.viewProvider.virtualFile)
    for (fileEditor in fileEditorManager.get().getAllEditors(vFile)) {
      cleanFileLevelHighlights(fileEditor, group)
    }
  }

  override fun hasFileLevelHighlights(group: Int, psiFile: PsiFile): Boolean {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    assertMyFile(psiFile.project, psiFile)
    val vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.viewProvider.virtualFile)
    for (fileEditor in fileEditorManager.get().getAllEditors(vFile)) {
      val infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS)
      if (!infos.isNullOrEmpty()) {
        for (info in infos) {
          if (info.group == group || group == ANY_GROUP) {
            return true
          }
        }
      }
    }
    return false
  }

  fun cleanAllFileLevelHighlights() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    for (fileEditor in fileEditorManager.get().allEditors) {
      cleanFileLevelHighlights(fileEditor, ANY_GROUP)
    }
  }

  private fun cleanFileLevelHighlights(fileEditor: FileEditor, group: Int) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS)
    if (infos.isNullOrEmpty()) {
      return
    }

    val infosToRemove = ArrayList<HighlightInfo>(infos.size)
    for (info in infos) {
      if (info.group == group || group == ANY_GROUP) {
        val component = info.getFileLevelComponent(fileEditor)
        if (component != null) {
          fileEditorManager.get().removeTopComponent(fileEditor, component)
          info.removeFileLeverComponent(fileEditor)
        }
        infosToRemove.add(info)
      }
    }
    infos.removeAll(infosToRemove)
  }

  override fun addFileLevelHighlight(group: Int, info: HighlightInfo, psiFile: PsiFile) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    assertMyFile(psiFile.project, psiFile)
    val vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.viewProvider.virtualFile)
    val fileEditorManager = fileEditorManager.get()
    for (fileEditor in fileEditorManager.getAllEditors(vFile)) {
      if (fileEditor is TextEditor) {
        val actionRanges: MutableList<Pair<IntentionActionDescriptor, TextRange>> = ArrayList()
        info.findRegisteredQuickFix<Any> { descriptor: IntentionActionDescriptor, range: TextRange ->
          actionRanges.add(
            Pair.create(descriptor, range))
          null
        }
        val component = FileLevelIntentionComponent(info.description, info.severity,
                                                    info.gutterIconRenderer, actionRanges,
                                                    psiFile, fileEditor.editor, info.toolTip)
        fileEditorManager.addTopComponent(fileEditor, component)
        var fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS)
        if (fileLevelInfos == null) {
          fileLevelInfos = ArrayList()
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos)
        }
        info.addFileLeverComponent(fileEditor, component)
        info.group = group
        fileLevelInfos.add(info)
      }
    }
  }

  override fun runMainPasses(psiFile: PsiFile, document: Document, progress: ProgressIndicator): List<HighlightInfo> {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    check(
      !ApplicationManager.getApplication().isReadAccessAllowed) { "Must run highlighting outside read action, external annotators do not support checkCanceled" }
    assertMyFile(psiFile.project, psiFile)
    GlobalInspectionContextBase.assertUnderDaemonProgress()
    // clear status maps to run passes from scratch so that refCountHolder won't conflict and try to restart itself on partially filled maps
    myFileStatusMap.markAllFilesDirty("prepare to run main passes")
    stopProcess(false, "disable background daemon")
    myPassExecutorService.cancelAll(true)
    val result: MutableList<HighlightInfo>
    try {
      result = ArrayList()
      val virtualFile = psiFile.virtualFile
      if (virtualFile != null && !virtualFile.fileType.isBinary) {
        val passes = DumbService.getInstance(myProject).runReadActionInSmartMode<List<TextEditorHighlightingPass>> {
          val mainPasses = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject)
            .instantiateMainPasses(psiFile, document, HighlightInfoProcessor.getEmpty())
          mainPasses.sortWith(Comparator<TextEditorHighlightingPass> { o1, o2 ->
            if (o1 is GeneralHighlightingPass) return@Comparator -1
            if (o2 is GeneralHighlightingPass) return@Comparator 1
            0
          })
          try {
            for (pass in mainPasses) {
              pass.doCollectInformation(progress)
            }
          }
          catch (e: ProcessCanceledException) {
            LOG.debug("Canceled: $progress")
            throw e
          }
          mainPasses
        }
        try {
          for (pass in passes) {
            result.addAll(pass.infos)
          }
        }
        catch (e: ProcessCanceledException) {
          LOG.debug("Canceled: $progress")
          throw e
        }
      }
    }
    finally {
      stopProcess(true, "re-enable background daemon after main passes run")
    }
    return result
  }

  @Volatile
  private var mustWaitForSmartMode = true

  @TestOnly
  fun mustWaitForSmartMode(mustWait: Boolean, parent: Disposable) {
    val old = mustWaitForSmartMode
    mustWaitForSmartMode = mustWait
    Disposer.register(parent) { mustWaitForSmartMode = old }
  }

  @TestOnly
  @Throws(ProcessCanceledException::class)
  fun runPasses(file: PsiFile,
                document: Document,
                textEditor: TextEditor,
                passesToIgnore: IntArray,
                canChangeDocument: Boolean,
                callbackWhileWaiting: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    assert(!isDisposed)
    assertMyFile(file.project, file)
    assert(
      textEditor.editor.document === document) { "Expected document " + document + " but one of the passed TextEditors points to a different document: " + textEditor.editor.document }
    val associatedDocument = PsiDocumentManager.getInstance(myProject).getDocument(file)
    assert(
      associatedDocument === document) { "Expected document $document but the passed PsiFile points to a different document: $associatedDocument" }
    check(
      !ApplicationManager.getApplication().isWriteAccessAllowed) { "Must not start highlighting from within write action, or deadlock is imminent" }
    DaemonProgressIndicator.setDebug(!ApplicationManagerEx.isInStressTest())
    (FileTypeManager.getInstance() as FileTypeManagerImpl).drainReDetectQueue()
    do {
      EDT.dispatchAllInvocationEvents()
      // refresh will fire write actions interfering with highlighting
      // heavy ops are bad, but VFS refresh is ok
    }
    while (RefreshQueueImpl.isRefreshInProgress() || heavyProcessIsRunning())
    val dStart = System.currentTimeMillis()
    while (mustWaitForSmartMode && DumbService.getInstance(myProject).isDumb) {
      check(
        System.currentTimeMillis() <= dStart + 100000) { "Timeout waiting for smart mode. If you absolutely want to be dumb, please use DaemonCodeAnalyzerImpl.mustWaitForSmartMode(false)." }
      EDT.dispatchAllInvocationEvents()
    }
    (GistManager.getInstance() as GistManagerImpl).clearQueueInTests()
    EDT.dispatchAllInvocationEvents()
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion() // wait for async editor loading
    myUpdateRunnableFuture.cancel(false)

    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myPassExecutorService.cancelAll(false)
    val fileStatusMap = fileStatusMap
    val old = fileStatusMap.allowDirt(canChangeDocument)
    for (ignoreId in passesToIgnore) {
      fileStatusMap.markFileUpToDate(document, ignoreId)
    }
    try {
      doRunPasses(textEditor, passesToIgnore, canChangeDocument, callbackWhileWaiting)
    }
    finally {
      DaemonProgressIndicator.setDebug(false)
      fileStatusMap.allowDirt(old)
    }
  }

  @TestOnly
  private fun doRunPasses(textEditor: TextEditor,
                          passesToIgnore: IntArray,
                          canChangeDocument: Boolean,
                          callbackWhileWaiting: Runnable?) {
    (ProgressManager.getInstance() as CoreProgressManager).suppressAllDeprioritizationsDuringLongTestsExecutionIn<Any?, RuntimeException> {
      val virtualFile = textEditor.file
      var psiFile = PsiManagerEx.getInstanceEx(myProject).findFile(
        virtualFile) // findCachedFile doesn't work with the temp file system in tests
      psiFile = if (psiFile is PsiCompiledFile) psiFile.decompiledPsiFile else psiFile
      LOG.assertTrue(psiFile != null, "PsiFile not found for $virtualFile")
      val session = queuePassesCreation(textEditor, virtualFile, psiFile!!, passesToIgnore)
      if (session == null) {
        LOG.error("Can't create session for " + textEditor + " (" + textEditor.javaClass + ")," +
                  " fileEditor.getBackgroundHighlighter()=" + textEditor.backgroundHighlighter +
                  "; virtualFile=" + virtualFile +
                  "; psiFile=" + psiFile)
        throw ProcessCanceledException()
      }
      val progress = session.progressIndicator
      // there can be PCE in FJP during queuePassesCreation
      // no PCE guarantees session is not null
      progress.checkCanceled()
      try {
        val start = System.currentTimeMillis()
        waitInOtherThread(600_000, canChangeDocument) {
          progress.checkCanceled()
          callbackWhileWaiting?.run()
          // give other threads a chance to do smth useful
          if (System.currentTimeMillis() > start + 50) {
            TimeoutUtil.sleep(10)
          }
          EDT.dispatchAllInvocationEvents()
          val savedException = PassExecutorService.getSavedException((progress as DaemonProgressIndicator))
          if (savedException != null) throw savedException
          progress.isRunning()
        }
        if (progress.isRunning && !progress.isCanceled) {
          throw RuntimeException(
            """Highlighting still running after ${(System.currentTimeMillis() - start) / 1000} seconds. Still submitted passes: ${myPassExecutorService.allSubmittedPasses} ForkJoinPool.commonPool(): ${ForkJoinPool.commonPool()}
, ForkJoinPool.commonPool() active thread count: ${ForkJoinPool.commonPool().activeThreadCount}, ForkJoinPool.commonPool() has queued submissions: ${ForkJoinPool.commonPool().hasQueuedSubmissions()}
${ThreadDumper.dumpThreadsToString()}""")
        }
        (session as HighlightingSessionImpl).waitForHighlightInfosApplied()
        EDT.dispatchAllInvocationEvents()
        EDT.dispatchAllInvocationEvents()
        assert(progress.isCanceled)
      }
      catch (e: Throwable) {
        val unwrapped = ExceptionUtilRt.unwrapException(e, ExecutionException::class.java)
        if (progress.isCanceled && progress.isRunning) {
          unwrapped.addSuppressed(RuntimeException("Daemon progress was canceled unexpectedly: $progress"))
        }
        ExceptionUtil.rethrow(unwrapped)
      }
      finally {
        progress.cancel()
        waitForTermination()
      }
      null
    }
  }

  @TestOnly
  @Throws(Throwable::class)
  private fun waitInOtherThread(@Suppress("SameParameterValue") millis: Int, canChangeDocument: Boolean, runWhile: ThrowableComputable<Boolean, Throwable>) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val disposable = Disposer.newDisposable()
    val assertOnModification = AtomicBoolean()
    // last hope protection against PsiModificationTrackerImpl.incCounter() craziness (yes, Kotlin)
    myProject.messageBus.connect(disposable).subscribe(PsiModificationTracker.TOPIC,
                                                       PsiModificationTracker.Listener {
                                                         check(
                                                           !assertOnModification.get()) { "You must not perform PSI modifications from inside highlighting" }
                                                       })
    if (!canChangeDocument) {
      myProject.messageBus.connect(disposable).subscribe<DaemonListener>(DAEMON_EVENT_TOPIC, object : DaemonListener {
        override fun daemonCancelEventOccurred(reason: String) {
          check(!assertOnModification.get()) { "You must not cancel daemon inside highlighting test: $reason" }
        }
      })
    }
    val deadline = System.currentTimeMillis() + millis
    try {
      val future = ApplicationManager.getApplication().executeOnPooledThread<Boolean> {
        try {
          return@executeOnPooledThread myPassExecutorService.waitFor(millis)
        }
        catch (e: Throwable) {
          throw RuntimeException(e)
        }
      }
      do {
        assertOnModification.set(true)
        try {
          future[50, TimeUnit.MILLISECONDS]
        }
        catch (ignored: TimeoutException) {
        }
        finally {
          assertOnModification.set(false) //do not assert during dispatchAllEvents() because that's where all quick fixes happen
        }
      }
      while (runWhile.compute() && System.currentTimeMillis() < deadline)
    }
    catch (ignored: InterruptedException) {
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @TestOnly
  fun prepareForTest() {
    setUpdateByTimerEnabled(false)
    waitForTermination()
    clearReferences()
  }

  @TestOnly
  fun cleanupAfterTest() {
    if (myProject.isOpen) {
      prepareForTest()
    }
  }

  @TestOnly
  fun waitForTermination() {
    myPassExecutorService.cancelAll(true)
  }

  override fun settingsChanged() {
    if (mySettings.isCodeHighlightingChanged(myLastSettings)) {
      restart()
    }
    myLastSettings = (mySettings as DaemonCodeAnalyzerSettingsImpl).clone()
  }

  @Synchronized
  override fun setUpdateByTimerEnabled(value: Boolean) {
    myUpdateByTimerEnabled = value
    stopProcess(value, "Update by timer change")
  }

  override fun disableUpdateByTimer(parentDisposable: Disposable) {
    setUpdateByTimerEnabled(false)
    myDisableCount.incrementAndGet()
    ApplicationManager.getApplication().assertIsDispatchThread()
    Disposer.register(parentDisposable) {
      if (myDisableCount.decrementAndGet() == 0) {
        setUpdateByTimerEnabled(true)
      }
    }
  }

  @Synchronized
  fun isUpdateByTimerEnabled(): Boolean {
    return myUpdateByTimerEnabled
  }

  override fun setImportHintsEnabled(file: PsiFile, value: Boolean) {
    assertMyFile(file.project, file)
    val vFile = file.virtualFile
    if (value) {
      myDisabledHintsFiles.remove(vFile)
      stopProcess(true, "Import hints change")
    }
    else {
      myDisabledHintsFiles.add(vFile)
      HintManager.getInstance().hideAllHints()
    }
  }

  override fun resetImportHintsEnabledForProject() {
    myDisabledHintsFiles.clear()
  }

  override fun setHighlightingEnabled(psiFile: PsiFile, value: Boolean) {
    assertMyFile(psiFile.project, psiFile)
    val virtualFile = PsiUtilCore.getVirtualFile(psiFile)
    if (value) {
      myDisabledHighlightingFiles.remove(virtualFile)
    }
    else {
      myDisabledHighlightingFiles.add(virtualFile)
    }
  }

  override fun isHighlightingAvailable(psiFile: PsiFile): Boolean {
    if (!psiFile.isPhysical) return false
    assertMyFile(psiFile.project, psiFile)
    if (myDisabledHighlightingFiles.contains(PsiUtilCore.getVirtualFile(psiFile))) return false
    if (psiFile is PsiCompiledElement) return false
    val fileType = psiFile.fileType

    // To enable T.O.D.O. highlighting
    return !fileType.isBinary
  }

  override fun isImportHintsEnabled(psiFile: PsiFile): Boolean {
    return isAutohintsAvailable(psiFile) && !myDisabledHintsFiles.contains(psiFile.virtualFile)
  }

  override fun isAutohintsAvailable(psiFile: PsiFile): Boolean {
    return isHighlightingAvailable(psiFile) && psiFile !is PsiCompiledElement
  }

  override fun restart() {
    stopProcessAndRestartAllFiles("Global restart")
  }

  // return true if the progress was really canceled
  fun stopProcessAndRestartAllFiles(reason: String) {
    myFileStatusMap.markAllFilesDirty(reason)
    stopProcess(true, reason)
  }

  override fun restart(psiFile: PsiFile) {
    assertMyFile(psiFile.project, psiFile)
    val document = psiFile.viewProvider.document ?: return
    val reason = "Psi file restart: " + psiFile.name
    myFileStatusMap.markFileScopeDirty(document, TextRange(0, document.textLength), psiFile.textLength, reason)
    stopProcess(true, reason)
  }

  fun getPassesToShowProgressFor(document: Document): List<ProgressableTextEditorHighlightingPass?> {
    val allPasses = myPassExecutorService.allSubmittedPasses
    return allPasses
      .asSequence()
      .mapNotNull { if (it is ProgressableTextEditorHighlightingPass && it.document === document) it else null }
      .sortedWith(Comparator.comparingInt { it.id })
      .toList()
  }

  fun isAllAnalysisFinished(psiFile: PsiFile): Boolean {
    if (isDisposed) return false
    assertMyFile(psiFile.project, psiFile)
    val document = psiFile.viewProvider.document
    return document != null && document.modificationStamp == psiFile.viewProvider.modificationStamp &&
           myFileStatusMap.allDirtyScopesAreNull(document)
  }

  override fun isErrorAnalyzingFinished(psiFile: PsiFile): Boolean {
    if (isDisposed) return false
    assertMyFile(psiFile.project, psiFile)
    val document = psiFile.viewProvider.document
    return document != null && document.modificationStamp == psiFile.viewProvider.modificationStamp && myFileStatusMap.getFileDirtyScope(
      document, psiFile, Pass.UPDATE_ALL) == null
  }

  override fun getFileStatusMap(): FileStatusMap {
    return myFileStatusMap
  }

  @get:Synchronized
  val isRunning: Boolean
    get() {
      for (indicator in myUpdateProgress.values) {
        if (!indicator.isCanceled) {
          return true
        }
      }
      return false
    }

  @get:TestOnly
  val isRunningOrPending: Boolean
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return isRunning || !myUpdateRunnableFuture.isDone || GeneralHighlightingPass.isRestartPending()
    }

  // return true if the progress really was canceled
  @Synchronized
  fun stopProcess(toRestartAlarm: Boolean, reason: @NonNls String) {
    cancelAllUpdateProgresses(toRestartAlarm, reason)
    val restart = toRestartAlarm && !isDisposed

    // reset myScheduledUpdateStart always, but re-schedule myUpdateRunnable only rarely because of thread scheduling overhead
    val autoReparseDelayNanos = TimeUnit.MILLISECONDS.toNanos(mySettings.autoReparseDelay.toLong())
    if (restart) {
      isScheduledUpdateTimestamp = System.nanoTime() + autoReparseDelayNanos
    }
    // optimisation: this check is to avoid too many re-schedules in case of thousands of event spikes
    val isDone = myUpdateRunnableFuture.isDone
    if (restart && isDone) {
      scheduleUpdateRunnable(autoReparseDelayNanos)
    }
  }

  @Synchronized
  private fun scheduleUpdateRunnable(delayNanos: Long) {
    val oldFuture = myUpdateRunnableFuture
    if (oldFuture.isDone) {
      ConcurrencyUtil.manifestExceptionsIn(oldFuture)
    }
    myUpdateRunnableFuture = EdtExecutorService.getScheduledExecutorInstance().schedule(myUpdateRunnable, delayNanos, TimeUnit.NANOSECONDS)
  }

  // return true if the progress really was canceled
  @Synchronized
  fun cancelAllUpdateProgresses(toRestartAlarm: Boolean, reason: @NonNls String) {
    if (isDisposed || myProject.isDisposed || myProject.messageBus.isDisposed) return
    var canceled = false
    for (updateProgress in myUpdateProgress.values) {
      if (!updateProgress.isCanceled) {
        PassExecutorService.log(updateProgress, null, "Cancel", reason, toRestartAlarm)
        updateProgress.cancel()
        myPassExecutorService.cancelAll(false)
        canceled = true
      }
    }
    if (canceled) {
      daemonListenerPublisher.daemonCancelEventOccurred(reason)
    }
    daemonCancelEventCount.incrementAndGet()
  }

  fun findHighlightByOffset(document: Document, offset: Int, includeFixRange: Boolean): HighlightInfo? {
    return findHighlightByOffset(document, offset, includeFixRange, HighlightSeverity.INFORMATION)
  }

  fun findHighlightByOffset(document: Document,
                            offset: Int,
                            includeFixRange: Boolean,
                            minSeverity: HighlightSeverity): HighlightInfo? {
    return findHighlightsByOffset(document, offset, includeFixRange, true, minSeverity)
  }

  /**
   * Collects HighlightInfos intersecting with a certain offset.
   * If there are several HighlightInfos, they're combined into HighlightInfoComposite and returned as a single object.
   * Several options are available to adjust the collecting strategy
   *
   * @param document document in which the collecting is performed
   * @param offset offset which the info should intersect with to be collected
   * @param includeFixRange states whether the range of a fix associated with the info should be taken into account during the range checking
   * @param highestPriorityOnly states whether to include all infos, or only the ones with the highest HighlightSeverity
   * @param minSeverity the minimum HighlightSeverity starting from which infos are considered
   */
  fun findHighlightsByOffset(document: Document,
                             offset: Int,
                             includeFixRange: Boolean,
                             highestPriorityOnly: Boolean,
                             minSeverity: HighlightSeverity): HighlightInfo? {
    val processor = HighlightByOffsetProcessor(highestPriorityOnly)
    processHighlightsNearOffset(document, myProject, minSeverity, offset, includeFixRange, processor)
    return processor.result
  }

  internal class HighlightByOffsetProcessor(private val highestPriorityOnly: Boolean) : Processor<HighlightInfo> {
    private val foundInfoList = SmartList<HighlightInfo>()

    override fun process(info: HighlightInfo): Boolean {
      if (info.severity === HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY || info.type === HighlightInfoType.TODO) {
        return true
      }
      if (!foundInfoList.isEmpty() && highestPriorityOnly) {
        val foundInfo = foundInfoList[0]
        val compare = foundInfo.severity.compareTo(info.severity)
        if (compare < 0) {
          foundInfoList.clear()
        }
        else if (compare > 0) {
          return true
        }
      }
      foundInfoList.add(info)
      return true
    }

    val result: HighlightInfo?
      get() {
        if (foundInfoList.isEmpty()) {
          return null
        }
        if (foundInfoList.size == 1) {
          return foundInfoList[0]
        }
        foundInfoList.sortWith(Comparator.comparing { obj: HighlightInfo -> obj.severity }.reversed())
        return HighlightInfoComposite.create(foundInfoList)
      }
  }

  val lastIntentionHint: IntentionHintComponent?
    get() = (IntentionsUI.getInstance(myProject) as IntentionsUIImpl).lastIntentionHint

  override fun hasVisibleLightBulbOrPopup(): Boolean {
    val hint = lastIntentionHint
    return hint != null && hint.hasVisibleLightBulbOrPopup()
  }

  override fun getState(): Element {
    val state = Element("state")
    if (myDisabledHintsFiles.isEmpty()) {
      return state
    }
    val array: MutableList<String> = ArrayList(myDisabledHintsFiles.size)
    for (file in myDisabledHintsFiles) {
      if (file.isValid) {
        array.add(file.url)
      }
    }
    if (!array.isEmpty()) {
      array.sort()
      val disableHintsElement = Element(DISABLE_HINTS_TAG)
      state.addContent(disableHintsElement)
      for (url in array) {
        disableHintsElement.addContent(Element(FILE_TAG).setAttribute(URL_ATT, url))
      }
    }
    return state
  }

  override fun loadState(state: Element) {
    myDisabledHintsFiles.clear()
    val element = state.getChild(DISABLE_HINTS_TAG)
    if (element != null) {
      for (e in element.getChildren(FILE_TAG)) {
        val url = e.getAttributeValue(URL_ATT)
        if (url != null) {
          val file = VirtualFileManager.getInstance().findFileByUrl(url)
          if (file != null) {
            myDisabledHintsFiles.add(file)
          }
        }
      }
    }
  }

  /**
   * @return HighlightingSession when everything's OK or
   * return null if session wasn't created because highlighter/document/psiFile wasn't found or
   * throw PCE if it really wasn't an appropriate moment to ask
   */
  private fun queuePassesCreation(fileEditor: FileEditor,
                                  virtualFile: VirtualFile,
                                  psiFile: PsiFile,
                                  passesToIgnore: IntArray): HighlightingSession? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var highlighter: BackgroundEditorHighlighter?
    withClientId(getClientId(fileEditor)).use { highlighter = fileEditor.backgroundHighlighter }
    if (highlighter == null) {
      return null
    }
    val progress = createUpdateProgress(fileEditor)
    // pre-create HighlightingSession in EDT to make visible range available in a background thread
    val editor = if (fileEditor is TextEditor) fileEditor.editor else null
    if (editor != null && editor.document.isInBulkUpdate) {
      // avoid restarts until the bulk mode is finished and daemon restarted in DaemonListeners
      stopProcess(false, editor.document.toString() + " is in bulk state")
      throw ProcessCanceledException()
    }
    val document = (if (fileEditor is TextEditor) fileEditor.editor.document
    else FileDocumentManager.getInstance().getCachedDocument(virtualFile))
                   ?: return null
    val scheme = editor?.colorsScheme
    var session: HighlightingSessionImpl
    withClientId(getClientId(fileEditor)).use {
      session = HighlightingSessionImpl.createHighlightingSession(psiFile, editor, scheme, progress, daemonCancelEventCount)
    }
    JobLauncher.getInstance().submitToJobThread(
      { submitInBackground(fileEditor, document, virtualFile, psiFile, highlighter!!, passesToIgnore, progress, session) }
    )  // manifest exceptions in EDT to avoid storing them in the Future and abandoning
    { task: Future<*>? ->
      ApplicationManager.getApplication().invokeLater {
        ConcurrencyUtil.manifestExceptionsIn(
          task!!)
      }
    }
    return session
  }

  private fun submitInBackground(fileEditor: FileEditor,
                                 document: Document,
                                 virtualFile: VirtualFile,
                                 psiFile: PsiFile,
                                 backgroundEditorHighlighter: BackgroundEditorHighlighter,
                                 passesToIgnore: IntArray,
                                 progress: DaemonProgressIndicator,
                                 session: HighlightingSessionImpl) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    if (myProject.isDisposed) {
      stopProcess(false, "project disposed")
      return
    }

    if (progress.isCanceled) {
      stopProcess(true, "canceled in queuePassesCreation: " + progress.cancellationTrace)
      return
    }

    if (myPsiDocumentManager.hasEventSystemEnabledUncommittedDocuments()) {
      stopProcess(true,
                  "more documents to commit: " + ReadAction.compute<String, RuntimeException> { myPsiDocumentManager.uncommittedDocuments.contentToString() })
      return
    }

    try {
      ProgressManager.getInstance().executeProcessUnderProgress(
        {
          // wait for heavy processing to stop, re-schedule daemon but not too soon
          val heavyProcessIsRunning = heavyProcessIsRunning()
          val passes = ReadAction.compute<Array<HighlightingPass>, RuntimeException> {
            if (myProject.isDisposed || !fileEditor.isValid || !psiFile.isValid) {
              return@compute HighlightingPass.EMPTY_ARRAY
            }

            if (session.isCanceled) {
              // editor or something was changed between commit document notification in EDT and this point in the FJP thread
              throw ProcessCanceledException()
            }

            session.additionalSetupFromBackground(psiFile)
            withClientId(getClientId(fileEditor)).use { _ ->
              var r = if (backgroundEditorHighlighter is TextEditorBackgroundHighlighter) {
                backgroundEditorHighlighter.getPasses(passesToIgnore).toArray(HighlightingPass.EMPTY_ARRAY)
              }
              else {
                backgroundEditorHighlighter.createPassesForEditor()
              }
              if (heavyProcessIsRunning && !r.isEmpty()) {
                r = r.filter { DumbService.isDumbAware(it) }.toArray(HighlightingPass.EMPTY_ARRAY)
              }
              return@compute r
            }
          }

          val hasPasses = passes.isNotEmpty()
          if (!hasPasses) {
            // will be re-scheduled by HeavyLatch listener in DaemonListeners
            return@executeProcessUnderProgress
          }
          // synchronize on TextEditorHighlightingPassRegistrarImpl instance to avoid concurrent modification of TextEditorHighlightingPassRegistrarImpl.nextAvailableId
          synchronized(TextEditorHighlightingPassRegistrar.getInstance(myProject)) {
            myPassExecutorService.submitPasses(document, virtualFile, psiFile,
                                               fileEditor, passes, progress)
          }
        },
        progress,
      )
    }
    catch (e: ProcessCanceledException) {
      stopProcess(true, "PCE in queuePassesCreation")
    }
    catch (e: Throwable) {
      // make it manifestable in tests
      PassExecutorService.saveException(e, progress)
      throw e
    }
  }

  @Synchronized
  private fun createUpdateProgress(fileEditor: FileEditor): DaemonProgressIndicator {
    val old = myUpdateProgress[fileEditor]
    if (old != null && !old.isCanceled) {
      old.cancel()
    }
    myUpdateProgress.entries.removeIf { (key): Map.Entry<FileEditor, DaemonProgressIndicator> -> !key.isValid }
    val progress: DaemonProgressIndicator = MyDaemonProgressIndicator(myProject, fileEditor)
    progress.setModalityProgress(null)
    progress.start()
    daemonListenerPublisher.daemonStarting(listOf(fileEditor))
    if (isRestartToCompleteEssentialHighlightingRequested) {
      progress.putUserData(COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY, true)
    }
    myUpdateProgress[fileEditor] = progress
    return progress
  }

  private class MyDaemonProgressIndicator(project: Project, fileEditor: FileEditor) : DaemonProgressIndicator() {
    private val project: Project
    private var fileEditor: FileEditor?

    init {
      this.fileEditor = fileEditor
      this.project = project
    }

    public override fun stopIfRunning(): Boolean {
      val wasStopped = super.stopIfRunning()
      if (wasStopped) {
        val daemon = getInstance(project) as DaemonCodeAnalyzerImpl
        daemon.daemonListenerPublisher.daemonFinished(listOf(fileEditor!!))
        fileEditor = null
        HighlightingSessionImpl.clearProgressIndicator(this)
        daemon.isRestartToCompleteEssentialHighlightingRequested = false
      }
      return wasStopped
    }
  }

  override fun autoImportReferenceAtCursor(editor: Editor, psiFile: PsiFile) {
    assertMyFile(psiFile.project, psiFile)
    for (importer in ReferenceImporter.EP_NAME.extensionList) {
      if (importer.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) && importer.autoImportReferenceAtCursor(editor, psiFile)) break
    }
  }

  @get:Synchronized
  @get:TestOnly
  val updateProgress: Map<FileEditor, DaemonProgressIndicator>
    get() = myUpdateProgress

  /**
   * This API is made `Internal` intentionally as it could lead to unpredictable highlighting performance behaviour.
   *
   * @param flag if `true`: enables code insight passes serialization:
   * Injected fragments [InjectedGeneralHighlightingPass] highlighting and Inspections run after
   * completion of Syntax analysis [GeneralHighlightingPass].
   * if `false` (default behaviour) code insight passes are running in parallel
   */
  @ApiStatus.Internal
  fun serializeCodeInsightPasses(flag: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setUpdateByTimerEnabled(false)
    try {
      cancelAllUpdateProgresses(false, "serializeCodeInsightPasses")
      val registrar = TextEditorHighlightingPassRegistrar.getInstance(myProject) as TextEditorHighlightingPassRegistrarImpl
      registrar.serializeCodeInsightPasses(flag)
    }
    finally {
      setUpdateByTimerEnabled(true)
    }
  }

  // tell the next restarted highlighting that it should start all inspections/external annotators/etc
  fun restartToCompleteEssentialHighlighting() {
    restart()
    isRestartToCompleteEssentialHighlightingRequested = true
  }
}

private fun getVirtualFile(fileEditor: FileEditor): VirtualFile? {
  val virtualFile = fileEditor.file
  for (provider in BackedVirtualFileProvider.EP_NAME.extensionList) {
    val replacedVirtualFile = provider.getReplacedVirtualFile(virtualFile)
    if (replacedVirtualFile != null) {
      return replacedVirtualFile
    }
  }
  return virtualFile
}

private fun findFileToHighlight(project: Project, virtualFile: VirtualFile?): PsiFile? {
  val psiFile = if (virtualFile == null || !virtualFile.isValid) {
    null
  }
  else {
    (PsiManagerEx.getInstanceEx(project).fileManager as FileManagerImpl).getFastCachedPsiFile(virtualFile)
  }
  return if (psiFile is PsiCompiledFile) psiFile.decompiledPsiFile else psiFile
}

// return true if a heavy op is running
private fun heavyProcessIsRunning(): Boolean {
  // VFS refresh is OK
  return if (DumbServiceImpl.ALWAYS_SMART) false else HeavyProcessLatch.INSTANCE.isRunningAnythingBut(HeavyProcessLatch.Type.Syncing)
}

private fun getSelectedEditors(project: Project, fileEditorManagerSupplier: Supplier<FileEditorManager>): Collection<FileEditor> {
  val app = ApplicationManager.getApplication()
  app.assertIsDispatchThread()

  // editors in modal context
  val editors = project.serviceIfCreated<EditorTracker>()?.activeEditors ?: emptyList()
  val activeTextEditors: Sequence<TextEditor> = if (editors.isEmpty()) {
    emptySequence()
  }
  else {
    val textEditorProvider = TextEditorProvider.getInstance()
    editors.asSequence()
      .mapNotNull { editor ->
        if (editor.isDisposed) null else textEditorProvider.getTextEditor(editor)
      }
      .distinct()
  }
  if (app.currentModalityState !== ModalityState.NON_MODAL) {
    return activeTextEditors.toList()
  }

  // tests usually care about just one explicitly configured editor
  val tabEditors = if (app.isUnitTestMode) {
    emptySequence<FileEditor>()
  }
  else {
    fileEditorManagerSupplier.get().selectedEditorWithRemotes.asSequence()
  }
  return (activeTextEditors + tabEditors)
    .filter { it.isValid && it.file != null && it.file.isValid }
    .toList()
}