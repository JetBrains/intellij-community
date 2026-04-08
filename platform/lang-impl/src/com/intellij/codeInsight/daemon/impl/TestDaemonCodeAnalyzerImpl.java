// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.CodeInsightContextManager;
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl;
import com.intellij.codeInsight.multiverse.CodeInsightContextUtil;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.IdeEventQueue;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.application.impl.TestOnlyThreading;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.MarkupModelImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.platform.backend.observation.Observation;
import com.intellij.psi.PsiConsistencyAssertions;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.AWTEvent;
import java.awt.event.InvocationEvent;
import java.lang.ref.Reference;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * methods for running {@link DaemonCodeAnalyzer} in tests and check its results
 */
@TestOnly
@ApiStatus.Internal
public final class TestDaemonCodeAnalyzerImpl {
  @NotNull private final Project myProject;
  private final @NotNull DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private boolean mustWaitForSmartModeByDefault = true;

  public TestDaemonCodeAnalyzerImpl(@NotNull Project project) {
    myProject = project;
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    assert !myDaemonCodeAnalyzer.myDisposed;
  }

  /**
   * do not run in production since it differs slightly from the {@link DaemonCodeAnalyzerImpl#runUpdate()}
   */
  public void runPasses(@NotNull PsiFile psiFile,
                        @NotNull Document document,
                        @NotNull TextEditor textEditor,
                        int @NotNull [] passesToIgnore,
                        boolean canChangeDocument,
                        boolean mustWaitForSmartMode,
                        @Nullable Runnable callbackWhileWaiting) throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    PsiUtilCore.ensureValid(psiFile);
    myDaemonCodeAnalyzer.assertFileFromMyProject(myProject, psiFile);
    Editor editor = textEditor.getEditor();
    assert editor.getDocument() == document : "Expected document " + document +
                                              " but the passed TextEditor points to a different document: " + editor.getDocument();
    Document associatedDocument = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    assert associatedDocument == document : "Expected document " + document + " but the passed PsiFile points to a different document: " + associatedDocument;
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed()) {
      throw new IllegalStateException("Must not start highlighting from within write action, or deadlock is imminent");
    }
    assert application.isUnitTestMode();

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    waitForAllThingsBeforeDaemonStart(mustWaitForSmartMode, 10_000);
    myDaemonCodeAnalyzer.clearReferences();
    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myDaemonCodeAnalyzer.myPassExecutorService.cancelAll(false, "DaemonCodeAnalyzerImpl.runPasses");

    PsiConsistencyAssertions.assertNoFileTextMismatch(psiFile, editor.getDocument(), null);
    CodeInsightContext context = CodeInsightContextUtil.getCodeInsightContext(psiFile);
    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();
    fileStatusMap.runAllowingDirt(canChangeDocument, () -> {
      for (int ignoreId : passesToIgnore) {
        fileStatusMap.markFileUpToDate(document, context, ignoreId, null);
      }
      ThrowableRunnable<Exception> doRunPasses = () -> doRunPasses(myDaemonCodeAnalyzer, textEditor, passesToIgnore, canChangeDocument, callbackWhileWaiting);
      boolean isDebugMode = !ApplicationManagerEx.isInStressTest();
      if (isDebugMode) {
        DaemonProgressIndicator.runInDebugMode(doRunPasses);
      }
      else {
        doRunPasses.run();
      }
    });
  }

  @RequiresEdt
  private void waitForAllThingsBeforeDaemonStart(boolean mustWaitForSmartMode, long timeoutMs) {
    ThreadingAssertions.assertEventDispatchThread();
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    do {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      // refresh will fire write actions interfering with highlighting
    }
    while (RefreshQueueImpl.isRefreshInProgress() || DaemonCodeAnalyzerImpl.heavyProcessIsRunning());
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (mustWaitForSmartMode && DumbService.getInstance(myProject).isDumb()) {
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("Timeout waiting for smart mode. If you absolutely want to be dumb, please use DaemonCodeAnalyzerImpl.mustWaitForSmartMode(false).");
      }
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    }
    ((GistManagerImpl)GistManager.getInstance()).clearQueueInTests();
    dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // wait for async editor loading

    // tracked activities (e.g., external system import, vendor scan) may fire write actions
    // that trigger rootsChanged — same reason we wait for VFS refresh above
    while (Observation.INSTANCE.configurationFlow(myProject).getValue()) {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    }
    // update the file status map before prohibiting its modifications
    waitForUpdateFileStatusBackgroundQueueInTests();
    try {
      waitUpdateExpensiveFlags();
    }
    catch (TimeoutException e) {
      throw new RuntimeException(e);
    }
    while (!((CodeInsightContextManagerImpl)CodeInsightContextManager.getInstance(myProject)).isContextInvalidationComplete()) {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      if (System.currentTimeMillis() > deadline) {
        throw new IllegalStateException("Timeout waiting for context invalidation");
      }
    }
  }

  private void doRunPasses(@NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer,
                           @NotNull TextEditor textEditor,
                           int @NotNull [] passesToIgnore,
                           boolean canChangeDocument,
                           @Nullable Runnable callbackWhileWaiting) {
    ThreadingAssertions.assertEventDispatchThread();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressAllDeprioritizationsDuringLongTestsExecutionIn(() -> {
      VirtualFile virtualFile = textEditor.getFile();
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      assert document != null : "Document is null for " + virtualFile + "; file type=" + virtualFile.getFileType();

      Editor editor = textEditor.getEditor();
      CodeInsightContext context = EditorContextManager.getEditorContext(editor, myProject);
      PsiFile psiFile = TextEditorBackgroundHighlighter.renewFile(myProject, document, context);
      FileASTNode fileNode = psiFile.getNode();
      HighlightingSession session = daemonCodeAnalyzer.queuePassesCreation(textEditor, virtualFile, passesToIgnore, new ConcurrentHashMap<>());
      if (session == null) {
        DaemonCodeAnalyzerImpl.LOG.error("Can't create session for " + textEditor + " (" + textEditor.getClass() + ")," +
          "; fileEditor.getBackgroundHighlighter()=" + textEditor.getBackgroundHighlighter() +
          "; getCachedFileToHighlight()=" + TextEditorBackgroundHighlighter.getCachedFileToHighlight(myProject, virtualFile, context) +
          "; getRawCachedFile()=" + ((PsiDocumentManagerEx)PsiDocumentManager.getInstance(myProject)).getRawCachedFile(virtualFile, context) +
          "; virtualFile=" + virtualFile + "(" + virtualFile.getClass() + ")");
        throw new ProcessCanceledException();
      }
      ProgressIndicator progress = session.getProgressIndicator();
      // there can be PCE in FJP during queuePassesCreation; "no PCE" guarantees that session is not null
      progress.checkCanceled();
      //noinspection IncorrectCancellationExceptionHandling
      try {
        long start = System.currentTimeMillis();
        waitInOtherThread(daemonCodeAnalyzer, 600_000, canChangeDocument, () -> {
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion();//auto-imports use non-blocking read actions
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
          progress.checkCanceled();
          if (callbackWhileWaiting != null) {
            callbackWhileWaiting.run();
          }
          // give other threads a chance to do smth useful
          if (System.currentTimeMillis() > start + 50) {
            TimeoutUtil.sleep(10);
          }
          dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
          progress.checkCanceled();
          return progress.isRunning();
        });
        if (progress.isRunning() && !progress.isCanceled()) {
          ForkJoinPool pool = ForkJoinPool.commonPool();
          throw new RuntimeException("Highlighting still running after " +
             (System.currentTimeMillis() - start) / 1000 + " seconds. Still submitted passes: " +
                                     daemonCodeAnalyzer.myPassExecutorService.getAllSubmittedPasses() +
                                     " ForkJoinPool.commonPool(): " + pool + "\n" +
                                     ", ForkJoinPool.commonPool() active thread count: " + pool.getActiveThreadCount() +
                                     ", ForkJoinPool.commonPool() has queued submissions: " + pool.hasQueuedSubmissions() + "\n" +
                                     ThreadDumper.dumpThreadsToString());
        }

        ((HighlightingSessionImpl)session).applyFileLevelHighlightsRequests();
        dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();//auto-imports use non-blocking read actions
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assert progress.isCanceled();
      }
      catch (Throwable e) {
        Throwable unwrapped = ExceptionUtilRt.unwrapException(e, ExecutionException.class);
        if (DaemonCodeAnalyzerImpl.LOG.isDebugEnabled()) {
          DaemonCodeAnalyzerImpl.LOG.debug("doRunPasses() thrown " + ExceptionUtil.getThrowableText(unwrapped));
        }
        if (unwrapped instanceof ProcessCanceledException unwrappedPCE) {
          Throwable savedException = ((DaemonProgressIndicator)progress).getCancellationTrace();
          if (savedException != null) {
            if (DaemonProgressIndicator.CANCEL_WAS_CALLED_REASON.equals(savedException.getMessage())) {
              throw unwrappedPCE;
            }
            unwrapped = savedException;
          }
        }
        if (progress.isCanceled() && progress.isRunning()) {
          unwrapped.addSuppressed(new RuntimeException("Daemon progress was canceled unexpectedly: " + progress));
          ExceptionUtil.rethrow(unwrapped);
        }
        if (!progress.isCanceled()) {
          ExceptionUtil.rethrow(unwrapped);
        }
      }
      finally {
        if (!progress.isCanceled()) {
          ((DaemonProgressIndicator)progress).cancel("Cancel after highlighting. threads:\n"+ThreadDumper.dumpThreadsToString());
        }
        waitForTermination();
      }
      Reference.reachabilityFence(psiFile); // PsiFile must be cached to start the highlighting
      Reference.reachabilityFence(fileNode); // perf: keep AST from gc
      return null;
    });
  }
  private void waitInOtherThread(@NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer,
                                 int millis,
                                 boolean canChangeDocument,
                                 @NotNull ThrowableComputable<Boolean, Throwable> runWhile) throws Throwable {
    ThreadingAssertions.assertEventDispatchThread();
    Disposable disposable = Disposer.newDisposable();
    AtomicBoolean assertOnModification = new AtomicBoolean();
    // last hope protection against PsiModificationTrackerImpl.incCounter() craziness (yes, Kotlin)
    myProject.getMessageBus().connect(disposable).subscribe(PsiModificationTracker.TOPIC,
                                                            () -> {
        if (assertOnModification.get()) {
          throw new IllegalStateException("You must not perform PSI modifications from inside highlighting");
        }
      });
    if (!canChangeDocument) {
      myProject.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          if (assertOnModification.get()) {
            throw new IllegalStateException("You must not cancel daemon inside highlighting test: "+reason);
          }
        }
      });
    }

    long deadline = System.currentTimeMillis() + millis;
    try {
      Future<?>
        passesFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> daemonCodeAnalyzer.myPassExecutorService.waitFor(System.currentTimeMillis() - deadline));
      do {
        assertOnModification.set(true);
        try {
          passesFuture.get(50, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ignored) {
        }
        finally {
          assertOnModification.set(false); //do not assert during dispatchAllEvents() because that's where all quick fixes happen
        }
      } while (runWhile.compute() && System.currentTimeMillis() < deadline);
      // it will wait for the async spawned processes
      Future<?> externalPassFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ExternalAnnotatorManager.getInstance().waitForAllExecuted(deadline-System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        return null;
      });
      externalPassFuture.get(1_000+deadline-System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException ignored) {
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void prepareForTest() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    waitForTermination();
    myDaemonCodeAnalyzer.clearReferences();
  }

  public void cleanupAfterTest() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    if (myProject.isOpen()) {
      prepareForTest();
    }
  }
  public void waitForTermination() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      // wait outside EDT to avoid stealing work from FJP
      while (!myDaemonCodeAnalyzer.myPassExecutorService.waitFor(50)) {
        Thread.yield();
      }
    });

    waitWhilePumping(future);
  }

  public static void waitWhilePumping(@NotNull Future<?> future) {
    do {
      try {
        future.get(10, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignored) {
      }
      catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (EDT.isCurrentThreadEdt()) {
        dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      }
      else {
        UIUtil.pump();
      }
    } while (!future.isDone());
  }

  public void waitForUpdateFileStatusBackgroundQueueInTests() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDaemonCodeAnalyzer.myListeners.waitForUpdateFileStatusQueue();
  }

  public void waitUpdateExpensiveFlags() throws TimeoutException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDaemonCodeAnalyzer.myListeners.waitUpdateExpensiveFlags(1, TimeUnit.MINUTES);
  }

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToFinish(@NotNull PsiFile psiFile) {
    return waitForDaemonToFinish(psiFile, () -> {
      TimeoutUtil.sleep(10);
    });
  }

  public static final int WAIT_DAEMON_FOR_FINISH_TIMEOUT_MS = 60_000;

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToFinish(@NotNull PsiFile psiFile, @NotNull Runnable callbackWhileWaiting) {
    ThreadingAssertions.assertEventDispatchThread();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    assert document != null;
    long start = System.currentTimeMillis();
    long deadline = start + WAIT_DAEMON_FOR_FINISH_TIMEOUT_MS;
    assert myDaemonCodeAnalyzer.isUpdateByTimerEnabled() : "codeAnalyzer.isUpdateByTimerEnabled()=false so waitForDaemonToFinish() will never finish";
    Collection<? extends DaemonProgressIndicator> progresses = waitForDaemonToStart(psiFile, deadline - System.currentTimeMillis());
    Disposable disposable = Disposer.newDisposable();
    Semaphore listenersCalled = new Semaphore(1);
    try {
      myProject.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
        @Override
        public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
          listenersCalled.up();
          PassExecutorService.LOG.trace("waitForDaemonToFinish.daemonFinished");
        }

        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          listenersCalled.up();
          PassExecutorService.LOG.trace("waitForDaemonToFinish.daemonCancelEventOccurred: " + reason);
        }
      });
      if (!daemonIsWorkingOrPending(document)) {
        // listener installed too late, will never fire
        listenersCalled.up();
      }

      long untilDeadline;
      do {
        untilDeadline = deadline - System.currentTimeMillis();
        if (untilDeadline < 0) {
          String dump = ThreadDumper.dumpThreadsToString();
          MarkupModelImpl markupModel = (MarkupModelImpl)DocumentMarkupModel.forDocument(document, myProject, true);
          List<RangeHighlighter> highlighters = List.of(markupModel.getAllHighlighters());
          AssertionError e = new AssertionError("Too long waiting for daemon to finish (" + (System.currentTimeMillis() - start) + "ms already). " +
             "file status map:" + myDaemonCodeAnalyzer.getFileStatusMap() +
             "\nprogress: "+progresses+
             "\ndaemonIsWorkingOrPending(document)="+daemonIsWorkingOrPending(document)+
             "\nPsiDocumentManager.getInstance(myProject).isCommitted(document)="+PsiDocumentManager.getInstance(myProject).isCommitted(document)+
             "\ncurrent highlights:("+highlighters.size()+")\n" + StringUtil.join(ContainerUtil.getFirstItems(highlighters, 100), Object::toString, "\n")+
             "\nthread dump:"+ dump);
          DaemonCodeAnalyzerImpl.LOG.info(e);
          throw e;
        }
        callbackWhileWaiting.run();
        waitForUpdateFileStatusBackgroundQueueInTests(); // commit can happen here any moment, so make sure PSI changes are handled
        dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
        for (DaemonProgressIndicator indicator : progresses) {
          Throwable trace = indicator.getCancellationTrace();
          if (trace != null && !(trace instanceof ProcessCanceledException) && !DaemonProgressIndicator.CANCEL_WAS_CALLED_REASON.equals(trace.getMessage())) {
            DaemonCodeAnalyzerImpl.LOG.debug("waitForDaemonToFinish canceled: exception was thrown: " + indicator + "; " + ExceptionUtil.getThrowableText(trace));
            ExceptionUtil.rethrow(trace);
          }
          if (indicator.isCanceled() && indicator.isRunning()) {
            // wait for daemon listeners to be called,
            // since many tests do "waitForFinish(); checkSomeState();", and the state is changed in DaemonListener
            if (!listenersCalled.waitFor(untilDeadline)) {
              throw new IncorrectOperationException();
            }
            DaemonCodeAnalyzerImpl.LOG.debug("waitForDaemonToFinish canceled: indicator was canceled: "+indicator
                                             +"; "+(trace == null ? indicator.getTraceableDisposableStackTrace() : ExceptionUtil.getThrowableText(trace)));
            indicator.checkCanceled(); // canceled in the middle, throw PCE
          }
        }
      } while (daemonIsWorkingOrPending(document));
      for (DaemonProgressIndicator indicator : progresses) {
        Throwable trace = indicator.getCancellationTrace();
        if (trace != null && !(trace instanceof ProcessCanceledException) && !DaemonProgressIndicator.CANCEL_WAS_CALLED_REASON.equals(trace.getMessage())) {
          ExceptionUtil.rethrow(trace);
        }
      }
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      DaemonCodeAnalyzerImpl.LOG.debug("waitForDaemonToFinish("+document+") finished: "+progresses+"; fileStatusMap:"+ myDaemonCodeAnalyzer.getFileStatusMap()+"\n"+ContainerUtil.mapNotNull(progresses, indicator -> {
        if (!indicator.isRunning()) {
          return null;
        }
        Throwable trace = indicator.getCancellationTrace();
        return (trace == null ? indicator.getTraceableDisposableStackTrace() : ExceptionUtil.getThrowableText(trace));
      }));
      // wait for daemon listeners to be called,
      // since many tests do "waitForFinish(); checkSomeState();", and the state is changed in DaemonListener
      if (!listenersCalled.waitFor(untilDeadline)) {
        throw new IncorrectOperationException();
      }
      return progresses;
    }
    finally {
      Disposer.dispose(disposable);
      Reference.reachabilityFence(document);
    }
  }

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToStart(@NotNull PsiFile psiFile, long timeoutMs) {
    ThreadingAssertions.assertEventDispatchThread();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    PassExecutorService.LOG.trace("waitForDaemonToStart start");
    Disposable disposable = Disposer.newDisposable();
    AtomicBoolean listenersCalled = new AtomicBoolean();
    myProject.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished(@NotNull Collection<? extends FileEditor> fileEditors) {
        listenersCalled.set(true);
        PassExecutorService.LOG.trace("waitForDaemonToStart.daemonFinished");
      }

      @Override
      public void daemonCancelEventOccurred(@NotNull String reason) {
        listenersCalled.set(true);
        PassExecutorService.LOG.trace("waitForDaemonToStart.daemonCancelEventOccurred: " + reason);
      }
    });
    try {
    waitForAllThingsBeforeDaemonStart(mustWaitForSmartModeByDefault, timeoutMs);
    long deadline = System.currentTimeMillis() + timeoutMs;
    assert myDaemonCodeAnalyzer.isUpdateByTimerEnabled() : "codeAnalyzer.isUpdateByTimerEnabled()=false so waitForDaemonToStart() will never finish";
    while (!myDaemonCodeAnalyzer.isAllAnalysisFinished(psiFile) && !listenersCalled.get()) {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      List<DaemonProgressIndicator> progresses = ContainerUtil.filter(myDaemonCodeAnalyzer.getUpdateProgress().values(), i -> !i.isCanceled());
      if (!progresses.isEmpty()) {
        DaemonCodeAnalyzerImpl.LOG.debug("waitForDaemonToStart("+document+") finished successfully: "+progresses+"; fileStatusMap:"+ myDaemonCodeAnalyzer.getFileStatusMap());
        return progresses;
      }

      if (System.currentTimeMillis() > deadline) {
        AssertionError e = new AssertionError("Too long waiting for daemon to start (" + (System.currentTimeMillis() - deadline + timeoutMs) + "ms) " +
                                              "daemonIsWorkingOrPending=" + daemonIsWorkingOrPending(document) +
                                              "\n allFinished=" + myDaemonCodeAnalyzer.isAllAnalysisFinished(psiFile) +
                                              "\n; filestatusmap: " +
                                              myDaemonCodeAnalyzer.getFileStatusMap() +
                                              "\n; thread dump:\n------" + ThreadDumper.dumpThreadsToString() + "\n======");
        DaemonCodeAnalyzerImpl.LOG.info(e);
        throw e;
      }
    }
    } finally {
      Disposer.dispose(disposable);
    }
    DaemonCodeAnalyzerImpl.LOG.debug("waitForDaemonToStart("+document+") finished because daemon completed: progress="+myDaemonCodeAnalyzer.getUpdateProgress()+
                                     "\n; fileStatusMap:"+ myDaemonCodeAnalyzer.getFileStatusMap()+
                                     "\n; finished:"+myDaemonCodeAnalyzer.isAllAnalysisFinished(psiFile)+
                                     "\n; running:"+myDaemonCodeAnalyzer.isRunningOrPending()+
                                     "\n; listenersCalled:"+listenersCalled
    );
    return myDaemonCodeAnalyzer.getUpdateProgress().values();
  }

  private boolean daemonIsWorkingOrPending(@NotNull Document document) {
    return myDaemonCodeAnalyzer.isRunningOrPending() || PsiDocumentManager.getInstance(myProject).isUncommited(document);
  }

  @RequiresEdt
  public @NotNull List<HighlightInfo> waitHighlighting(@NotNull PsiFile psiFile, @NotNull HighlightSeverity minSeverity) {
    waitForDaemonToFinish(psiFile);
    Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    return DaemonCodeAnalyzerImpl.getHighlights(document, minSeverity, myProject);
  }

  private static void dispatchAllInvocationEventsInIdeEventQueueReleasingWIL() {
    ThreadingAssertions.assertEventDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    ThreadContext.resetThreadContext(() -> {
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
        // due to non-blocking acquisition of write-intent, `NonBlockingFlushQueue` can appear in the state
        // where it has stuck WI runnables. This method is called to ensure that _all_ runnables are dispatched,
        // so we also want to wait for WI runnables here
        AtomicBoolean canary = new AtomicBoolean(false);
        ApplicationManager.getApplication().invokeLater(() -> canary.set(true), ModalityState.any());
        while (true) {
          AWTEvent event = eventQueue.peekEvent();
          if (event == null && canary.get()) break;
          event = eventQueue.getNextEvent();
          if (event instanceof InvocationEvent) {
            eventQueue.dispatchEvent(event);
          }
        }
      });
      return null;
    });
  }

  public static <T extends Throwable> void runWithReparseDelay(int reparseDelayMs, @NotNull ThrowableRunnable<T> task) throws T {
    DaemonCodeAnalyzerSettings settings = DaemonCodeAnalyzerSettings.getInstance();
    int oldDelay = settings.getAutoReparseDelay();
    settings.setAutoReparseDelay(reparseDelayMs);
    try {
      task.run();
    }
    finally {
      settings.setAutoReparseDelay(oldDelay);
    }
  }

  public void mustWaitForSmartModeByDefault(boolean value) {
    mustWaitForSmartModeByDefault = value;
  }

  public @NotNull List<HighlightInfo> waitHighlightingSurviveCancellations(@NotNull PsiFile psiFile, @NotNull HighlightSeverity minSeverity) {
    while (true) {
      try {
        return waitHighlighting(psiFile, minSeverity);
      }
      catch (ProcessCanceledException e) {
        // document modifications are expected here, e.g. when auto-import adds an import and cancels the current highlighting
        dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      }
    }
  }

  @VisibleForTesting
  public boolean isMarkedExcluded(@NotNull Document document) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myDaemonCodeAnalyzer.myListeners.isMarkedExcluded(document);
  }
  @VisibleForTesting
  public boolean isMarkedCodeFragment(@NotNull Document document) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myDaemonCodeAnalyzer.myListeners.isMarkedCodeFragment(document);
  }
}
