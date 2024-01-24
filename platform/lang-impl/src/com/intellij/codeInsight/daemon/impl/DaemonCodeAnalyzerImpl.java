// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixUpdater;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.JobLauncher;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.notebook.editor.BackedVirtualFileProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.EDT;
import io.opentelemetry.context.Context;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@State(name = "DaemonCodeAnalyzer", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzerEx
  implements PersistentStateComponent<Element>, Disposable, DaemonCodeAnalysisStatus {

  private static final Logger LOG = Logger.getInstance(DaemonCodeAnalyzerImpl.class);

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");
  private static final @NotNull Key<Boolean> COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY = Key.create("COMPLETE_ESSENTIAL_HIGHLIGHTING");
  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  private final DaemonListeners myListeners;
  private PsiDocumentManager psiDocumentManager;
  private FileEditorManager fileEditorManager;
  private final Map<FileEditor, DaemonProgressIndicator> myUpdateProgress = new ConcurrentHashMap<>();

  private final UpdateRunnable myUpdateRunnable;
  private volatile @NotNull Future<?> myUpdateRunnableFuture = CompletableFuture.completedFuture(null);
  private boolean myUpdateByTimerEnabled = true; // guarded by this
  private final Collection<VirtualFile> myDisabledHintsFiles = new HashSet<>();
  private final Collection<VirtualFile> myDisabledHighlightingFiles = new HashSet<>();

  private final FileStatusMap myFileStatusMap;
  private DaemonCodeAnalyzerSettings myLastSettings;

  private volatile boolean myDisposed;     // the only possible transition: false -> true

  private static final @NonNls String DISABLE_HINTS_TAG = "disable_hints";
  private static final @NonNls String FILE_TAG = "file";
  private static final @NonNls String URL_ATT = "url";
  private final PassExecutorService myPassExecutorService;
  /**
   * Timestamp of {@link #myUpdateRunnable} which it's needed to start (in System.nanoTime() sense)
   * May be later than the actual ScheduledFuture sitting in the {@link EdtExecutorService} queue.
   * When it happens that the future has started sooner than this stamp, it will re-schedule itself for later.
   */
  private long myScheduledUpdateTimestamp; // guarded by this
  private volatile boolean completeEssentialHighlightingRequested;
  private final AtomicInteger daemonCancelEventCount = new AtomicInteger();
  private final DaemonListener myDaemonListenerPublisher;

  public DaemonCodeAnalyzerImpl(@NotNull Project project) {
    // DependencyValidationManagerImpl adds scope listener, so we need to force service creation
    DependencyValidationManager.getInstance(project);

    myProject = project;
    mySettings = DaemonCodeAnalyzerSettings.getInstance();
    myLastSettings = ((DaemonCodeAnalyzerSettingsImpl)mySettings).clone();

    myFileStatusMap = new FileStatusMap(project);
    myPassExecutorService = new PassExecutorService(project);
    Disposer.register(this, myPassExecutorService);
    Disposer.register(this, myFileStatusMap);
    //noinspection TestOnlyProblems
    DaemonProgressIndicator.setDebug(LOG.isDebugEnabled());

    Disposer.register(this, new StatusBarUpdater(project));

    myDisposed = false;
    myFileStatusMap.markAllFilesDirty("DaemonCodeAnalyzer init");
    myUpdateRunnable = new UpdateRunnable(project);
    Disposer.register(this, () -> {
      assert !myDisposed : "Double dispose";
      myUpdateRunnable.clearFieldsOnDispose();

      stopProcess(false, "Dispose "+project);

      myDisposed = true;
      myLastSettings = null;
    });
    myDaemonListenerPublisher = project.getMessageBus().syncPublisher(DAEMON_EVENT_TOPIC);
    myListeners = new DaemonListeners(project, this);
    Disposer.register(this, myListeners);
  }

  private FileEditorManager getFileEditorManager() {
    FileEditorManager result = fileEditorManager;
    if (result == null) {
      result = FileEditorManager.getInstance(myProject);
      fileEditorManager = result;
    }
    return result;
  }

  private PsiDocumentManager getPsiDocumentManager() {
    PsiDocumentManager result = psiDocumentManager;
    if (result == null) {
      result = PsiDocumentManager.getInstance(myProject);
      psiDocumentManager = result;
    }
    return result;
  }

  @Override
  public synchronized void dispose() {
    clearReferences();
  }

  private synchronized void clearReferences() {
    myUpdateProgress.values().forEach(ProgressIndicator::cancel);
    // avoid leak of highlight session via user data
    myUpdateProgress.clear();
    myUpdateRunnableFuture.cancel(true);
  }

  synchronized void clearProgressIndicator() {
    myUpdateProgress.values().forEach(HighlightingSessionImpl::clearProgressIndicator);
  }

  @TestOnly
  public static @NotNull List<HighlightInfo> getHighlights(@NotNull Document document,
                                                           @Nullable HighlightSeverity minSeverity,
                                                           @NotNull Project project) {
    List<HighlightInfo> infos = new ArrayList<>();
    processHighlights(document, project, minSeverity, 0, document.getTextLength(),
                      Processors.cancelableCollectProcessor(infos));
    return infos;
  }

  @TestOnly
  public @NotNull List<HighlightInfo> getFileLevelHighlights(@NotNull Project project, @NotNull PsiFile file) {
    assertMyFile(file.getProject(), file);
    assertMyFile(project, file);
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return Arrays.stream(getFileEditorManager().getAllEditors(vFile))
      .map(fileEditor -> fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS))
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private void assertMyFile(@NotNull Project project, @NotNull PsiFile file) {
    if (project != myProject) {
      throw new IllegalStateException("my project is " + myProject + " but I was called with " + project);
    }
    if (file.getProject() != myProject) {
      throw new IllegalStateException("my project is " + myProject + " but I was called with file " + file + " from " + file.getProject());
    }
  }

  @Override
  public void cleanFileLevelHighlights(int group, @NotNull PsiFile psiFile) {
    ThreadingAssertions.assertEventDispatchThread();
    assertMyFile(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditors(vFile)) {
      cleanFileLevelHighlights(fileEditor, group);
    }
  }

  public boolean hasFileLevelHighlights(int group, @NotNull PsiFile psiFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    assertMyFile(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditors(vFile)) {
      List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos != null && !infos.isEmpty()) {
        for (HighlightInfo info : infos) {
          if (info.getGroup() == group || group == ANY_GROUP) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static final int ANY_GROUP = -409423948;
  void cleanAllFileLevelHighlights() {
    ThreadingAssertions.assertEventDispatchThread();
    for (FileEditor fileEditor : getFileEditorManager().getAllEditors()) {
      cleanFileLevelHighlights(fileEditor, ANY_GROUP);
    }
  }

  private void cleanFileLevelHighlights(@NotNull FileEditor fileEditor, int group) {
    ThreadingAssertions.assertEventDispatchThread();
    List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
    if (infos == null || infos.isEmpty()) {
      return;
    }

    List<HighlightInfo> infosToRemove = new ArrayList<>(infos.size());
    for (HighlightInfo info : infos) {
      if (info.getGroup() == group || group == ANY_GROUP) {
        JComponent component = info.getFileLevelComponent(fileEditor);
        if (component != null) {
          getFileEditorManager().removeTopComponent(fileEditor, component);
          info.removeFileLeverComponent(fileEditor);
        }
        RangeHighlighterEx highlighter = info.highlighter;
        if (highlighter != null) {
          highlighter.dispose();
        }
        infosToRemove.add(info);
      }
    }
    infos.removeAll(infosToRemove);
    if (LOG.isDebugEnabled()) {
      LOG.debug("cleanFileLevelHighlights group:" +group+ "; infosToRemove:" + infosToRemove);
    }
  }

  @Override
  void removeFileLevelHighlight(@NotNull PsiFile psiFile, @NotNull HighlightInfo info) {
    ThreadingAssertions.assertEventDispatchThread();
    assertMyFile(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditors(vFile)) {
      List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos != null) {
        infos.remove(info);
        JComponent component = info.getFileLevelComponent(fileEditor);
        if (component != null) {
          getFileEditorManager().removeTopComponent(fileEditor, component);
          info.removeFileLeverComponent(fileEditor);
        }
        RangeHighlighterEx highlighter = info.highlighter;
        if (highlighter != null) {
          highlighter.dispose();
        }
      }
    }
  }

  @Override
  public void addFileLevelHighlight(int group, @NotNull HighlightInfo info, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse) {
    ThreadingAssertions.assertEventDispatchThread();
    assertMyFile(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    FileEditorManager fileEditorManager = getFileEditorManager();
    for (FileEditor fileEditor : fileEditorManager.getAllEditors(vFile)) {
      if (fileEditor instanceof TextEditor textEditor) {
        List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> actionRanges = new ArrayList<>();
        info.findRegisteredQuickFix((descriptor, range) -> {
          actionRanges.add(Pair.create(descriptor, range));
          return null;
        });
        List<HighlightInfo> fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
        if (fileLevelInfos == null) {
          fileLevelInfos = ContainerUtil.createConcurrentList(); // must be able to iterate in hasFileLevelHighlights() and concurrently modify in addFileLevelHighlight()
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos);
        }
        if (!ContainerUtil.exists(fileLevelInfos, existing->existing.equalsByActualOffset(info))) {
          Document document = textEditor.getEditor().getDocument();
          MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
          RangeHighlighter highlighter = toReuse == null ? markupModel.addRangeHighlighter(0, document.getTextLength(), ANY_GROUP, null, HighlighterTargetArea.EXACT_RANGE) : toReuse;
          highlighter.setGreedyToLeft(true);
          highlighter.setGreedyToRight(true);
          highlighter.setErrorStripeTooltip(info);
          // for the condition `existing.equalsByActualOffset(info)` above work correctly,
          // create a fake whole-file highlighter which will track the document size changes
          // and which will make possible to calculate correct `info.getActualEndOffset()`
          info.setHighlighter((RangeHighlighterEx)highlighter);
          info.setGroup(group);
          fileLevelInfos.add(info);
          FileLevelIntentionComponent component = new FileLevelIntentionComponent(info.getDescription(), info.getSeverity(),
                                                                                  info.getGutterIconRenderer(), actionRanges,
                                                                                  psiFile, ((TextEditor)fileEditor).getEditor(), info.getToolTip());
          fileEditorManager.addTopComponent(fileEditor, component);
          info.addFileLevelComponent(fileEditor, component);
          if (LOG.isDebugEnabled()) {
            LOG.debug("addFileLevelHighlight [" + info + "]: fileLevelInfos:" + fileLevelInfos);
          }
        }
      }
    }
  }

  @Override
  boolean cutOperationJustHappened() {
    return myListeners.cutOperationJustHappened;
  }

  @Override
  boolean isEscapeJustPressed() {
    return myListeners.isEscapeJustPressed();
  }

  @Override
  public @NotNull List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile, @NotNull Document document, @NotNull ProgressIndicator progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    assertMyFile(psiFile.getProject(), psiFile);

    GlobalInspectionContextBase.assertUnderDaemonProgress();
    // clear status maps to run passes from scratch so that refCountHolder won't conflict and try to restart itself on partially filled maps
    myFileStatusMap.markAllFilesDirty("prepare to run main passes");
    stopProcess(false, "disable background daemon");
    myPassExecutorService.cancelAll(true, "DaemonCodeAnalyzerImpl.runMainPasses");

    List<HighlightInfo> result;
    try {
      result = new ArrayList<>();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && !virtualFile.getFileType().isBinary()) {
        List<TextEditorHighlightingPass> passes = DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
          List<TextEditorHighlightingPass> mainPasses = TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject)
            .instantiateMainPasses(psiFile, document, HighlightInfoProcessor.getEmpty());

          JobLauncher.getInstance().invokeConcurrentlyUnderProgress(mainPasses, progress, pass -> ReadAction.compute(() -> {
            pass.doCollectInformation(progress);
            return true;
          }));
          return mainPasses;
        });

        try {
          for (TextEditorHighlightingPass pass : passes) {
            result.addAll(pass.getInfos());
          }
        }
        catch (ProcessCanceledException e) {
          LOG.debug("Canceled: " + progress);
          throw e;
        }
      }
    }
    finally {
      stopProcess(true, "re-enable background daemon after main passes run");
    }

    return result;
  }

  private volatile boolean mustWaitForSmartMode = true;
  @TestOnly
  public void mustWaitForSmartMode(boolean mustWait, @NotNull Disposable parent) {
    boolean old = mustWaitForSmartMode;
    mustWaitForSmartMode = mustWait;
    Disposer.register(parent, () -> mustWaitForSmartMode = old);
  }

  @TestOnly
  public void runPasses(@NotNull PsiFile file,
                        @NotNull Document document,
                        @NotNull TextEditor textEditor,
                        int @NotNull [] passesToIgnore,
                        boolean canChangeDocument,
                        @Nullable Runnable callbackWhileWaiting) throws ProcessCanceledException {
    ThreadingAssertions.assertEventDispatchThread();
    assert !myDisposed;
    assertMyFile(file.getProject(), file);
    assert textEditor.getEditor().getDocument() == document : "Expected document "+document+" but one of the passed TextEditors points to a different document: "+textEditor.getEditor().getDocument();
    Document associatedDocument = PsiDocumentManager.getInstance(myProject).getDocument(file);
    assert associatedDocument == document : "Expected document " + document + " but the passed PsiFile points to a different document: " + associatedDocument;
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      throw new IllegalStateException("Must not start highlighting from within write action, or deadlock is imminent");
    }
    DaemonProgressIndicator.setDebug(!ApplicationManagerEx.isInStressTest());
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    do {
      EDT.dispatchAllInvocationEvents();
      // refresh will fire write actions interfering with highlighting
      // heavy ops are bad, but VFS refresh is ok
    }
    while (RefreshQueueImpl.isRefreshInProgress() || heavyProcessIsRunning());
    long dStart = System.currentTimeMillis();
    while (mustWaitForSmartMode && DumbService.getInstance(myProject).isDumb()) {
      if (System.currentTimeMillis() > dStart + 100_000) {
        throw new IllegalStateException("Timeout waiting for smart mode. If you absolutely want to be dumb, please use DaemonCodeAnalyzerImpl.mustWaitForSmartMode(false).");
      }
      EDT.dispatchAllInvocationEvents();
    }
    ((GistManagerImpl)GistManager.getInstance()).clearQueueInTests();
    EDT.dispatchAllInvocationEvents();

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // wait for async editor loading

    myUpdateRunnableFuture.cancel(false);

    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myPassExecutorService.cancelAll(false, "DaemonCodeAnalyzerImpl.runPasses");

    FileStatusMap fileStatusMap = getFileStatusMap();
    boolean old = fileStatusMap.allowDirt(canChangeDocument);
    for (int ignoreId : passesToIgnore) {
      fileStatusMap.markFileUpToDate(document, ignoreId);
    }

    try {
      doRunPasses(textEditor, passesToIgnore, canChangeDocument, callbackWhileWaiting);
    }
    finally {
      DaemonProgressIndicator.setDebug(false);
      fileStatusMap.allowDirt(old);
    }
  }

  @TestOnly
  private void doRunPasses(@NotNull TextEditor textEditor,
                           int @NotNull [] passesToIgnore,
                           boolean canChangeDocument,
                           @Nullable Runnable callbackWhileWaiting) {
    ((CoreProgressManager)ProgressManager.getInstance()).suppressAllDeprioritizationsDuringLongTestsExecutionIn(() -> {
      VirtualFile virtualFile = textEditor.getFile();
      PsiFile psiFile = PsiManagerEx.getInstanceEx(myProject).findFile(virtualFile); // findCachedFile doesn't work with the temp file system in tests
      psiFile = psiFile instanceof PsiCompiledFile compiled ? compiled.getDecompiledPsiFile() : psiFile;
      LOG.assertTrue(psiFile != null, "PsiFile not found for " + virtualFile);
      HighlightingSession session = queuePassesCreation(textEditor, virtualFile, psiFile, passesToIgnore);
      if (session == null) {
        LOG.error("Can't create session for " + textEditor + " (" + textEditor.getClass() + ")," +
                  " fileEditor.getBackgroundHighlighter()=" + textEditor.getBackgroundHighlighter() +
                  "; virtualFile=" + virtualFile +
                  "; psiFile=" + psiFile);
        throw new ProcessCanceledException();
      }
      ProgressIndicator progress = session.getProgressIndicator();
      // there can be PCE in FJP during queuePassesCreation
      // no PCE guarantees session is not null
      progress.checkCanceled();
      try {
        long start = System.currentTimeMillis();
        waitInOtherThread(600_000, canChangeDocument, () -> {
          progress.checkCanceled();
          if (callbackWhileWaiting != null) {
            callbackWhileWaiting.run();
          }
          // give other threads a chance to do smth useful
          if (System.currentTimeMillis() > start + 50) {
            TimeoutUtil.sleep(10);
          }
          EDT.dispatchAllInvocationEvents();
          Throwable savedException = PassExecutorService.getSavedException((DaemonProgressIndicator)progress);
          if (savedException != null) throw savedException;
          return progress.isRunning();
        });
        if (progress.isRunning() && !progress.isCanceled()) {
          throw new RuntimeException("Highlighting still running after " +
             (System.currentTimeMillis() - start) / 1000 + " seconds. Still submitted passes: " +
             myPassExecutorService.getAllSubmittedPasses() +
             " ForkJoinPool.commonPool(): " + ForkJoinPool.commonPool() + "\n" +
             ", ForkJoinPool.commonPool() active thread count: " + ForkJoinPool.commonPool().getActiveThreadCount() +
             ", ForkJoinPool.commonPool() has queued submissions: " + ForkJoinPool.commonPool().hasQueuedSubmissions() + "\n" +
             ThreadDumper.dumpThreadsToString());
        }

        ((HighlightingSessionImpl)session).applyFileLevelHighlightsRequests();
        EDT.dispatchAllInvocationEvents();
        EDT.dispatchAllInvocationEvents();
        assert progress.isCanceled();
      }
      catch (Throwable e) {
        Throwable unwrapped = ExceptionUtilRt.unwrapException(e, ExecutionException.class);
        if (progress.isCanceled() && progress.isRunning()) {
          unwrapped.addSuppressed(new RuntimeException("Daemon progress was canceled unexpectedly: " + progress));
        }
        ExceptionUtil.rethrow(unwrapped);
      }
      finally {
        progress.cancel();
        waitForTermination();
      }
      return null;
    });
  }

  @TestOnly
  private void waitInOtherThread(int millis, boolean canChangeDocument, @NotNull ThrowableComputable<Boolean, Throwable> runWhile) throws Throwable {
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
      myProject.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonListener() {
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
      Future<?> passesFuture = ApplicationManager.getApplication().executeOnPooledThread(() -> myPassExecutorService.waitFor(System.currentTimeMillis() - deadline));
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
  public void prepareForTest() {
    setUpdateByTimerEnabled(false);
    waitForTermination();
    clearReferences();
  }

  @TestOnly
  public void cleanupAfterTest() {
    if (myProject.isOpen()) {
      prepareForTest();
    }
  }

  @TestOnly
  public void waitForTermination() {
    myPassExecutorService.cancelAll(true, "DaemonCodeAnalyzerImpl.waitForTermination");
  }

  @Override
  public void settingsChanged() {
    if (mySettings.isCodeHighlightingChanged(myLastSettings)) {
      restart();
    }
    myLastSettings = ((DaemonCodeAnalyzerSettingsImpl)mySettings).clone();
  }

  @Override
  public synchronized void setUpdateByTimerEnabled(boolean value) {
    myUpdateByTimerEnabled = value;
    stopProcess(value, "Update by timer change");
  }

  private final AtomicInteger myDisableCount = new AtomicInteger();

  @Override
  public void disableUpdateByTimer(@NotNull Disposable parentDisposable) {
    setUpdateByTimerEnabled(false);
    myDisableCount.incrementAndGet();
    ThreadingAssertions.assertEventDispatchThread();

    Disposer.register(parentDisposable, () -> {
      if (myDisableCount.decrementAndGet() == 0) {
        setUpdateByTimerEnabled(true);
      }
    });
  }

  synchronized boolean isUpdateByTimerEnabled() {
    return myUpdateByTimerEnabled;
  }

  @Override
  public void setImportHintsEnabled(@NotNull PsiFile file, boolean value) {
    assertMyFile(file.getProject(), file);
    VirtualFile vFile = file.getVirtualFile();
    if (value) {
      myDisabledHintsFiles.remove(vFile);
      stopProcess(true, "Import hints change");
    }
    else {
      myDisabledHintsFiles.add(vFile);
      HintManager.getInstance().hideAllHints();
    }
  }

  @Override
  public void resetImportHintsEnabledForProject() {
    myDisabledHintsFiles.clear();
  }

  @Override
  public void setHighlightingEnabled(@NotNull PsiFile psiFile, boolean value) {
    assertMyFile(psiFile.getProject(), psiFile);

    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiFile);
    if (value) {
      myDisabledHighlightingFiles.remove(virtualFile);
    }
    else {
      myDisabledHighlightingFiles.add(virtualFile);
    }
  }

  @Override
  public boolean isHighlightingAvailable(@NotNull PsiFile psiFile) {
    if (!psiFile.isPhysical()) return false;
    assertMyFile(psiFile.getProject(), psiFile);
    if (myDisabledHighlightingFiles.contains(PsiUtilCore.getVirtualFile(psiFile))) return false;

    if (psiFile instanceof PsiCompiledElement) return false;
    FileType fileType = psiFile.getFileType();

    // To enable T.O.D.O. highlighting
    return !fileType.isBinary();
  }

  @Override
  public boolean isImportHintsEnabled(@NotNull PsiFile psiFile) {
    return isAutohintsAvailable(psiFile) && !myDisabledHintsFiles.contains(psiFile.getVirtualFile());
  }

  @Override
  public boolean isAutohintsAvailable(@NotNull PsiFile psiFile) {
    return isHighlightingAvailable(psiFile) && !(psiFile instanceof PsiCompiledElement);
  }

  @Override
  public void restart() {
    stopProcessAndRestartAllFiles("Global restart");
  }

  // return true if the progress was really canceled
  void stopProcessAndRestartAllFiles(@NotNull String reason) {
    myFileStatusMap.markAllFilesDirty(reason);
    stopProcess(true, reason);
  }

  @Override
  public void restart(@NotNull PsiFile psiFile) {
    assertMyFile(psiFile.getProject(), psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    if (document == null) return;
    String reason = "Psi file restart: " + psiFile.getName();
    myFileStatusMap.markFileScopeDirty(document, new TextRange(0, document.getTextLength()), psiFile.getTextLength(), reason);
    stopProcess(true, reason);
  }

  public @NotNull List<ProgressableTextEditorHighlightingPass> getPassesToShowProgressFor(@NotNull Document document) {
    List<HighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    return allPasses.stream()
      .map(p->p instanceof ProgressableTextEditorHighlightingPass pPass ? pPass : null)
      .filter(p-> p != null && p.getDocument() == document)
      .sorted(Comparator.comparingInt(p->p.getId()))
      .collect(Collectors.toList());
  }

  /**
   * Used in tests, don't remove VisibleForTesting
   */
  @Override
  @ApiStatus.Internal
  @VisibleForTesting
  public boolean isAllAnalysisFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) return false;
    assertMyFile(psiFile.getProject(), psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    return document != null &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.allDirtyScopesAreNull(document);
  }

  @Override
  public boolean isErrorAnalyzingFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) return false;
    assertMyFile(psiFile.getProject(), psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    return document != null &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, psiFile, Pass.UPDATE_ALL) == null;
  }

  @Override
  public @NotNull FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  public synchronized boolean isRunning() {
    for (DaemonProgressIndicator indicator : myUpdateProgress.values()) {
      if (!indicator.isCanceled()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @TestOnly
  public boolean isRunningOrPending() {
    ThreadingAssertions.assertEventDispatchThread();
    return isRunning() || !myUpdateRunnableFuture.isDone() || GeneralHighlightingPass.isRestartPending();
  }

  /**
   * return true if the progress really was canceled
   * reset {@link #myScheduledUpdateTimestamp} always, but re-schedule {@link #myUpdateRunnable} only rarely because of thread scheduling overhead
   */
  synchronized void stopProcess(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    cancelAllUpdateProgresses(toRestartAlarm, reason);
    boolean restart = toRestartAlarm && !myDisposed;
    if (restart) {
      long autoReparseDelayNanos = TimeUnit.MILLISECONDS.toNanos(mySettings.getAutoReparseDelay());
      myScheduledUpdateTimestamp = System.nanoTime() + autoReparseDelayNanos;
      // optimisation: this check is to avoid too many re-schedules in case of thousands of event spikes
      boolean isDone = myUpdateRunnableFuture.isDone();
      if (isDone) {
        scheduleUpdateRunnable(autoReparseDelayNanos);
      }
    }
  }

  private synchronized void scheduleUpdateRunnable(long delayNanos) {
    Future<?> oldFuture = myUpdateRunnableFuture;
    if (oldFuture.isDone()) {
      ConcurrencyUtil.manifestExceptionsIn(oldFuture);
    }
    myUpdateRunnableFuture = EdtExecutorService.getScheduledExecutorInstance().schedule(myUpdateRunnable, delayNanos, TimeUnit.NANOSECONDS);
  }

  // return true if the progress really was canceled
  synchronized void cancelAllUpdateProgresses(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    if (myDisposed || myProject.isDisposed() || myProject.getMessageBus().isDisposed()) return;
    for (DaemonProgressIndicator updateProgress : myUpdateProgress.values()) {
      if (!updateProgress.isCanceled()) {
        PassExecutorService.log(updateProgress, null, "Cancel", reason, toRestartAlarm);
        updateProgress.cancel(reason);
        myPassExecutorService.cancelAll(false, reason);
      }
    }
    daemonCancelEventCount.incrementAndGet();
  }

  static boolean processHighlightsNearOffset(@NotNull Document document,
                                             @NotNull Project project,
                                             @NotNull HighlightSeverity minSeverity,
                                             int offset,
                                             boolean includeFixRange,
                                             @NotNull Processor<? super HighlightInfo> processor) {
    return processHighlights(document, project, null, 0, document.getTextLength(), info -> {
      if (!info.containsOffset(offset, includeFixRange)) return true;

      int compare = info.getSeverity().compareTo(minSeverity);
      return compare < 0 || processor.process(info);
    });
  }

  public @Nullable HighlightInfo findHighlightByOffset(@NotNull Document document, int offset, boolean includeFixRange) {
    return findHighlightByOffset(document, offset, includeFixRange, HighlightSeverity.INFORMATION);
  }

  @Nullable
  HighlightInfo findHighlightByOffset(@NotNull Document document,
                                      int offset,
                                      boolean includeFixRange,
                                      @NotNull HighlightSeverity minSeverity) {
    return findHighlightsByOffset(document, offset, includeFixRange, true, minSeverity);
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
  public @Nullable HighlightInfo findHighlightsByOffset(@NotNull Document document,
                                                        int offset,
                                                        boolean includeFixRange,
                                                        boolean highestPriorityOnly,
                                                        @NotNull HighlightSeverity minSeverity) {
    HighlightByOffsetProcessor processor = new HighlightByOffsetProcessor(highestPriorityOnly);
    processHighlightsNearOffset(document, myProject, minSeverity, offset, includeFixRange, processor);
    return processor.getResult();
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void waitForUnresolvedReferencesQuickFixesUnderCaret(@NotNull PsiFile file, @NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessNotAllowed();
    List<HighlightInfo> relevantInfos = new ArrayList<>();
    Project project = file.getProject();
    ReadAction.run(() -> {
      PsiUtilBase.assertEditorAndProjectConsistent(project, editor);
      CaretModel caretModel = editor.getCaretModel();
      int offset = caretModel.getOffset();
      Document document = editor.getDocument();
      int logicalLine = caretModel.getLogicalPosition().line;
      processHighlights(document, project, null, 0, document.getTextLength(), info -> {
        if (info.containsOffset(offset, true) && info.isUnresolvedReference()) {
          relevantInfos.add(info);
          return true;
        }
        // since we don't know fix ranges of potentially not-yet-added quick fixes, consider all HighlightInfos at the same line
        boolean atTheSameLine = editor.offsetToLogicalPosition(info.getActualStartOffset()).line <= logicalLine && logicalLine <= editor.offsetToLogicalPosition(info.getActualEndOffset()).line;
        if (atTheSameLine && info.isUnresolvedReference()) {
          relevantInfos.add(info);
        }
        return true;
      });
    });
    UnresolvedReferenceQuickFixUpdater.getInstance(project).waitQuickFixesSynchronously(file, editor, relevantInfos);
  }

  static final class HighlightByOffsetProcessor implements Processor<HighlightInfo> {
    private final List<HighlightInfo> foundInfoList = new SmartList<>();
    private final boolean highestPriorityOnly;

    HighlightByOffsetProcessor(boolean highestPriorityOnly) {
      this.highestPriorityOnly = highestPriorityOnly;
    }

    @Override
    public boolean process(@NotNull HighlightInfo info) {
      if (info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY || info.type == HighlightInfoType.TODO) {
        return true;
      }

      if (!foundInfoList.isEmpty() && highestPriorityOnly) {
        HighlightInfo foundInfo = foundInfoList.get(0);
        int compare = foundInfo.getSeverity().compareTo(info.getSeverity());
        if (compare < 0) {
          foundInfoList.clear();
        }
        else if (compare > 0) {
          return true;
        }
      }
      foundInfoList.add(info);
      return true;
    }

    @Nullable
    HighlightInfo getResult() {
      if (foundInfoList.isEmpty()) return null;
      if (foundInfoList.size() == 1) return foundInfoList.get(0);
      foundInfoList.sort(Comparator.comparing(HighlightInfo::getSeverity).reversed());
      return HighlightInfoComposite.create(foundInfoList);
    }
  }

  public static @NotNull List<LineMarkerInfo<?>> getLineMarkers(@NotNull Document document, @NotNull Project project) {
    List<LineMarkerInfo<?>> result = new ArrayList<>();
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    markupModel.processRangeHighlightersOverlappingWith(0, document.getTextLength(),
      highlighter -> {
        LineMarkerInfo<?> info = LineMarkersUtil.getLineMarkerInfo(highlighter);
        if (info != null) {
          result.add(info);
        }
        return true;
      }
    );
    return result;
  }

  @Nullable
  IntentionHintComponent getLastIntentionHint() {
    return ((IntentionsUIImpl)IntentionsUI.getInstance(myProject)).getLastIntentionHint();
  }

  @Override
  public boolean hasVisibleLightBulbOrPopup() {
    IntentionHintComponent hint = getLastIntentionHint();
    return hint != null && hint.hasVisibleLightBulbOrPopup();
  }

  @Override
  public @NotNull Element getState() {
    Element state = new Element("state");
    if (myDisabledHintsFiles.isEmpty()) {
      return state;
    }

    List<String> array = new ArrayList<>(myDisabledHintsFiles.size());
    for (VirtualFile file : myDisabledHintsFiles) {
      if (file.isValid()) {
        array.add(file.getUrl());
      }
    }

    if (!array.isEmpty()) {
      Collections.sort(array);

      Element disableHintsElement = new Element(DISABLE_HINTS_TAG);
      state.addContent(disableHintsElement);
      for (String url : array) {
        disableHintsElement.addContent(new Element(FILE_TAG).setAttribute(URL_ATT, url));
      }
    }
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myDisabledHintsFiles.clear();

    Element element = state.getChild(DISABLE_HINTS_TAG);
    if (element != null) {
      for (Element e : element.getChildren(FILE_TAG)) {
        String url = e.getAttributeValue(URL_ATT);
        if (url != null) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
          if (file != null) {
            myDisabledHintsFiles.add(file);
          }
        }
      }
    }
  }

  // made this class static and fields clearable to avoid leaks when this object stuck in invokeLater queue
  private static final class UpdateRunnable implements Runnable {
    private Project myProject;
    private UpdateRunnable(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void run() {
      runUpdate(myProject, this);
    }

    private void clearFieldsOnDispose() {
      myProject = null;
    }
  }

  private static void runUpdate(@Nullable Project project, @NotNull UpdateRunnable updateRunnable) {
    ThreadingAssertions.assertEventDispatchThread();
    DaemonCodeAnalyzerImpl dca;
    if (project == null ||
        project.isDefault() ||
        !project.isInitialized() ||
        project.isDisposed() ||
        LightEdit.owns(project) ||
        (dca = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project)).myDisposed) {
      return;
    }
    if (PowerSaveMode.isEnabled()) {
      // to show the correct "power save" traffic light icon
      dca.myListeners.repaintTrafficLightIconForAllEditors();
      return;
    }

    synchronized (dca) {
      long actualDelay = dca.myScheduledUpdateTimestamp - System.nanoTime();
      if (actualDelay > 0) {
         // started too soon (there must've been some typings after we'd scheduled this; need to re-schedule)
        dca.scheduleUpdateRunnable(actualDelay);
        return;
      }
    }

    Collection<? extends FileEditor> activeEditors = dca.getSelectedEditors();
    boolean updateByTimerEnabled = dca.isUpdateByTimerEnabled();
    if (PassExecutorService.LOG.isDebugEnabled()) {
      PassExecutorService.log(null, null, "Update Runnable. myUpdateByTimerEnabled:",
        updateByTimerEnabled, " activeEditors:", activeEditors,
        (dca.getPsiDocumentManager().hasEventSystemEnabledUncommittedDocuments() ? " hasEventSystemEnabledUncommittedDocuments(" + Arrays.toString(dca.getPsiDocumentManager().getUncommittedDocuments()) + ")" : "()")
        + (ApplicationManager.getApplication().isWriteAccessAllowed() ? " inside write action" : "r"));
    }
    if (!updateByTimerEnabled || activeEditors.isEmpty()) {
      return;
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      // makes no sense to start from within write action - will cancel anyway
      // we'll restart when the write action finish
      return;
    }
    if (dca.getPsiDocumentManager().hasEventSystemEnabledUncommittedDocuments()) {
      // restart when everything committed
      dca.getPsiDocumentManager().performLaterWhenAllCommitted(updateRunnable);
      return;
    }

    try {
      boolean submitted = false;
      for (FileEditor fileEditor : activeEditors) {
        if (fileEditor instanceof TextEditor textEditor && !AsyncEditorLoader.Companion.isEditorLoaded(textEditor.getEditor())) {
          // make sure the highlighting is restarted when the editor is finally loaded, because otherwise some crazy things happen,
          // for instance `FileEditor.getBackgroundHighlighter()` returning null, essentially stopping highlighting silently
          AsyncEditorLoader.Companion.performWhenLoaded(((TextEditor)fileEditor).getEditor(), updateRunnable);
        }
        VirtualFile virtualFile = getVirtualFile(fileEditor);
        PsiFile psiFile = virtualFile == null ? null : findFileToHighlight(dca.myProject, virtualFile);
        submitted |= psiFile != null && dca.queuePassesCreation(fileEditor, virtualFile, psiFile, ArrayUtil.EMPTY_INT_ARRAY) != null;
        if (PassExecutorService.LOG.isDebugEnabled()) {
          PassExecutorService.log(null, null, "submitting psiFile:", psiFile+" ("+virtualFile+"); submitted=", submitted);
        }
      }
      if (!submitted) {
        // happens e.g., when we are trying to open a directory and there's a FileEditor supporting this
        dca.stopProcess(true, "Couldn't create session for "+activeEditors);
      }
    }
    catch (ProcessCanceledException ignored) {
    }
  }

  private static VirtualFile getVirtualFile(@NotNull FileEditor fileEditor) {
    VirtualFile virtualFile = fileEditor.getFile();
    for (BackedVirtualFileProvider provider : BackedVirtualFileProvider.EP_NAME.getExtensionList()) {
      VirtualFile replacedVirtualFile = provider.getReplacedVirtualFile(virtualFile);
      if (replacedVirtualFile != null) {
        virtualFile = replacedVirtualFile;
        break;
      }
    }
    return virtualFile;
  }

  /**
   * @return HighlightingSession when everything's OK or
   * return null if session wasn't created because highlighter/document/psiFile wasn't found or
   * throw PCE if it really wasn't an appropriate moment to ask
   */
  private HighlightingSession queuePassesCreation(@NotNull FileEditor fileEditor,
                                                  @NotNull VirtualFile virtualFile,
                                                  @NotNull PsiFile psiFile,
                                                  int @NotNull [] passesToIgnore) {
    ThreadingAssertions.assertEventDispatchThread();
    BackgroundEditorHighlighter highlighter;

    try (AccessToken ignored = ClientId.withClientId(ClientFileEditorManager.getClientId(fileEditor))) {
      highlighter = fileEditor.getBackgroundHighlighter();
    }
    Editor editor = fileEditor instanceof TextEditor textEditor ? textEditor.getEditor() : null;
    if (highlighter == null) {
      if (PassExecutorService.LOG.isDebugEnabled()) {
        PassExecutorService.log(null, null, "couldn't highlight", virtualFile, "because getBackgroundHighlighter() returned null. fileEditor=",
          fileEditor,fileEditor.getClass(),(editor == null ? "editor is null" : "editor loaded:"+ AsyncEditorLoader.Companion.isEditorLoaded(editor)));
      }
      return null;
    }
    DaemonProgressIndicator progress = createUpdateProgress(fileEditor);
    // pre-create HighlightingSession in EDT to make visible range available in a background thread
    if (editor != null && editor.getDocument().isInBulkUpdate()) {
      // avoid restarts until the bulk mode is finished and daemon restarted in DaemonListeners
      stopProcess(false, editor.getDocument() +" is in bulk state");
      throw new ProcessCanceledException();
    }
    Document document = editor == null ? FileDocumentManager.getInstance().getCachedDocument(virtualFile) : editor.getDocument();
    if (document == null) {
      if (PassExecutorService.LOG.isDebugEnabled()) {
        PassExecutorService.log(progress, null, "couldn't submit", virtualFile, "because document is null: fileEditor=",fileEditor,fileEditor.getClass());
      }
      return null;
    }
    EditorColorsScheme scheme = editor == null ? null : editor.getColorsScheme();
    HighlightingSessionImpl session;
    try (AccessToken ignored = ClientId.withClientId(ClientFileEditorManager.getClientId(fileEditor))) {
      session = HighlightingSessionImpl.createHighlightingSession(psiFile, editor, scheme, progress, daemonCancelEventCount);
    }
    JobLauncher.getInstance().submitToJobThread(Context.current().wrap(() ->
      submitInBackground(fileEditor, document, virtualFile, psiFile, highlighter, passesToIgnore, progress, session)),
      // manifest exceptions in EDT to avoid storing them in the Future and abandoning
      task -> ApplicationManager.getApplication().invokeLater(() -> ConcurrencyUtil.manifestExceptionsIn(task)));
    return session;
  }

  private static PsiFile findFileToHighlight(@NotNull Project project, @Nullable VirtualFile virtualFile) {
    PsiFile psiFile = virtualFile == null || !virtualFile.isValid() ? null : ((FileManagerImpl)PsiManagerEx.getInstanceEx(project)
      .getFileManager()).getFastCachedPsiFile(virtualFile);
    psiFile = psiFile instanceof PsiCompiledFile compiled ? compiled.getDecompiledPsiFile() : psiFile;
    return psiFile;
  }

  private void submitInBackground(@NotNull FileEditor fileEditor,
                                  @NotNull Document document,
                                  @NotNull VirtualFile virtualFile,
                                  @NotNull PsiFile psiFile,
                                  @NotNull BackgroundEditorHighlighter backgroundEditorHighlighter,
                                  int @NotNull [] passesToIgnore,
                                  @NotNull DaemonProgressIndicator progress,
                                  @NotNull HighlightingSessionImpl session) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    if (myProject.isDisposed()) {
      stopProcess(false, "project disposed");
      return;
    }
    if (progress.isCanceled()) {
      stopProcess(true, "canceled in queuePassesCreation: "+progress.getCancellationTrace());
      return;
    }
    if (getPsiDocumentManager().hasEventSystemEnabledUncommittedDocuments()) {
      stopProcess(true, "more documents to commit: " + ReadAction.compute(() -> Arrays.toString(getPsiDocumentManager().getUncommittedDocuments())));
      return;
    }
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(Context.current().wrap(() -> {
        // wait for heavy processing to stop, re-schedule daemon but not too soon
        boolean heavyProcessIsRunning = heavyProcessIsRunning();
        HighlightingPass[] passes = ReadAction.compute(() -> {
          if (myProject.isDisposed() || !fileEditor.isValid() || !psiFile.isValid()) {
            return HighlightingPass.EMPTY_ARRAY;
          }
          if (session.isCanceled()) {
            // editor or something was changed between commit document notification in EDT and this point in the FJP thread
            throw new ProcessCanceledException();
          }
          session.additionalSetupFromBackground(psiFile);
          try (AccessToken ignored = ClientId.withClientId(ClientFileEditorManager.getClientId(fileEditor))) {
            HighlightingPass[] r = backgroundEditorHighlighter instanceof TextEditorBackgroundHighlighter textHighlighter ?
                                   textHighlighter.getPasses(passesToIgnore).toArray(HighlightingPass.EMPTY_ARRAY) :
                                   backgroundEditorHighlighter.createPassesForEditor();
            if (heavyProcessIsRunning) {
              r = ContainerUtil.findAllAsArray(r, DumbService::isDumbAware);
            }
            return r;
          }
        });
        boolean hasPasses = passes.length != 0;
        if (!hasPasses) {
          // will be re-scheduled by HeavyLatch listener in DaemonListeners
          return;
        }
        // synchronize on TextEditorHighlightingPassRegistrarImpl instance to avoid concurrent modification of TextEditorHighlightingPassRegistrarImpl.nextAvailableId
        synchronized (TextEditorHighlightingPassRegistrar.getInstance(myProject)) {
          myPassExecutorService.submitPasses(document, virtualFile, psiFile, fileEditor, passes, progress);
        }
      }), progress);
    }
    catch (ProcessCanceledException e) {
      LOG.debug(e);
      stopProcess(true, "PCE in queuePassesCreation");
    }
    catch (Throwable e) {
      // make it manifestable in tests
      PassExecutorService.saveException(e, progress);
      throw e;
    }
  }

  // return true if a heavy op is running
  private static boolean heavyProcessIsRunning() {
    if (DumbServiceImpl.ALWAYS_SMART) return false;
    // VFS refresh is OK
    return HeavyProcessLatch.INSTANCE.isRunningAnythingBut(HeavyProcessLatch.Type.Syncing);
  }

  private synchronized @NotNull DaemonProgressIndicator createUpdateProgress(@NotNull FileEditor fileEditor) {
    DaemonProgressIndicator old = myUpdateProgress.get(fileEditor);
    if (old != null && !old.isCanceled()) {
      old.cancel();
    }
    myUpdateProgress.entrySet().removeIf(entry -> !entry.getKey().isValid());
    DaemonProgressIndicator progress = new MyDaemonProgressIndicator(myProject, fileEditor);
    progress.setModalityProgress(null);
    progress.start();
    myDaemonListenerPublisher.daemonStarting(List.of(fileEditor));
    if (isRestartToCompleteEssentialHighlightingRequested()) {
      progress.putUserData(COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY, true);
    }
    myUpdateProgress.put(fileEditor, progress);
    return progress;
  }

  private static final class MyDaemonProgressIndicator extends DaemonProgressIndicator {
    private final Project myProject;
    private FileEditor myFileEditor;

    MyDaemonProgressIndicator(@NotNull Project project, @NotNull FileEditor fileEditor) {
      myFileEditor = fileEditor;
      myProject = project;
    }

    @Override
    public void onCancelled(@NotNull String reason) {
      DaemonCodeAnalyzerImpl daemon = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      daemon.myDaemonListenerPublisher.daemonCanceled(reason, List.of(myFileEditor));
      myFileEditor = null;
    }

    @Override
    public void onStop() {
      DaemonCodeAnalyzerImpl daemon = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      daemon.myDaemonListenerPublisher.daemonFinished(List.of(myFileEditor));
      myFileEditor = null;
      HighlightingSessionImpl.clearProgressIndicator(this);
      daemon.completeEssentialHighlightingRequested = false;
    }
  }

  @Override
  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    assertMyFile(psiFile.getProject(), psiFile);
    for (ReferenceImporter importer : ReferenceImporter.EP_NAME.getExtensionList()) {
      if (importer.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) && importer.autoImportReferenceAtCursor(editor, psiFile)) break;
    }
  }

  @TestOnly
  synchronized @NotNull Map<FileEditor, DaemonProgressIndicator> getUpdateProgress() {
    return myUpdateProgress;
  }

  private @NotNull Collection<? extends FileEditor> getSelectedEditors() {
    ThreadingAssertions.assertEventDispatchThread();
    // editors in modal context
    List<? extends Editor> editors = EditorTracker.getInstance(myProject).getActiveEditors();
    Collection<FileEditor> activeTextEditors = new HashSet<>(editors.size());
    Set<VirtualFile> files = new HashSet<>(editors.size());
    if (!editors.isEmpty()) {
      TextEditorProvider textEditorProvider = TextEditorProvider.getInstance();
      for (Editor editor : editors) {
        if (!editor.isDisposed()) {
          TextEditor textEditor = textEditorProvider.getTextEditor(editor);
          if (isValidEditor(textEditor)) {
            VirtualFile virtualFile = textEditor.getFile();
            activeTextEditors.add(textEditor);
            files.add(virtualFile);
          }
        }
      }
    }

    if (ModalityState.current() != ModalityState.nonModal()) {
      return activeTextEditors;
    }

    // tests usually care about just one explicitly configured editor
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      for (FileEditor tabEditor : getFileEditorManager().getSelectedEditorWithRemotes()) {
        if (!isValidEditor(tabEditor)) continue;

        if (tabEditor instanceof FileEditorWithTextEditors delegate) {
          TextEditorProvider textEditorProvider = TextEditorProvider.getInstance();

          for (Editor embeddedEditor : delegate.getEmbeddedEditors()) {
            TextEditor embeddedTextEditor = textEditorProvider.getTextEditor(embeddedEditor);
            if (files.add(embeddedTextEditor.getFile()) && isValidEditor(embeddedTextEditor)) {
              activeTextEditors.add(embeddedTextEditor);
            }
          }
        }
        else if (files.add(tabEditor.getFile())) {
          activeTextEditors.add(tabEditor);
        }
      }
    }

    return activeTextEditors;
  }

  private static boolean isValidEditor(@NotNull FileEditor editor) {
    VirtualFile virtualFile = editor.getFile();
    return virtualFile != null && virtualFile.isValid() && editor.isValid() && isInActiveProject(editor);
  }

  private static boolean isInActiveProject(@NotNull FileEditor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    if (ProjectManager.getInstance().getOpenProjects().length <= 1 || ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    // optimization: in the case of two or more projects, ignore projects which are not active at the moment, e.g., inside minimized windows
    // see IDEA-314543 Don't run LineMarkersPass on non-active(opened) projects
    Window window = SwingUtilities.getWindowAncestor(editor.getComponent());
    return window == null || window.isActive();
  }

  /**
   * This API is made {@code Internal} intentionally as it could lead to unpredictable highlighting performance behaviour.
   *
   * @param flag if {@code true}: enables code insight passes serialization:
   *             Injected fragments {@link InjectedGeneralHighlightingPass} highlighting and Inspections run after
   *             completion of Syntax analysis {@link GeneralHighlightingPass}.
   *             if {@code false} (default behaviour) code insight passes are running in parallel
   */
  @ApiStatus.Internal
  public void serializeCodeInsightPasses(boolean flag) {
    ThreadingAssertions.assertEventDispatchThread();
    setUpdateByTimerEnabled(false);
    try {
      cancelAllUpdateProgresses(false, "serializeCodeInsightPasses");
      TextEditorHighlightingPassRegistrarImpl registrar =
        (TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(myProject);
      registrar.serializeCodeInsightPasses(flag);
    }
    finally {
      setUpdateByTimerEnabled(true);
    }
  }

  // tell the next restarted highlighting that it should start all inspections/external annotators/etc
  void restartToCompleteEssentialHighlighting() {
    restart();
    completeEssentialHighlightingRequested = true;
  }
  public boolean isRestartToCompleteEssentialHighlightingRequested() {
    return completeEssentialHighlightingRequested;
  }
}
