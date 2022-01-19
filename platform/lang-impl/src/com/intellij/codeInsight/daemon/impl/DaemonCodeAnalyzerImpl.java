// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class also controls the auto-reparse and auto-hints.
 */
@State(name = "DaemonCodeAnalyzer", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzerEx implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(DaemonCodeAnalyzerImpl.class);

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");
  private static final @NotNull Key<Boolean> COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY = Key.create("COMPLETE_ESSENTIAL_HIGHLIGHTING");
  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  @NotNull private final PsiDocumentManager myPsiDocumentManager;
  private final FileEditorManagerEx myFileEditorManager;
  private final EditorTracker myEditorTracker;
  private DaemonProgressIndicator myUpdateProgress = new DaemonProgressIndicator(); //guarded by this

  private final UpdateRunnable myUpdateRunnable;
  // use scheduler instead of Alarm because the latter requires ModalityState.current() which is obtainable from EDT only which requires too many invokeLaters
  private final ScheduledExecutorService myAlarm = EdtExecutorService.getScheduledExecutorInstance();
  @NotNull
  private volatile Future<?> myUpdateRunnableFuture = CompletableFuture.completedFuture(null);
  private boolean myUpdateByTimerEnabled = true; // guarded by this
  private final Collection<VirtualFile> myDisabledHintsFiles = new HashSet<>();
  private final Collection<VirtualFile> myDisabledHighlightingFiles = new HashSet<>();

  private final FileStatusMap myFileStatusMap;
  private DaemonCodeAnalyzerSettings myLastSettings;

  private volatile boolean myDisposed;     // the only possible transition: false -> true
  private volatile boolean myInitialized;  // the only possible transition: false -> true

  @NonNls private static final String DISABLE_HINTS_TAG = "disable_hints";
  @NonNls private static final String FILE_TAG = "file";
  @NonNls private static final String URL_ATT = "url";
  private final PassExecutorService myPassExecutorService;
  // Timestamp of myUpdateRunnable which it's needed to start (in System.nanoTime() sense)
  // May be later than the actual ScheduledFuture sitting in the myAlarm queue.
  // When it's so happens that future is started sooner than myScheduledUpdateStart, it will re-schedule itself for later.
  private long myScheduledUpdateTimestamp; // guarded by this
  volatile private boolean completeEssentialHighlightingRequested;

  public DaemonCodeAnalyzerImpl(@NotNull Project project) {
    // DependencyValidationManagerImpl adds scope listener, so, we need to force service creation
    DependencyValidationManager.getInstance(project);

    myProject = project;
    mySettings = DaemonCodeAnalyzerSettings.getInstance();
    myPsiDocumentManager = PsiDocumentManager.getInstance(project);
    myLastSettings = ((DaemonCodeAnalyzerSettingsImpl)mySettings).clone();

    myFileStatusMap = new FileStatusMap(project);
    myPassExecutorService = new PassExecutorService(project);
    Disposer.register(this, myPassExecutorService);
    Disposer.register(this, myFileStatusMap);
    //noinspection TestOnlyProblems
    DaemonProgressIndicator.setDebug(LOG.isDebugEnabled());

    assert !myInitialized : "Double Initializing";
    Disposer.register(this, new StatusBarUpdater(project));

    myInitialized = true;
    myDisposed = false;
    myFileStatusMap.markAllFilesDirty("DCAI init");
    myUpdateRunnable = new UpdateRunnable(project);
    Disposer.register(this, () -> {
      assert myInitialized : "Disposing not initialized component";
      assert !myDisposed : "Double dispose";
      myUpdateRunnable.clearFieldsOnDispose();

      stopProcess(false, "Dispose "+project);

      myDisposed = true;
      myLastSettings = null;
    });
    myFileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    if (myFileEditorManager == null && !project.isDefault()) {
      throw new IllegalStateException("FileEditorManagerEx.getInstanceEx(myProject) = null; myProject="+project);
    }
    myEditorTracker = EditorTracker.getInstance(project);
  }

  @Override
  public synchronized void dispose() {
    clearReferences();
  }

  private synchronized void clearReferences() {
    myUpdateProgress = new DaemonProgressIndicator(); // leak of highlight session via user data
    myUpdateRunnableFuture.cancel(true);
  }

  synchronized void clearProgressIndicator() {
    HighlightingSessionImpl.clearProgressIndicator(myUpdateProgress);
  }

  public boolean isCompleteEssentialHighlightingSession() {
    return myUpdateProgress.getUserData(COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY) == Boolean.TRUE;
  }

  @NotNull
  @TestOnly
  public static List<HighlightInfo> getHighlights(@NotNull Document document,
                                                  @Nullable HighlightSeverity minSeverity,
                                                  @NotNull Project project) {
    List<HighlightInfo> infos = new ArrayList<>();
    processHighlights(document, project, minSeverity, 0, document.getTextLength(),
                      Processors.cancelableCollectProcessor(infos));
    return infos;
  }

  @Override
  @NotNull
  @TestOnly
  public List<HighlightInfo> getFileLevelHighlights(@NotNull Project project, @NotNull PsiFile file) {
    assertMyProject(file.getProject());
    assertMyProject(project);
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    return Arrays.stream(myFileEditorManager.getAllEditors(vFile))
      .map(fileEditor -> fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS))
      .filter(Objects::nonNull)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }

  private void assertMyProject(@NotNull Project project) {
    assert project == myProject : "my project is " + myProject + " but I was called with " + project;
  }

  @Override
  public void cleanFileLevelHighlights(int group, @NotNull PsiFile psiFile) {
    assertMyProject(psiFile.getProject());
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : myFileEditorManager.getAllEditors(vFile)) {
      cleanFileLevelHighlights(fileEditor, group);
    }
  }

  private static final int ANY_GROUP = -409423948;
  void cleanAllFileLevelHighlights() {
    for (FileEditor fileEditor : myFileEditorManager.getAllEditors()) {
      cleanFileLevelHighlights(fileEditor, ANY_GROUP);
    }
  }

  private void cleanFileLevelHighlights(@NotNull FileEditor fileEditor, int group) {
    List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
    if (infos == null) return;
    List<HighlightInfo> infosToRemove = new ArrayList<>(infos.size());
    for (HighlightInfo info : infos) {
      if (info.getGroup() == group || group == ANY_GROUP) {
        JComponent component = info.getFileLevelComponent(fileEditor);
        if (component != null) {
          myFileEditorManager.removeTopComponent(fileEditor, component);
          info.removeFileLeverComponent(fileEditor);
        }
        infosToRemove.add(info);
      }
    }
    infos.removeAll(infosToRemove);
  }

  @Override
  public void addFileLevelHighlight(int group,
                                    @NotNull HighlightInfo info,
                                    @NotNull PsiFile psiFile) {
    assertMyProject(psiFile.getProject());
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : myFileEditorManager.getAllEditors(vFile)) {
      if (fileEditor instanceof TextEditor) {
        FileLevelIntentionComponent component = new FileLevelIntentionComponent(info.getDescription(), info.getSeverity(),
                                                                                info.getGutterIconRenderer(), info.quickFixActionRanges,
                                                                                psiFile, ((TextEditor)fileEditor).getEditor(), info.getToolTip());
        myFileEditorManager.addTopComponent(fileEditor, component);
        List<HighlightInfo> fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
        if (fileLevelInfos == null) {
          fileLevelInfos = new ArrayList<>();
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos);
        }
        info.addFileLeverComponent(fileEditor, component);
        info.setGroup(group);
        fileLevelInfos.add(info);
      }
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile,
                                           @NotNull Document document,
                                           @NotNull ProgressIndicator progress) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new IllegalStateException("Must not run highlighting from under EDT");
    }
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new IllegalStateException("Must run highlighting from under read action");
    }
    assertMyProject(psiFile.getProject());

    GlobalInspectionContextBase.assertUnderDaemonProgress();
    // clear status maps to run passes from scratch so that refCountHolder won't conflict and try to restart itself on partially filled maps
    myFileStatusMap.markAllFilesDirty("prepare to run main passes");
    stopProcess(false, "disable background daemon");
    myPassExecutorService.cancelAll(true);

    List<HighlightInfo> result;
    try {
      result = new ArrayList<>();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null && !virtualFile.getFileType().isBinary()) {
        List<TextEditorHighlightingPass> passes =
          TextEditorHighlightingPassRegistrarEx.getInstanceEx(myProject).instantiateMainPasses(psiFile, document,
                                                                                               HighlightInfoProcessor.getEmpty());

        passes.sort((o1, o2) -> {
          if (o1 instanceof GeneralHighlightingPass) return -1;
          if (o2 instanceof GeneralHighlightingPass) return 1;
          return 0;
        });

        try {
          for (TextEditorHighlightingPass pass : passes) {
            pass.doCollectInformation(progress);
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
                        @NotNull List<? extends TextEditor> textEditors,
                        int @NotNull [] toIgnore,
                        boolean canChangeDocument,
                        @Nullable Runnable callbackWhileWaiting) throws ProcessCanceledException {
    assert myInitialized;
    assert !myDisposed;
    assertMyProject(file.getProject());
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (application.isWriteAccessAllowed()) {
      throw new AssertionError("Must not start highlighting from within write action, or deadlock is imminent");
    }
    DaemonProgressIndicator.setDebug(!ApplicationManagerEx.isInStressTest());
    ((FileTypeManagerImpl)FileTypeManager.getInstance()).drainReDetectQueue();
    do {
      UIUtil.dispatchAllInvocationEvents();
      // refresh will fire write actions interfering with highlighting
      // heavy ops are bad, but VFS refresh is ok
    }
    while (RefreshQueueImpl.isRefreshInProgress() || postponeBecauseOfHeavyProcess());
    long dstart = System.currentTimeMillis();
    while (mustWaitForSmartMode && DumbService.getInstance(myProject).isDumb()) {
      if (System.currentTimeMillis() > dstart + 100000) {
        throw new IllegalStateException("Timeout waiting for smart mode. If you absolutely want to be dumb, please use DaemonCodeAnalyzerImpl.mustWaitForSmartMode(false).");
      }
      UIUtil.dispatchAllInvocationEvents();
    }
    ((GistManagerImpl)GistManager.getInstance()).clearQueueInTests();
    UIUtil.dispatchAllInvocationEvents();

    FileStatusMap fileStatusMap = getFileStatusMap();

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion(); // wait for async editor loading
    Map<FileEditor, HighlightingPass[]> map = new HashMap<>();
    for (TextEditor textEditor : textEditors) {
      TextEditorBackgroundHighlighter highlighter = (TextEditorBackgroundHighlighter)textEditor.getBackgroundHighlighter();
      if (highlighter == null) {
        Editor editor = textEditor.getEditor();
        throw new RuntimeException("Null highlighter from " + textEditor + "; loaded: " + AsyncEditorLoader.isEditorLoaded(editor));
      }
      List<TextEditorHighlightingPass> passes = highlighter.getPasses(toIgnore);
      HighlightingPass[] array = passes.toArray(HighlightingPass.EMPTY_ARRAY);
      assert array.length != 0 : "Highlighting is disabled for the file " + file;
      map.put(textEditor, array);
    }
    for (int ignoreId : toIgnore) {
      fileStatusMap.markFileUpToDate(document, ignoreId);
    }

    myUpdateRunnableFuture.cancel(false);
    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myPassExecutorService.cancelAll(false);
    fileStatusMap.allowDirt(canChangeDocument);
    DaemonProgressIndicator progress = createUpdateProgress(map.keySet());
    myPassExecutorService.submitPasses(map, progress);
    try {
      long start = System.currentTimeMillis();
      while (progress.isRunning() && System.currentTimeMillis() < start + 10*60*1000) {
        progress.checkCanceled();
        if (callbackWhileWaiting != null) {
          callbackWhileWaiting.run();
        }
        waitInOtherThread(50, canChangeDocument);
        UIUtil.dispatchAllInvocationEvents();
        Throwable savedException = PassExecutorService.getSavedException(progress);
        if (savedException != null) throw savedException;
      }
      if (progress.isRunning() && !progress.isCanceled()) {
        throw new RuntimeException("Highlighting still running after " +(System.currentTimeMillis()-start)/1000 + " seconds." +
                                   " Still submitted passes: "+myPassExecutorService.getAllSubmittedPasses()+
                                   " ForkJoinPool.commonPool(): "+ForkJoinPool.commonPool()+"\n"+
                                   ", ForkJoinPool.commonPool() active thread count: "+ ForkJoinPool.commonPool().getActiveThreadCount()+
                                   ", ForkJoinPool.commonPool() has queued submissions: "+ ForkJoinPool.commonPool().hasQueuedSubmissions()+
                                   "\n"+ ThreadDumper.dumpThreadsToString());
      }

      HighlightingSessionImpl session = (HighlightingSessionImpl)HighlightingSessionImpl.getOrCreateHighlightingSession(file, progress, null);
      if (!waitInOtherThread(60000, canChangeDocument)) {
        throw new TimeoutException("Unable to complete in 60s. Thread dump:\n"+ThreadDumper.dumpThreadsToString());
      }
      session.waitForHighlightInfosApplied();
      UIUtil.dispatchAllInvocationEvents();
      UIUtil.dispatchAllInvocationEvents();
      assert progress.isCanceled() && progress.isDisposed();
    }
    catch (Throwable e) {
      Throwable unwrapped = ExceptionUtilRt.unwrapException(e, ExecutionException.class);
      if (progress.isCanceled() && progress.isRunning()) {
        unwrapped.addSuppressed(new RuntimeException("Daemon progress was canceled unexpectedly: " + progress));
      }
      ExceptionUtil.rethrow(unwrapped);
    }
    finally {
      DaemonProgressIndicator.setDebug(false);
      fileStatusMap.allowDirt(true);
      progress.cancel();
      waitForTermination();
    }
  }

  @TestOnly
  private boolean waitInOtherThread(int millis, boolean canChangeDocument) throws Throwable {
    Disposable disposable = Disposer.newDisposable();
    // last hope protection against PsiModificationTrackerImpl.incCounter() craziness (yes, Kotlin)
    myProject.getMessageBus().connect(disposable).subscribe(PsiModificationTracker.TOPIC,
      () -> {
        throw new IllegalStateException("You must not perform PSI modifications from inside highlighting");
      });
    if (!canChangeDocument) {
      myProject.getMessageBus().connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonListener() {
        @Override
        public void daemonCancelEventOccurred(@NotNull String reason) {
          throw new IllegalStateException("You must not cancel daemon inside highlighting test: "+reason);
        }
      });
    }

    try {
      Future<Boolean> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          return myPassExecutorService.waitFor(millis);
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      });
      return future.get(millis, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException | InterruptedException ex) {
      return false;
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
    myPassExecutorService.cancelAll(true);
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
    ApplicationManager.getApplication().assertIsDispatchThread();

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
    assertMyProject(file.getProject());
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
    assertMyProject(psiFile.getProject());

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
    assertMyProject(psiFile.getProject());
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
    doRestart("Global restart");
  }

  // return true if the progress was really canceled
  boolean doRestart(@NotNull String reason) {
    myFileStatusMap.markAllFilesDirty(reason);
    return stopProcess(true, reason);
  }

  @Override
  public void restart(@NotNull PsiFile psiFile) {
    assertMyProject(psiFile.getProject());
    Document document = myPsiDocumentManager.getCachedDocument(psiFile);
    if (document == null) return;
    String reason = "Psi file restart: " + psiFile.getName();
    myFileStatusMap.markFileScopeDirty(document, new TextRange(0, document.getTextLength()), psiFile.getTextLength(), reason);
    stopProcess(true, reason);
  }

  @NotNull
  public List<ProgressableTextEditorHighlightingPass> getPassesToShowProgressFor(@NotNull Document document) {
    List<HighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    return allPasses.stream()
      .map(p->p instanceof ProgressableTextEditorHighlightingPass ? (ProgressableTextEditorHighlightingPass)p : null)
      .filter(p-> p != null && p.getDocument() == document)
      .sorted(Comparator.comparingInt(p->p.getId()))
      .collect(Collectors.toList());
  }

  boolean isAllAnalysisFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) return false;
    assertMyProject(psiFile.getProject());
    Document document = myPsiDocumentManager.getCachedDocument(psiFile);
    return document != null &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.allDirtyScopesAreNull(document);
  }

  @Override
  public boolean isErrorAnalyzingFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) return false;
    assertMyProject(psiFile.getProject());
    Document document = myPsiDocumentManager.getCachedDocument(psiFile);
    return document != null &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, Pass.UPDATE_ALL) == null;
  }

  @Override
  @NotNull
  public FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  public synchronized boolean isRunning() {
    return !myUpdateProgress.isCanceled();
  }

  @TestOnly
  public boolean isRunningOrPending() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return isRunning() || !myUpdateRunnableFuture.isDone() || GeneralHighlightingPass.isRestartPending();
  }

  // return true if the progress really was canceled
  synchronized boolean stopProcess(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    boolean canceled = cancelUpdateProgress(toRestartAlarm, reason);
    boolean restart = toRestartAlarm && !myDisposed && myInitialized;

    // reset myScheduledUpdateStart always, but re-schedule myUpdateRunnable only rarely because of thread scheduling overhead
    if (restart) {
      myScheduledUpdateTimestamp = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(mySettings.getAutoReparseDelay());
    }
    // optimisation: this check is to avoid too many re-schedules in case of thousands of event spikes
    boolean isDone = myUpdateRunnableFuture.isDone();
    if (restart && isDone) {
      scheduleUpdateRunnable(TimeUnit.MILLISECONDS.toNanos(mySettings.getAutoReparseDelay()));
    }

    return canceled;
  }

  private void scheduleUpdateRunnable(long delayNanos) {
    Future<?> oldFuture = myUpdateRunnableFuture;
    if (oldFuture.isDone()) {
      ConcurrencyUtil.manifestExceptionsIn(oldFuture);
    }
    myUpdateRunnableFuture = myAlarm.schedule(myUpdateRunnable, delayNanos, TimeUnit.NANOSECONDS);
  }

  // return true if the progress really was canceled
  private synchronized boolean cancelUpdateProgress(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    DaemonProgressIndicator updateProgress = myUpdateProgress;
    if (myDisposed) return false;
    boolean wasCanceled = updateProgress.isCanceled();
    if (!wasCanceled) {
      PassExecutorService.log(updateProgress, null, "Cancel", reason, toRestartAlarm);
      updateProgress.cancel();
      myPassExecutorService.cancelAll(false);
      return true;
    }
    return false;
  }

  void cancelSubmittedPasses() {
    myPassExecutorService.cancelAll(true);
  }


  static boolean processHighlightsNearOffset(@NotNull Document document,
                                             @NotNull Project project,
                                             @NotNull HighlightSeverity minSeverity,
                                             int offset,
                                             boolean includeFixRange,
                                             @NotNull Processor<? super HighlightInfo> processor) {
    return processHighlights(document, project, null, 0, document.getTextLength(), info -> {
      if (!isOffsetInsideHighlightInfo(offset, info, includeFixRange)) return true;

      int compare = info.getSeverity().compareTo(minSeverity);
      return compare < 0 || processor.process(info);
    });
  }

  @Nullable
  public HighlightInfo findHighlightByOffset(@NotNull Document document, int offset, boolean includeFixRange) {
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
   * If there's several infos they're combined into HighlightInfoComposite and returned as a single object.
   * Several options are available to adjust the collecting strategy
   *
   * @param document document in which the collecting is performed
   * @param offset offset which infos should intersect with to be collected
   * @param includeFixRange states whether the rage of a fix associated with an info should be taken into account during the range checking
   * @param highestPriorityOnly states whether to include all infos or only the ones with the highest HighlightSeverity
   * @param minSeverity the minimum HighlightSeverity starting from which infos are considered for collection
   */
  @Nullable
  public HighlightInfo findHighlightsByOffset(@NotNull Document document,
                                              int offset,
                                              boolean includeFixRange,
                                              boolean highestPriorityOnly,
                                              @NotNull HighlightSeverity minSeverity) {
    HighlightByOffsetProcessor processor = new HighlightByOffsetProcessor(highestPriorityOnly);
    processHighlightsNearOffset(document, myProject, minSeverity, offset, includeFixRange, processor);
    return processor.getResult();
  }

  static class HighlightByOffsetProcessor implements Processor<HighlightInfo> {
    private final List<HighlightInfo> foundInfoList = new SmartList<>();
    private final boolean highestPriorityOnly;

    HighlightByOffsetProcessor(boolean highestPriorityOnly) {
      this.highestPriorityOnly = highestPriorityOnly;
    }

    @Override
    public boolean process(HighlightInfo info) {
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

  private static boolean isOffsetInsideHighlightInfo(int offset, @NotNull HighlightInfo info, boolean includeFixRange) {
    RangeHighlighterEx highlighter = info.getHighlighter();
    if (highlighter == null || !highlighter.isValid()) return false;
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (startOffset <= offset && offset <= endOffset) {
      return true;
    }
    if (!includeFixRange) return false;
    RangeMarker fixMarker = info.fixMarker;
    if (fixMarker != null) {  // null means its range is the same as highlighter
      if (!fixMarker.isValid()) return false;
      startOffset = fixMarker.getStartOffset();
      endOffset = fixMarker.getEndOffset();
      return startOffset <= offset && offset <= endOffset;
    }
    return false;
  }

  @NotNull
  public static List<LineMarkerInfo<?>> getLineMarkers(@NotNull Document document, @NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<LineMarkerInfo<?>> result = new ArrayList<>();
    LineMarkersUtil.processLineMarkers(project, document, new TextRange(0, document.getTextLength()), -1,
                                       new CommonProcessors.CollectProcessor<>(result));
    return result;
  }

  @Nullable
  IntentionHintComponent getLastIntentionHint() {
    return ((IntentionsUIImpl)IntentionsUI.getInstance(myProject)).getLastIntentionHint();
  }

  @NotNull
  @Override
  public Element getState() {
    Element state = new Element("state");
    if (myDisabledHintsFiles.isEmpty()) {
      return state;
    }

    List<String> array = new SmartList<>();
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
      ApplicationManager.getApplication().assertIsDispatchThread();
      Project project = myProject;
      DaemonCodeAnalyzerImpl dca;
      if (project == null ||
          project.isDefault() ||
          !project.isInitialized() ||
          project.isDisposed() ||
          PowerSaveMode.isEnabled() ||
          LightEdit.owns(project) ||
          (dca = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project)).myDisposed) {
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

      Collection<FileEditor> activeEditors = dca.getSelectedEditors();
      boolean updateByTimerEnabled = dca.isUpdateByTimerEnabled();
      if (PassExecutorService.LOG.isDebugEnabled()) {
        PassExecutorService.log(dca.getUpdateProgress(), null, "Update Runnable. myUpdateByTimerEnabled:",
                                updateByTimerEnabled, " something disposed:",
                                PowerSaveMode.isEnabled() || !myProject.isInitialized(), " activeEditors:", activeEditors);
      }
      if (!updateByTimerEnabled) return;

      if (activeEditors.isEmpty()) return;

      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        // makes no sense to start from within write action, will cancel anyway
        // we'll restart when the write action finish
        return;
      }
      if (dca.myPsiDocumentManager.hasEventSystemEnabledUncommittedDocuments()) {
        // restart when everything committed
        dca.myPsiDocumentManager.performLaterWhenAllCommitted(this);
        return;
      }

      Map<FileEditor, HighlightingPass[]> passes = new HashMap<>(activeEditors.size());
      for (FileEditor fileEditor : activeEditors) {
        BackgroundEditorHighlighter highlighter = fileEditor.getBackgroundHighlighter();
        if (highlighter != null) {
          HighlightingPass[] highlightingPasses = highlighter.createPassesForEditor();
          passes.put(fileEditor, highlightingPasses);
        }
      }

      // wait for heavy processing to stop, re-schedule daemon but not too soon
      if (postponeBecauseOfHeavyProcess()) {
        boolean hasPasses = false;
        for (Map.Entry<FileEditor, HighlightingPass[]> entry : passes.entrySet()) {
          HighlightingPass[] dumbAwarePasses = Arrays.stream(entry.getValue()).filter(DumbService::isDumbAware).toArray(HighlightingPass[]::new);
          entry.setValue(dumbAwarePasses);
          hasPasses |= dumbAwarePasses.length != 0;
        }
        if (!hasPasses) {
          // will be re-scheduled by HeavyLatch listener in DaemonListeners
          return;
        }
      }

      // cancel all after calling createPasses() since there are perverts {@link com.intellij.util.xml.ui.DomUIFactoryImpl} who are changing PSI there
      dca.cancelUpdateProgress(true, "Cancel by alarm");
      DaemonProgressIndicator progress = dca.createUpdateProgress(passes.keySet());
      dca.myPassExecutorService.submitPasses(passes, progress);
    }

    private void clearFieldsOnDispose() {
      myProject = null;
    }
  }

  // return true if a heavy op is running
  private static boolean postponeBecauseOfHeavyProcess() {
    if (DumbServiceImpl.ALWAYS_SMART) return false;
    // VFS refresh is OK
    return HeavyProcessLatch.INSTANCE.isRunningAnythingBut(HeavyProcessLatch.Type.Syncing);
  }

  @NotNull
  private synchronized DaemonProgressIndicator createUpdateProgress(@NotNull Collection<? extends FileEditor> fileEditors) {
    DaemonProgressIndicator old = myUpdateProgress;
    if (!old.isCanceled()) {
      old.cancel();
    }
    DaemonProgressIndicator progress = new MyDaemonProgressIndicator(myProject, fileEditors);
    progress.setModalityProgress(null);
    progress.start();
    myProject.getMessageBus().syncPublisher(DAEMON_EVENT_TOPIC).daemonStarting(fileEditors);
    if (isRestartToCompleteEssentialHighlightingRequested()) {
      progress.putUserData(COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY, true);
    }
    myUpdateProgress = progress;
    return progress;
  }

  private static final class MyDaemonProgressIndicator extends DaemonProgressIndicator {
    private final Project myProject;
    private final Collection<? extends FileEditor> myFileEditors;

    MyDaemonProgressIndicator(@NotNull Project project, @NotNull Collection<? extends FileEditor> fileEditors) {
      myFileEditors = ContainerUtil.createConcurrentList(fileEditors);
      myProject = project;
    }

    @Override
    boolean stopIfRunning() {
      boolean wasStopped = super.stopIfRunning();
      if (wasStopped) {
        myProject.getMessageBus().syncPublisher(DAEMON_EVENT_TOPIC).daemonFinished(myFileEditors);
        myFileEditors.clear();
        HighlightingSessionImpl.clearProgressIndicator(this);
        ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject)).completeEssentialHighlightingRequested = false;
      }
      return wasStopped;
    }
  }

  @Override
  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    assertMyProject(psiFile.getProject());
    for (ReferenceImporter importer : ReferenceImporter.EP_NAME.getExtensionList()) {
      if (importer.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) && importer.autoImportReferenceAtCursor(editor, psiFile)) break;
    }
  }

  @TestOnly
  @NotNull
  synchronized DaemonProgressIndicator getUpdateProgress() {
    return myUpdateProgress;
  }

  @NotNull
  private Collection<FileEditor> getSelectedEditors() {
    Application app = ApplicationManager.getApplication();
    app.assertIsDispatchThread();

    // editors in modal context
    List<? extends Editor> editors = myEditorTracker.getActiveEditors();
    Collection<FileEditor> activeTextEditors;
    if (editors.isEmpty()) {
      activeTextEditors = Collections.emptyList();
    }
    else {
      activeTextEditors = new HashSet<>(editors.size());
      for (Editor editor : editors) {
        if (editor.isDisposed()) continue;
        TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        activeTextEditors.add(textEditor);
      }
    }

    if (app.getCurrentModalityState() != ModalityState.NON_MODAL) {
      return activeTextEditors;
    }

    Collection<FileEditor> result = new HashSet<>(activeTextEditors.size());
    Collection<VirtualFile> files = new HashSet<>(activeTextEditors.size());
    if (!app.isUnitTestMode() && !myProject.isDefault()) {
      // editors in tabs
      for (FileEditor tabEditor : myFileEditorManager.getSelectedEditorWithRemotes()) {
        if (!tabEditor.isValid()) continue;
        VirtualFile file = tabEditor.getFile();
        if (file != null) {
          files.add(file);
        }
        result.add(tabEditor);
      }
    }

    // do not duplicate documents
    if (!activeTextEditors.isEmpty()) {
      for (FileEditor fileEditor : activeTextEditors) {
        VirtualFile file = fileEditor.getFile();
        if (file != null && files.contains(file)) {
          continue;
        }
        result.add(fileEditor);
      }
    }
    return result;
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
    ApplicationManager.getApplication().assertIsDispatchThread();
    setUpdateByTimerEnabled(false);
    try {
      cancelUpdateProgress(false, "serializeCodeInsightPasses");
      myPassExecutorService.cancelAll(true);

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
