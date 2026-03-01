// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
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
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.psi.PsiConsistencyAssertions;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
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

  public TestDaemonCodeAnalyzerImpl(@NotNull Project project) {
    myProject = project;
    myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    assert !myDaemonCodeAnalyzer.myDisposed;
  }

  /**
   * do not run in production since it differs slightly from the {@link DaemonCodeAnalyzerImpl#runUpdate()}
   */
  @TestOnly
  @ApiStatus.Internal
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
    boolean isDebugMode = !ApplicationManagerEx.isInStressTest();
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    do {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      // refresh will fire write actions interfering with highlighting
      // heavy ops are bad, but VFS refresh is ok
    }
    while (RefreshQueueImpl.isRefreshInProgress() || DaemonCodeAnalyzerImpl.heavyProcessIsRunning());
    long dStart = System.currentTimeMillis();
    while (mustWaitForSmartMode && DumbService.getInstance(myProject).isDumb()) {
      if (System.currentTimeMillis() > dStart + 100_000) {
        throw new IllegalStateException("Timeout waiting for smart mode. If you absolutely want to be dumb, please use DaemonCodeAnalyzerImpl.mustWaitForSmartMode(false).");
      }
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    }
    ((GistManagerImpl)GistManager.getInstance()).clearQueueInTests();
    dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // wait for async editor loading

    myDaemonCodeAnalyzer.clearReferences();

    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myDaemonCodeAnalyzer.myPassExecutorService.cancelAll(false, "DaemonCodeAnalyzerImpl.runPasses");

    CodeInsightContext context = CodeInsightContextUtil.getCodeInsightContext(psiFile);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    PsiConsistencyAssertions.assertNoFileTextMismatch(psiFile, editor.getDocument(), null);
    // update the file status map before prohibiting its modifications
    waitForUpdateFileStatusBackgroundQueueInTests();
    FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();
    fileStatusMap.runAllowingDirt(canChangeDocument, () -> {
      for (int ignoreId : passesToIgnore) {
        fileStatusMap.markFileUpToDate(document, context, ignoreId, null);
      }
      ThrowableRunnable<Exception> doRunPasses = () -> doRunPasses(myDaemonCodeAnalyzer, textEditor, passesToIgnore, canChangeDocument, callbackWhileWaiting);
      if (isDebugMode) {
        DaemonProgressIndicator.runInDebugMode(doRunPasses);
      }
      else {
        doRunPasses.run();
      }
    });
  }

  @TestOnly
  private void doRunPasses(@NotNull DaemonCodeAnalyzerImpl daemonCodeAnalyzer,
                           @NotNull TextEditor textEditor,
                           int @NotNull [] passesToIgnore,
                           boolean canChangeDocument,
                           @Nullable Runnable callbackWhileWaiting) throws Exception {
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
                                         " fileEditor.getBackgroundHighlighter()=" + textEditor.getBackgroundHighlighter() +
                                         "; virtualFile=" + virtualFile);
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
  @TestOnly
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

  @TestOnly
  public void prepareForTest() throws InterruptedException, ExecutionException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDaemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    waitForTermination();
    myDaemonCodeAnalyzer.clearReferences();
  }

  @TestOnly
  public void cleanupAfterTest() throws InterruptedException, ExecutionException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    if (myProject.isOpen()) {
      prepareForTest();
    }
  }
  @TestOnly
  public void waitForTermination() throws InterruptedException, ExecutionException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    Future<?> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
      // wait outside EDT to avoid stealing work from FJP
      myDaemonCodeAnalyzer.myPassExecutorService.cancelAll(true, "DaemonCodeAnalyzerImpl.waitForTermination");
    });
    waitWhilePumping(future);
  }

  public static void waitWhilePumping(@NotNull Future<?> future) throws InterruptedException, ExecutionException {
    do {
      try {
        future.get(10, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignored) {
      }
      if (EDT.isCurrentThreadEdt()) {
        dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      }
      else {
        UIUtil.pump();
      }
    } while (!future.isDone());
  }

  @TestOnly
  public void waitForUpdateFileStatusBackgroundQueueInTests() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDaemonCodeAnalyzer.myListeners.waitForUpdateFileStatusQueue();
  }

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToFinish(@NotNull Project project, @NotNull Document document) {
    return waitForDaemonToFinish(project, document, () -> {});
  }

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToFinish(@NotNull Project project, @NotNull Document document, @NotNull Runnable callbackWhileWaiting) {
    ThreadingAssertions.assertEventDispatchThread();
    long start = System.currentTimeMillis();
    long deadline = start + 60_000;
    waitForUpdateFileStatusBackgroundQueueInTests();
    Collection<? extends DaemonProgressIndicator> progresses = waitForDaemonToStart(project, document, 60_000);
    assert myDaemonCodeAnalyzer.isUpdateByTimerEnabled() : "codeAnalyzer.isUpdateByTimerEnabled()=false so waitForDaemonToFinish() will never finish";
    do {
      if (System.currentTimeMillis() > deadline) {
        String dump = ThreadDumper.dumpThreadsToString();
        throw new AssertionError("Too long waiting for daemon to finish (" + (System.currentTimeMillis() - start) + "ms already). " +
           "file status map:" + myDaemonCodeAnalyzer.getFileStatusMap() + "\n" +
           "current highlights:" + StringUtil.join(DocumentMarkupModel.forDocument(document, project, true).getAllHighlighters(), Object::toString, "\n")+
           "thread dump:"+ dump);
      }
      callbackWhileWaiting.run();
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      for (DaemonProgressIndicator indicator : progresses) {
        Throwable trace = indicator.getCancellationTrace();
        if (trace != null && !(trace instanceof ProcessCanceledException) && !DaemonProgressIndicator.CANCEL_WAS_CALLED_REASON.equals(trace.getMessage())) {
          ExceptionUtil.rethrow(trace);
        }
        if (indicator.isCanceled() && indicator.isRunning()) {
          indicator.checkCanceled(); // canceled in the middle, throw PCE
        }
      }
    } while (daemonIsWorkingOrPending(project, document));
    dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
    return progresses;
  }

  @RequiresEdt
  public @NotNull Collection<? extends DaemonProgressIndicator> waitForDaemonToStart(@NotNull Project project, @NotNull Document document, long timeoutMs) {
    waitForUpdateFileStatusBackgroundQueueInTests();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    long deadline = System.currentTimeMillis() + timeoutMs;
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    assert codeAnalyzer.isUpdateByTimerEnabled() : "codeAnalyzer.isUpdateByTimerEnabled()=false so waitForDaemonToStart() will never finish";
    while (!codeAnalyzer.isAllAnalysisFinished(psiFile)) {
      dispatchAllInvocationEventsInIdeEventQueueReleasingWIL();
      List<DaemonProgressIndicator> progresses = ContainerUtil.filter(codeAnalyzer.getUpdateProgress().values(), i -> !i.isCanceled());
      if (!progresses.isEmpty()) {
        return progresses;
      }
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Too long waiting for daemon to start (" + (System.currentTimeMillis() - deadline + timeoutMs) + "ms) " +
                      "daemonIsWorkingOrPending=" + daemonIsWorkingOrPending(project, document) +
                      "; allFinished=" + codeAnalyzer.isAllAnalysisFinished(psiFile) + ": " + codeAnalyzer.getFileStatusMap() +
                      "; thread dump:\n------" + ThreadDumper.dumpThreadsToString() + "\n======");
      }
    }
    return List.of();
  }

  public boolean daemonIsWorkingOrPending(@NotNull Project project, @NotNull Document document) {
    return myDaemonCodeAnalyzer.isRunningOrPending() || PsiDocumentManager.getInstance(project).isUncommited(document);
  }

  @RequiresEdt
  public @NotNull List<HighlightInfo> waitHighlighting(@NotNull Project project, @NotNull Document document, @NotNull HighlightSeverity minSeverity) {
    waitForDaemonToFinish(project, document);
    return DaemonCodeAnalyzerImpl.getHighlights(document, minSeverity, project);
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
}
