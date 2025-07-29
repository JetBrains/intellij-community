// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.multiverse.*;
import com.intellij.codeInsight.quickfix.LazyQuickFixUpdater;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.JobLauncher;
import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.notebook.editor.BackedVirtualFileProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.application.impl.TestOnlyThreading;
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
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiConsistencyAssertions;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.EDT;
import io.opentelemetry.context.Context;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@State(name = "DaemonCodeAnalyzer", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
@ApiStatus.Internal
public final class DaemonCodeAnalyzerImpl extends DaemonCodeAnalyzerEx
  implements PersistentStateComponent<Element>, Disposable, DaemonCodeAnalysisStatus {

  private static final Logger LOG = Logger.getInstance(DaemonCodeAnalyzerImpl.class);

  private static final Key<List<HighlightInfo>> FILE_LEVEL_HIGHLIGHTS = Key.create("FILE_LEVEL_HIGHLIGHTS");
  private static final @NotNull Key<Boolean> COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY = Key.create("COMPLETE_ESSENTIAL_HIGHLIGHTING");
  @NotNull
  private final Project myProject;
  private final DaemonCodeAnalyzerSettings mySettings;
  private final DaemonListeners myListeners;
  private PsiDocumentManager psiDocumentManager;
  private FileEditorManager fileEditorManager;
  private final Map<FileEditor, DaemonProgressIndicator> myUpdateProgress = new HashMap<>(); // guarded by `this` lock

  private final UpdateRunnable myUpdateRunnable;
  private volatile @NotNull Future<?> myUpdateRunnableFuture = CompletableFuture.completedFuture(null);
  private boolean myUpdateByTimerEnabled = true; // guarded by this
  private final Collection<VirtualFile> myDisabledHintsFiles = new HashSet<>();
  private final Collection<VirtualFile> myDisabledHighlightingFiles = new HashSet<>();

  private final FileStatusMap myFileStatusMap;

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

  private final DaemonCodeAnalyzerRepaintIconHelper repaintIconHelper;

  public DaemonCodeAnalyzerImpl(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    // DependencyValidationManagerImpl adds scope listener, so we need to force service creation
    DependencyValidationManager.getInstance(project);

    myProject = project;
    mySettings = DaemonCodeAnalyzerSettings.getInstance();

    myFileStatusMap = new FileStatusMap(project);
    myPassExecutorService = new PassExecutorService(project);
    Disposer.register(this, myPassExecutorService);
    Disposer.register(this, myFileStatusMap);
    //noinspection TestOnlyProblems
    DaemonProgressIndicator.setDebug(LOG.isDebugEnabled());

    myDisposed = false;
    myFileStatusMap.markAllFilesDirty("DaemonCodeAnalyzer init");
    myUpdateRunnable = new UpdateRunnable(this);
    Disposer.register(this, () -> {
      assert !myDisposed : "Double dispose";
      myUpdateRunnable.clearFieldsOnDispose();

      stopProcess(false, "Dispose "+project);

      myDisposed = true;
    });
    myDaemonListenerPublisher = project.getMessageBus().syncPublisher(DAEMON_EVENT_TOPIC);
    myListeners = new DaemonListeners(project, this);
    Disposer.register(this, myListeners);
    repaintIconHelper = new DaemonCodeAnalyzerRepaintIconHelper(coroutineScope);
  }

  private @NotNull FileEditorManager getFileEditorManager() {
    FileEditorManager result = fileEditorManager;
    if (result == null) {
      result = FileEditorManager.getInstance(myProject);
      fileEditorManager = result;
    }
    return result;
  }

  private @NotNull PsiDocumentManager getPsiDocumentManager() {
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
    updateRequests.set(0);
  }

  synchronized void clearProgressIndicator() {
    myUpdateProgress.values().forEach(HighlightingSessionImpl::clearAllHighlightingSessions);
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
  public @NotNull List<HighlightInfo> getFileLevelHighlights(@NotNull Project project, @NotNull PsiFile psiFile) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    assertFileFromMyProject(project, psiFile);
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    List<HighlightInfo> list = new ArrayList<>();
    for (FileEditor fileEditor : getFileEditorManager().getAllEditorList(vFile)) {
      List<HighlightInfo> data = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (data != null) {
        list.addAll(data);
      }
    }
    return list;
  }

  private void assertFileFromMyProject(@NotNull Project project, @NotNull PsiFile psiFile) {
    if (project != myProject) {
      throw new IllegalStateException("my project is " + myProject + " but I was called with " + project);
    }
    if (psiFile.getProject() != myProject) {
      throw new IllegalStateException("my project is " + myProject + " but I was called with file " + psiFile + " from " + psiFile.getProject());
    }
  }

  @Override
  public void cleanFileLevelHighlights(int group, @NotNull PsiFile psiFile) {
    ThreadingAssertions.assertEventDispatchThread();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditorList(vFile)) {
      cleanFileLevelHighlights(fileEditor, group);
    }
  }

  @Override
  public boolean hasFileLevelHighlights(int group, @NotNull PsiFile psiFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditorList(vFile)) {
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
        disposeFileLevelInfo(fileEditor, info);
        infosToRemove.add(info);
      }
    }
    infos.removeAll(infosToRemove);
    if (LOG.isDebugEnabled()) {
      LOG.debug("cleanFileLevelHighlights group:" +group+ "; infosToRemove:" + infosToRemove);
    }
  }

  @Override
  @ApiStatus.Internal
  public void removeFileLevelHighlight(@NotNull PsiFile psiFile, @NotNull HighlightInfo info) {
    ThreadingAssertions.assertEventDispatchThread();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    for (FileEditor fileEditor : getFileEditorManager().getAllEditorList(vFile)) {
      List<HighlightInfo> infos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
      if (infos == null) {
        continue;
      }
      infos.removeIf(fileLevelInfo-> {
        if (!info.attributesEqual(fileLevelInfo)) {
          return false;
        }
        disposeFileLevelInfo(fileEditor, fileLevelInfo);
        return true;
      });
      if (LOG.isDebugEnabled()) {
        LOG.debug("removeFileLevelHighlight [" + info + "]: fileLevelInfos:" + infos);
      }
    }
  }

  private void disposeFileLevelInfo(@NotNull FileEditor fileEditor, @NotNull HighlightInfo info) {
    ThreadingAssertions.assertEventDispatchThread();
    JComponent component = info.getFileLevelComponent(fileEditor);
    if (component != null) {
      getFileEditorManager().removeTopComponent(fileEditor, component);
      info.removeFileLeverComponent(fileEditor);
    }
    RangeHighlighterEx highlighter = info.getHighlighter();
    if (highlighter != null) {
      highlighter.dispose();
    }
  }

  @Override
  public void addFileLevelHighlight(int group, @NotNull HighlightInfo info, @NotNull PsiFile psiFile, @Nullable RangeHighlighter toReuse) {
    ThreadingAssertions.assertEventDispatchThread();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    FileEditorManager fileEditorManager = getFileEditorManager();
    for (FileEditor fileEditor : fileEditorManager.getAllEditorList(vFile)) {
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
          RangeHighlighter highlighter = toReuse != null && toReuse.isValid() ? toReuse
                                         : markupModel.addRangeHighlighter(0, document.getTextLength(), FILE_LEVEL_FAKE_LAYER, null, HighlighterTargetArea.EXACT_RANGE);
          highlighter.setGreedyToLeft(true);
          highlighter.setGreedyToRight(true);
          highlighter.setErrorStripeTooltip(info);
          // for the condition `existing.equalsByActualOffset(info)` above work correctly,
          // create a fake whole-file highlighter which will track the document size changes
          // and which will make possible to calculate correct `info.getActualEndOffset()`
          if (toReuse == null) {
            // assign only newly created highlighter here; otherwise the reused highlighter was already set, no need (and can't) to overwrite
            info.setHighlighter((RangeHighlighterEx)highlighter);
          }
          info.setGroup(group);
          fileLevelInfos.add(info);
          FileLevelIntentionComponent component = new FileLevelIntentionComponent(info.getDescription(), info.getSeverity(),
                                                                                  info.getGutterIconRenderer(), actionRanges,
                                                                                  psiFile, textEditor.getEditor(), info.getToolTip());
          fileEditorManager.addTopComponent(fileEditor, component);
          info.addFileLevelComponent(fileEditor, component);
          if (LOG.isDebugEnabled()) {
            LOG.debug("addFileLevelHighlight [" + info + "]: fileLevelInfos:" + fileLevelInfos);
          }
        }
      }
    }
  }

  // replace one file-level info with the other, possible reusing the fake highlighter
  @Override
  public void replaceFileLevelHighlight(@NotNull HighlightInfo oldInfo,
                                        @NotNull HighlightInfo newInfo,
                                        @NotNull PsiFile psiFile,
                                        @Nullable RangeHighlighter toReuse) {
    ThreadingAssertions.assertEventDispatchThread();
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = BackedVirtualFile.getOriginFileIfBacked(psiFile.getViewProvider().getVirtualFile());
    FileEditorManager fileEditorManager = getFileEditorManager();
    for (FileEditor fileEditor : fileEditorManager.getAllEditorList(vFile)) {
      if (fileEditor instanceof TextEditor textEditor) {
        List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> actionRanges = new ArrayList<>();
        newInfo.findRegisteredQuickFix((descriptor, range) -> {
          actionRanges.add(Pair.create(descriptor, range));
          return null;
        });
        List<HighlightInfo> fileLevelInfos = fileEditor.getUserData(FILE_LEVEL_HIGHLIGHTS);
        if (fileLevelInfos == null) {
          fileLevelInfos = ContainerUtil.createConcurrentList(); // must be able to iterate in hasFileLevelHighlights() and concurrently modify in addFileLevelHighlight()
          fileEditor.putUserData(FILE_LEVEL_HIGHLIGHTS, fileLevelInfos);
        }
        // do not dispose highlighter if it needs to be reused
        fileLevelInfos.removeIf(fileLevelInfo-> {
          if (!fileLevelInfo.attributesEqual(fileLevelInfo)) {
            return false;
          }
          ThreadingAssertions.assertEventDispatchThread();
          JComponent component = fileLevelInfo.getFileLevelComponent(fileEditor);
          if (component != null) {
            fileEditorManager.removeTopComponent(fileEditor, component);
            fileLevelInfo.removeFileLeverComponent(fileEditor);
          }
          RangeHighlighterEx highlighter = fileLevelInfo.getHighlighter();
          if (highlighter != null && highlighter != toReuse) {
            highlighter.dispose();
          }
          return true;
        });
        Document document = textEditor.getEditor().getDocument();
        MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
        if (toReuse == null) {
          HighlightInfoUpdaterImpl.createOrReuseFakeFileLevelHighlighter(newInfo.getGroup(), newInfo, null, markupModel);
        }
        fileLevelInfos.add(newInfo);
        FileLevelIntentionComponent component = new FileLevelIntentionComponent(newInfo.getDescription(), newInfo.getSeverity(),
                                                                                newInfo.getGutterIconRenderer(), actionRanges,
                                                                                psiFile, textEditor.getEditor(), newInfo.getToolTip());
        fileEditorManager.addTopComponent(fileEditor, component);
        newInfo.addFileLevelComponent(fileEditor, component);
        if (LOG.isDebugEnabled()) {
          LOG.debug("replaceFileLevelHighlight [" + newInfo + "]: fileLevelInfos:" + fileLevelInfos);
        }
      }
    }
  }

  @Override
  public boolean cutOperationJustHappened() {
    return myListeners.cutOperationJustHappened;
  }

  @Override
  public boolean isEscapeJustPressed() {
    return myListeners.isEscapeJustPressed();
  }

  @Override
  protected void progressIsAdvanced(@NotNull HighlightingSession session, Editor editor, double progress) {
    if (editor != null) {
      repaintIconHelper.repaintTrafficIcon(session.getPsiFile(), editor, progress);
    }
  }

  static void repaintErrorStripeAndIcon(@NotNull Editor editor, @NotNull Project project, @Nullable PsiFile psiFile) {
    MarkupModel markup = editor.getMarkupModel();
    if (markup instanceof EditorMarkupModelImpl editorMarkup) {
      editorMarkup.repaintTrafficLightIcon();
      ErrorStripeUpdateManager.getInstance(project).launchRepaintErrorStripePanel(editor, psiFile);
    }
  }

  @Override
  public @NotNull List<HighlightInfo> runMainPasses(@NotNull PsiFile psiFile, @NotNull Document document, @NotNull ProgressIndicator progress) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    assertFileFromMyProject(psiFile.getProject(), psiFile);

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
          if (LOG.isDebugEnabled()) {
            LOG.debug("Canceled: " + progress);
          }
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
    assert ApplicationManager.getApplication().isUnitTestMode();
    boolean old = mustWaitForSmartMode;
    mustWaitForSmartMode = mustWait;
    Disposer.register(parent, () -> mustWaitForSmartMode = old);
  }

  /**
   * do not run in production since it differs slightly from the {@link #runUpdate()}
   */
  @TestOnly
  @ApiStatus.Internal
  public void runPasses(@NotNull PsiFile psiFile,
                        @NotNull Document document,
                        @NotNull TextEditor textEditor,
                        int @NotNull [] passesToIgnore,
                        boolean canChangeDocument,
                        @Nullable Runnable callbackWhileWaiting) throws Exception {
    ThreadingAssertions.assertEventDispatchThread();
    assert !myDisposed;
    PsiUtilCore.ensureValid(psiFile);
    assertFileFromMyProject(psiFile.getProject(), psiFile);
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
      TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
        EDT.dispatchAllInvocationEvents();
        return Unit.INSTANCE;
      });
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

    clearReferences();

    // previous passes can be canceled but still in flight. wait for them to avoid interference
    myPassExecutorService.cancelAll(false, "DaemonCodeAnalyzerImpl.runPasses");

    CodeInsightContext context = FileViewProviderUtil.getCodeInsightContext(psiFile); // todo IJPL-339 ???

    waitForUpdateFileStatusBackgroundQueueInTests(); // update the file status map before prohibiting its modifications
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    PsiConsistencyAssertions.assertNoFileTextMismatch(psiFile, editor.getDocument(), null);
    FileStatusMap fileStatusMap = getFileStatusMap();
    fileStatusMap.runAllowingDirt(canChangeDocument, () -> {
      for (int ignoreId : passesToIgnore) {
        fileStatusMap.markFileUpToDate(document, context, ignoreId, null);
      }
      ThrowableRunnable<Exception> doRunPasses = () -> doRunPasses(textEditor, passesToIgnore, canChangeDocument, callbackWhileWaiting);
      if (isDebugMode) {
        DaemonProgressIndicator.runInDebugMode(doRunPasses);
      }
      else {
        doRunPasses.run();
      }
    });
  }

  @TestOnly
  private void doRunPasses(@NotNull TextEditor textEditor,
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
      HighlightingSession session = queuePassesCreation(textEditor, virtualFile, passesToIgnore, new ConcurrentHashMap<>());
      if (session == null) {
        LOG.error("Can't create session for " + textEditor + " (" + textEditor.getClass() + ")," +
                  " fileEditor.getBackgroundHighlighter()=" + textEditor.getBackgroundHighlighter() +
                  "; virtualFile=" + virtualFile);
        throw new ProcessCanceledException();
      }
      ProgressIndicator progress = session.getProgressIndicator();
      // there can be PCE in FJP during queuePassesCreation; "no PCE" guarantees that session is not null
      progress.checkCanceled();
      try {
        long start = System.currentTimeMillis();
        waitInOtherThread(600_000, canChangeDocument, () -> {
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
          TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack(() -> {
            EDT.dispatchAllInvocationEvents();
            return Unit.INSTANCE;
          });
          progress.checkCanceled();
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
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();//auto-imports use non-blocking read actions
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        assert progress.isCanceled();
      }
      catch (Throwable e) {
        Throwable unwrapped = ExceptionUtilRt.unwrapException(e, ExecutionException.class);
        if (LOG.isDebugEnabled()) {
          LOG.debug("doRunPasses() thrown " + ExceptionUtil.getThrowableText(unwrapped));
        }
        if (unwrapped instanceof ProcessCanceledException) {
          Throwable savedException = ((DaemonProgressIndicator)progress).getCancellationTrace();
          if (savedException != null) {
            if (DaemonProgressIndicator.CANCEL_WAS_CALLED_REASON.equals(savedException.getMessage())) {
              throw (ProcessCanceledException)unwrapped;
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
      Reference.reachabilityFence(psiFile); // PsiFile must be cached, in order to start the highlighting
      Reference.reachabilityFence(fileNode); // perf: keep AST from gc
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
      myProject.getMessageBus().connect(disposable).subscribe(DAEMON_EVENT_TOPIC, new DaemonListener() {
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
  public void prepareForTest() throws InterruptedException, ExecutionException {
    assert ApplicationManager.getApplication().isUnitTestMode();
    setUpdateByTimerEnabled(false);
    waitForTermination();
    clearReferences();
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
    AppExecutorUtil.getAppExecutorService().submit(() -> {
      // wait outside EDT to avoid stealing work from FJP
      myPassExecutorService.cancelAll(true, "DaemonCodeAnalyzerImpl.waitForTermination");
    }).get();
  }

  @Override
  public void settingsChanged() {
    //noinspection SpellCheckingInspection
    restart("DCAI.settingsChanged");
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
  public void setImportHintsEnabled(@NotNull PsiFile psiFile, boolean value) {
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    VirtualFile vFile = psiFile.getVirtualFile();
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
    assertFileFromMyProject(psiFile.getProject(), psiFile);

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
    if (!psiFile.isPhysical()) {
      return false;
    }
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    if (myDisabledHighlightingFiles.contains(PsiUtilCore.getVirtualFile(psiFile))) {
      return false;
    }

    if (psiFile instanceof PsiCompiledElement) {
      return false;
    }
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
    restart("Global restart");
  }

  @Override
  public void restart(@NotNull Object reason) {
    myFileStatusMap.markAllFilesDirty(reason);
    stopProcess(true, reason.toString());
  }

  @Override
  public void restart(@NotNull PsiFile psiFile) {
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    if (document == null) {
      return;
    }
    String reason = "Psi file restart: " + psiFile.getName();
    myFileStatusMap.markWholeFileScopeDirty(document, reason);
    stopProcess(true, reason);
  }

  public @NotNull List<ProgressableTextEditorHighlightingPass> getPassesToShowProgressFor(@NotNull Document document) {
    List<HighlightingPass> allPasses = myPassExecutorService.getAllSubmittedPasses();
    List<ProgressableTextEditorHighlightingPass> list = new ArrayList<>(allPasses.size());
    for (HighlightingPass allPass : allPasses) {
      ProgressableTextEditorHighlightingPass pass = allPass instanceof ProgressableTextEditorHighlightingPass pPass ? pPass : null;
      if (pass != null && pass.getDocument() == document) {
        list.add(pass);
      }
    }
    list.sort(Comparator.comparingInt(p -> p.getId()));
    return list;
  }

  /**
   * Used in tests, don't remove VisibleForTesting
   */
  @Override
  @ApiStatus.Internal
  @VisibleForTesting
  public boolean isAllAnalysisFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) {
      return false;
    }
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    CodeInsightContext context = FileViewProviderUtil.getCodeInsightContext(psiFile);
    return document != null &&
           PsiDocumentManager.getInstance(myProject).isCommitted(document) &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.allDirtyScopesAreNull(document, context);
  }

  @Override
  public boolean isErrorAnalyzingFinished(@NotNull PsiFile psiFile) {
    if (myDisposed) {
      return false;
    }
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    CodeInsightContext context = FileViewProviderUtil.getCodeInsightContext(psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    return document != null &&
           document.getModificationStamp() == psiFile.getViewProvider().getModificationStamp() &&
           myFileStatusMap.getFileDirtyScope(document, context, psiFile, Pass.UPDATE_ALL) == null;
  }

  @Override
  public @NotNull FileStatusMap getFileStatusMap() {
    return myFileStatusMap;
  }

  @Override
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
  @RequiresEdt
  public boolean isRunningOrPending() {
    ThreadingAssertions.assertEventDispatchThread();
    return isRunning() || !myUpdateRunnableFuture.isDone() || GeneralHighlightingPass.isRestartPending();
  }

  synchronized void stopProcess(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    cancelAllUpdateProgresses(toRestartAlarm, reason);
    boolean restart = toRestartAlarm && !myDisposed;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Stopping process: toRestart:", toRestartAlarm, "; myDisposed:", myDisposed, "; reason: '", reason, "'");
    }
    if (restart) {
      scheduleIfNotRunning();
    }
  }

  synchronized void stopAndRestartMyProcess(@NotNull DaemonProgressIndicator indicator,
                                            @Nullable Throwable cause,
                                            @NotNull @NonNls String reason) {
    cancelIndicator(indicator, true, cause, reason);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Stopping my process: "+indicator+". reason: '", reason, "'; myDisposed:", myDisposed);
    }
    if (!myDisposed) {
      scheduleIfNotRunning();
    }
  }

  private final AtomicLong updateRequests = new AtomicLong(); // LSB: number of queued requests to run update; MSB: number of executed updates
  // number of queued requests (calls to scheduleIfNotRunning)
  private static long queuedRequests(long u) {
    return u & 0x0000_0000_ffff_ffffL;
  }
  // number of executed requests (runUpdate() executions)
  private static long executedRequests(long u) {
    return (u >> 32) & 0x0000_0000_ffff_ffffL;
  }
  // return true if queued requests became greater than executed requests, and we need to schedule another update
  private boolean incrementQueuedRequests() {
    long u = updateRequests.getAndUpdate(l -> (executedRequests(l) << 32) | ((queuedRequests(l) + 1) & 0x0000_0000_ffff_ffffL));
    return queuedRequests(u) == executedRequests(u);
  }
  // increase executed requests by delta and return new delta between written queued requests and executed requests (always >= 0)
  private long incrementExecutedRequests(long delta) {
    assert delta >= 0 : delta;
    long u = updateRequests.updateAndGet(l -> (((executedRequests(l) + delta) & 0x0000_0000_ffff_ffffL) << 32)  | queuedRequests(l));
    long newDelta = queuedRequests(u) - executedRequests(u);
    assert newDelta >= 0: Long.toHexString(u);
    return newDelta;
  }

  // number of queued requests not yet executed
  private long getDelta() {
    long u = updateRequests.get();
    long diff = (queuedRequests(u) - executedRequests(u) + 0x1_0000_0000L/*Assume not more than one wraparound*/) & 0x0000_0000_ffff_ffffL;
    assert diff >= 0 : diff + ":" + Long.toHexString(u);
    return diff;
  }

  /**
   * reset {@link #myScheduledUpdateTimestamp} always, but re-schedule {@link #myUpdateRunnable} only rarely because of thread scheduling overhead
   */
  private synchronized void scheduleIfNotRunning() {
    long autoReparseDelayNanos = TimeUnit.MILLISECONDS.toNanos(mySettings.getAutoReparseDelay());
    myScheduledUpdateTimestamp = System.nanoTime() + autoReparseDelayNanos;
    // optimisation: this check is to avoid too many re-schedules in case of thousands of event spikes
    boolean isDone = myUpdateRunnableFuture.isDone();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Rescheduling highlighting: isDone: ", isDone);
    }
    if (incrementQueuedRequests()) {
      scheduleUpdateRunnable(autoReparseDelayNanos);
    }
  }

  private synchronized void scheduleUpdateRunnable(long delayNanos) {
    Future<?> oldFuture = myUpdateRunnableFuture;
    if (LOG.isTraceEnabled()) {
      LOG.trace("scheduleUpdateRunnable("+TimeUnit.NANOSECONDS.toMillis(delayNanos)+"ms); oldFuture="+oldFuture+"; isDone="+oldFuture.isDone());
    }
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return;
    }
    if (oldFuture.isDone()) {
      // schedule `manifest` into a separate call to avoid breaking the current stack with an exception from the previous execution
      ApplicationManager.getApplication().invokeLater(() -> ConcurrencyUtil.manifestExceptionsIn(oldFuture));
    }
    else {
      oldFuture.cancel(false); // do not have too many requests sit in the EdtExecutorService queue
    }
    myUpdateRunnableFuture = EdtExecutorService.getScheduledExecutorInstance().schedule(myUpdateRunnable, delayNanos, TimeUnit.NANOSECONDS);
  }

  // return true if the progress really was canceled
  synchronized void cancelAllUpdateProgresses(boolean toRestartAlarm, @NotNull @NonNls String reason) {
    if (myDisposed || myProject.isDisposed() || myProject.getMessageBus().isDisposed()) {
      return;
    }
    for (Map.Entry<FileEditor, DaemonProgressIndicator> entry : new ArrayList<>(myUpdateProgress.entrySet())) {
      DaemonProgressIndicator updateProgress = entry.getValue();
      cancelIndicator(updateProgress, toRestartAlarm, null, reason);
    }
    myUpdateProgress.clear();
    myPassExecutorService.cancelAll(false, reason);
    daemonCancelEventCount.incrementAndGet();
  }

  private static void cancelIndicator(@NotNull DaemonProgressIndicator indicator,
                                      boolean toRestartAlarm,
                                      @Nullable Throwable cause,
                                      @NonNls @NotNull String reason) {
    if (!indicator.isCanceled()) {
      PassExecutorService.log(indicator, null, "Cancel (reason:", reason, ")", toRestartAlarm);
      if (cause == null) {
        indicator.cancel(reason);
      }
      else {
        indicator.cancel(cause, reason);
      }
    }
  }

  private synchronized void removeIndicatorFromMap(@NotNull FileEditor fileEditor, @NotNull DaemonProgressIndicator indicator) {
    myUpdateProgress.remove(fileEditor, indicator);
  }

  static boolean processHighlightsNearOffset(@NotNull Document document,
                                             @NotNull Project project,
                                             @NotNull HighlightSeverity minSeverity,
                                             int offset,
                                             boolean includeFixRange,
                                             @NotNull Processor<? super HighlightInfo> processor) {
    return processHighlights(document, project, null, 0, document.getTextLength(), info -> {
      if (!info.containsOffset(offset, includeFixRange)) {
        return true;
      }

      int compare = info.getSeverity().compareTo(minSeverity);
      return compare < 0 || processor.process(info);
    });
  }

  public @Nullable HighlightInfo findHighlightByOffset(@NotNull Document document,
                                                       int offset,
                                                       boolean includeFixRange,
                                                       @NotNull CodeInsightContext context) {
    return findHighlightByOffset(document, offset, includeFixRange, HighlightSeverity.INFORMATION, context);
  }

  @Nullable
  HighlightInfo findHighlightByOffset(@NotNull Document document,
                                      int offset,
                                      boolean includeFixRange,
                                      @NotNull HighlightSeverity minSeverity,
                                      @NotNull CodeInsightContext context) {
    return findHighlightsByOffset(document, offset, includeFixRange, true, minSeverity, true, context);
  }


  /*
  *  todo IJPL-339 deprecate findHighlightByOffset when multiverse gets more mature
  *  @deprecated This method is deprecated because it does not support contexts.
  *             Use {@link #findHighlightByOffset(Document, int, boolean, CodeInsightContext)} instead.
  */

  /**
   * Collects HighlightInfo intersecting with a certain offset.
   */
  public @Nullable HighlightInfo findHighlightByOffset(@NotNull Document document,
                                                       int offset,
                                                       boolean includeFixRange) {
    return findHighlightByOffset(document, offset, includeFixRange, HighlightSeverity.INFORMATION, CodeInsightContexts.anyContext());
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
    return findHighlightsByOffset(document, offset, includeFixRange, highestPriorityOnly, minSeverity, true,
                                  CodeInsightContexts.anyContext());
  }

  @ApiStatus.Internal
  public @Nullable HighlightInfo findHighlightsByOffset(@NotNull Document document,
                                                        int offset,
                                                        boolean includeFixRange,
                                                        boolean highestPriorityOnly,
                                                        @NotNull HighlightSeverity minSeverity,
                                                        boolean includeFileLevel,
                                                        @NotNull CodeInsightContext context) {
    HighlightByOffsetProcessor processor = new HighlightByOffsetProcessor(highestPriorityOnly, includeFileLevel, context);
    processHighlightsNearOffset(document, myProject, minSeverity, offset, includeFixRange, processor);
    return processor.getResult();
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public static void waitForLazyQuickFixesUnderCaret(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ThreadingAssertions.assertNoOwnReadAccess();
    List<HighlightInfo> relevantInfos = new ArrayList<>();
    Project project = psiFile.getProject();
    ReadAction.run(() -> {
      PsiUtilBase.assertEditorAndProjectConsistent(project, editor);
      CaretModel caretModel = editor.getCaretModel();
      int offset = caretModel.getOffset();
      Document document = editor.getDocument();
      int logicalLine = caretModel.getLogicalPosition().line;
      processHighlights(document, project, null, 0, document.getTextLength(), info -> {
        if (!info.hasLazyQuickFixes()) {
          return true;
        }
        if (info.containsOffset(offset, true)) {
          relevantInfos.add(info);
          return true;
        }
        // since we don't know fix ranges of potentially not-yet-added quick fixes, consider all HighlightInfos at the same line
        boolean atTheSameLine = editor.offsetToLogicalPosition(info.getActualStartOffset()).line <= logicalLine && logicalLine <= editor.offsetToLogicalPosition(info.getActualEndOffset()).line;
        if (atTheSameLine) {
          relevantInfos.add(info);
        }
        return true;
      });
    });
    for (HighlightInfo info : relevantInfos) {
      LazyQuickFixUpdater.getInstance(project).waitQuickFixesSynchronously(psiFile, editor, info);
    }
  }

  static final class HighlightByOffsetProcessor implements Processor<HighlightInfo> {
    private final List<HighlightInfo> foundInfoList = new SmartList<>();
    private final boolean highestPriorityOnly;
    private final boolean myIncludeFileLevel;
    private final @NotNull CodeInsightContext highlightingContext;

    HighlightByOffsetProcessor(boolean highestPriorityOnly, boolean includeFileLevel, @NotNull CodeInsightContext context) {
      this.highestPriorityOnly = highestPriorityOnly;
      myIncludeFileLevel = includeFileLevel;
      highlightingContext = context;
    }

    @Override
    public boolean process(@NotNull HighlightInfo info) {
      if (info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY
          || info.type == HighlightInfoType.TODO
          || !myIncludeFileLevel && info.isFileLevelAnnotation()
      ) {
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
      if (info.getHighlighter() != null && !CodeInsightContextHighlightingUtil.acceptRangeHighlighter(highlightingContext, info.getHighlighter())) {
        return true;
      }
      foundInfoList.add(info);
      return true;
    }

    @Nullable
    HighlightInfo getResult() {
      if (foundInfoList.isEmpty()) {
        return null;
      }
      if (foundInfoList.size() == 1) {
        return foundInfoList.get(0);
      }
      foundInfoList.sort(Comparator.comparing(HighlightInfo::getSeverity).reversed());
      return HighlightInfo.createComposite(foundInfoList);
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

  @VisibleForTesting
  public @Nullable IntentionHintComponent getLastIntentionHint() {
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
    private volatile DaemonCodeAnalyzerImpl myAnalyzer;
    private UpdateRunnable(@NotNull DaemonCodeAnalyzerImpl analyzer) {
      myAnalyzer = analyzer;
    }
    private void clearFieldsOnDispose() {
      myAnalyzer = null;
    }
    @Override
    public void run() {
      DaemonCodeAnalyzerImpl analyzer = myAnalyzer;
      if (analyzer == null) {
        return; // disposed
      }
      Project project = analyzer.myProject;
      // take a number of queued requests to update, and pretend we execute them all (they are all the same, so one is enough)
      long requestDelta = analyzer.getDelta();
      try {
        if (!project.isDefault() && project.isInitialized() && !LightEdit.owns(project)) {
          String result = analyzer.runUpdate();
          if (LOG.isDebugEnabled()) {
            LOG.debug("runUpdate result: " + result+"; requestDelta:"+requestDelta);
          }
        }
      }
      finally {
        long newDelta = analyzer.incrementExecutedRequests(requestDelta);
        if (newDelta != 0) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("runUpdate newDelta="+newDelta);
          }
          analyzer.scheduleUpdateRunnable(0);
        }
      }
    }
  }

  // return update outcome for debug
  private @NotNull @NonNls String runUpdate() {
    ThreadingAssertions.assertEventDispatchThread();
    synchronized (this) {
      long actualDelay = myScheduledUpdateTimestamp - System.nanoTime();
      if (actualDelay > 0) {
        // started too soon (there must've been some typings after we'd scheduled this; need to re-schedule)
        scheduleUpdateRunnable(actualDelay);
        return "wasn't run because called too soon: rescheduled in "+TimeUnit.NANOSECONDS.toMillis(actualDelay)+"ms";
      }
    }

    if (myDisposed) {
      return "wasn't run because i'm disposed";
    }
    if (PowerSaveMode.isEnabled()) {
      // to show the correct "power save" traffic light icon
      myListeners.repaintTrafficLightIconForAllEditors();
      return "wasn't run because power save mode was on";
    }

    Collection<? extends FileEditor> activeEditors = getSelectedEditors();
    boolean updateByTimerEnabled = isUpdateByTimerEnabled();
    if (!updateByTimerEnabled || activeEditors.isEmpty()) {
      return "wasn't run because updateByTimerEnabled="+updateByTimerEnabled+"; activeEditors: ("+activeEditors.size()+"): "+activeEditors;
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      // makes no sense to start from within write action - will cancel anyway
      // we'll restart when the write action finish
      return "wasn't run because inside write action";
    }
    PsiDocumentManager documentManager = getPsiDocumentManager();
    if (documentManager.hasEventSystemEnabledUncommittedDocuments()) {
      // restart when everything committed
      documentManager.performLaterWhenAllCommitted(() -> {
        synchronized (this) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Rescheduled after commit");
          }
          scheduleIfNotRunning();
        }
      });
      return "wasn't run because uncommitted docs found: "+Arrays.toString(documentManager.getUncommittedDocuments())+"; delayed until commit";
    }

    boolean submitted = false;
    ProcessCanceledException pce = null;
    // have to store created indicators because myUpdateProgress removes canceled indicator immediately
    List<ProgressIndicator> createdIndicators = new ArrayList<>();
    List<String> result = new SmartList<>();
    Map<Pair<Document, Class<? extends ProgressableTextEditorHighlightingPass>>, ProgressableTextEditorHighlightingPass> mainDocumentPasses = new ConcurrentHashMap<>();
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("runUpdate activeEditors: ("+activeEditors.size()+"): "+ContainerUtil.map(activeEditors, e->e+"("+e.getClass()+") for file "+e.getFile()));
      }
      for (FileEditor fileEditor : activeEditors) {
        if (fileEditor instanceof TextEditor textEditor && !textEditor.isEditorLoaded()) {
          // make sure the highlighting is restarted when the editor is finally loaded, because otherwise some crazy things happen,
          // for instance `FileEditor.getBackgroundHighlighter()` returning null, essentially stopping highlighting silently
          if (PassExecutorService.LOG.isDebugEnabled()) {
            PassExecutorService.log(null, null, "runUpdate for ", fileEditor, " rescheduled because the editor was not loaded yet");
          }
          result.add("didn't submit " + fileEditor + " because it's not loaded");
          // AsyncEditorLoader will restart
        }
        else {
          VirtualFile virtualFile = getVirtualFile(fileEditor);
          HighlightingSession session;
          if (virtualFile == null || !virtualFile.isValid()) {
            session = null;
          }
          else {
            session = queuePassesCreation(fileEditor, virtualFile, ArrayUtil.EMPTY_INT_ARRAY, mainDocumentPasses);
          }
          submitted |= session != null;
          if (session != null) {
            createdIndicators.add(session.getProgressIndicator());
          }
          result.add("submit fileEditor: "+fileEditor+" submitted="+submitted+(session==null? "" : " under "+session.getProgressIndicator()));
        }
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("runUpdate submitted activeEditors: ("+activeEditors.size()+"): "+ContainerUtil.map(activeEditors, e->e+"("+e.getClass()+") for file "+e.getFile())+"; indicators: "+createdIndicators);
      }
    }
    catch (ProcessCanceledException e) {
      pce = e;
      return "wasn't run because PCE was thrown:"+ExceptionUtil.getThrowableText(e);
    }
    finally {
      boolean wasCanceledDuringSubmit = ContainerUtil.exists(createdIndicators, p -> p.isCanceled());
      if (!submitted || wasCanceledDuringSubmit) {
        // happens e.g., when we are trying to open a directory and there's a FileEditor supporting this
        // invokeLater is required because we can't stop daemon from inside UpdateRunnable, since its future hasn't been scheduled yet
        // or when PCE happened in queuePassesCreation
        String reason = "Couldn't create session for " + activeEditors
                        + (pce == null ? "" : "; PCE was thrown: " + pce)
                        + (wasCanceledDuringSubmit ? "; was canceled during queuePassesCreation(): "+createdIndicators : "");
        ApplicationManager.getApplication().invokeLater(() -> stopProcess(false, reason), __->myDisposed);
      }
    }
    return StringUtil.join(result, "; ");
  }

  private static VirtualFile getVirtualFile(@NotNull FileEditor fileEditor) {
    VirtualFile virtualFile = fileEditor.getFile();
    VirtualFile replacedVirtualFile = BackedVirtualFileProvider.EP_NAME.computeSafeIfAny(provider -> provider.getReplacedVirtualFile(virtualFile));
    return replacedVirtualFile == null ? virtualFile : replacedVirtualFile;
  }

  /**
   * @return HighlightingSession when everything's OK or
   * return null if session wasn't created because highlighter/document/psiFile wasn't found or
   * throw PCE if it really wasn't an appropriate moment to ask
   */
  private HighlightingSession queuePassesCreation(@NotNull FileEditor fileEditor,
                                                  @NotNull VirtualFile virtualFile,
                                                  int @NotNull [] passesToIgnore,
                                                  @NotNull Map<? super Pair<Document, Class<? extends ProgressableTextEditorHighlightingPass>>, ProgressableTextEditorHighlightingPass> mainDocumentPasses) {
    ThreadingAssertions.assertEventDispatchThread();
    BackgroundEditorHighlighter highlighter;

    try (AccessToken ignored = ClientId.withExplicitClientId(ClientFileEditorManager.getClientId(fileEditor))) {
      highlighter = fileEditor.getBackgroundHighlighter();
    }
    TextEditor textEditor = fileEditor instanceof TextEditor t ? t : null;
    Editor editor = textEditor == null ? null : textEditor.getEditor();
    if (highlighter == null) {
      if (PassExecutorService.LOG.isDebugEnabled()) {
        PassExecutorService.log(null, null, "couldn't highlight", virtualFile, "because getBackgroundHighlighter() returned null. fileEditor=",
          fileEditor, fileEditor.getClass(),
          textEditor == null ? "editor is null" : "editor loaded:" + textEditor.isEditorLoaded()
        );
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
    HighlightingSessionImpl session;
    try (AccessToken ignored = ClientId.withExplicitClientId(ClientFileEditorManager.getClientId(fileEditor))) {
      Document document = editor == null ? FileDocumentManager.getInstance().getCachedDocument(virtualFile) : editor.getDocument();
      EditorColorsScheme scheme = editor == null ? null : editor.getColorsScheme();
      CodeInsightContext cachedContext = editor != null ? EditorContextManager.getCachedEditorContext(editor, myProject) : null;
      CodeInsightContext context = cachedContext != null ? cachedContext : CodeInsightContexts.anyContext();
      PsiFile psiFileToSubmit = TextEditorBackgroundHighlighter.getCachedFileToHighlight(myProject, virtualFile, context);
      if (psiFileToSubmit == null || document == null) {
        String reason = document == null ? "queuePassesCreation: couldn't submit" +  virtualFile + " because document is null: fileEditor="+ fileEditor+" ("+ fileEditor.getClass()+")"
                        : "queuePassesCreation: psiFile is null for "+virtualFile;
        if (PassExecutorService.LOG.isDebugEnabled()) {
          PassExecutorService.log(progress, null, reason);
        }
        ForkJoinPool.commonPool().execute(() -> {
          ApplicationManagerEx.getApplicationEx().tryRunReadAction(() -> {
            if (!myProject.isDisposed()) {
              // refresh current file and cache it (in background) so that FileDocumentManager.getCachedDocument above could retrieve it later
              Document renewedDocument = editor == null ? FileDocumentManager.getInstance().getDocument(virtualFile) : editor.getDocument();
              CodeInsightContext renewedContext = editor != null
                                                  ? EditorContextManager.getEditorContext(editor, myProject)
                                                  : CodeInsightContexts.anyContext();
              if (renewedDocument != null) {
                PsiFile psiFile = TextEditorBackgroundHighlighter.renewFile(myProject, renewedDocument, renewedContext);
                if (psiFile != null) {
                  // if for some reason the TextEditorBackgroundHighlighter.getCachedFileToHighlight() returned null,
                  // but the full refresh and get PSI returned not-null PSI, restart the daemon
                  // (but only in this case; all other cases e.g. the file out of project roots should not lead to endless restarts)
                  stopAndRestartMyProcess(progress, null, reason);
                }
              }
            }
          });
        });
        return null;
      }
      TextRange compositeDocumentDirtyRange = ObjectUtils.notNull(myFileStatusMap.getCompositeDocumentDirtyRange(document), TextRange.EMPTY_RANGE);
      session = HighlightingSessionImpl.createHighlightingSession(psiFileToSubmit, context, editor, scheme, progress, daemonCancelEventCount, compositeDocumentDirtyRange);
      JobLauncher.getInstance().submitToJobThread(ThreadContext.captureThreadContext(Context.current().wrap(() ->
            submitInBackground(fileEditor, document, virtualFile, psiFileToSubmit, highlighter, passesToIgnore, progress, session, mainDocumentPasses))),
            // manifest exceptions in EDT to avoid storing them in the Future and abandoning
            task -> ApplicationManager.getApplication().invokeLater(() -> ConcurrencyUtil.manifestExceptionsIn(task)));
    }
    if (PassExecutorService.LOG.isDebugEnabled()) {
      PassExecutorService.log(progress, null, "queuePassesCreation completed. session=", session);
    }
    return session;
  }

  private void submitInBackground(@NotNull FileEditor fileEditor,
                                  @NotNull Document document,
                                  @NotNull VirtualFile virtualFile,
                                  @NotNull PsiFile psiFile,
                                  @NotNull BackgroundEditorHighlighter backgroundEditorHighlighter,
                                  int @NotNull [] passesToIgnore,
                                  @NotNull DaemonProgressIndicator progress,
                                  @NotNull HighlightingSessionImpl session,
                                  @NotNull Map<? super Pair<Document, Class<? extends ProgressableTextEditorHighlightingPass>>, ProgressableTextEditorHighlightingPass> mainDocumentPasses) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(Context.current().wrap(() -> {
        HighlightingPass[] passes = ReadAction.compute(() -> {
          if (progress.isCanceled() ||
              myProject.isDisposed() ||
              getPsiDocumentManager().hasEventSystemEnabledUncommittedDocuments() ||
              !fileEditor.isValid() ||
              !psiFile.isValid()) {
            String reason = (myProject.isDisposed() ? "project isDisposed" : "")
              + (getPsiDocumentManager().hasEventSystemEnabledUncommittedDocuments() ? " hasUncommitted documents: " + Arrays.toString(getPsiDocumentManager().getUncommittedDocuments()) : "")
              + (fileEditor.isValid() ? "" : " file editor "+fileEditor+" is invalid")
              + (psiFile.isValid() ? "" : " psiFile "+psiFile+" is invalid")
              ;
            stopAndRestartMyProcess(progress, null, reason);
            return HighlightingPass.EMPTY_ARRAY;
          }
          if (session.isCanceled()) {
            // editor or something was changed between commit document notification in EDT and this point in the FJP thread
            stopAndRestartMyProcess(progress, null, session + " is canceled");
            throw new ProcessCanceledException();
          }
          myListeners.flushUpdateFileStatusQueue(); // finish updating FileStatusMap on PSI changes
          session.additionalSetupFromBackground(psiFile);
          try (AccessToken ignored = ClientId.withExplicitClientId(ClientFileEditorManager.getClientId(fileEditor))) {
            HighlightingPass[] r = backgroundEditorHighlighter.createPassesForEditor();
            if (passesToIgnore.length != 0) {
              r = ContainerUtil.findAllAsArray(r, pass->!(pass instanceof TextEditorHighlightingPass te) || ArrayUtil.indexOf(passesToIgnore, te.getId()) == -1);
            }
            for (int i = 0; i < r.length; i++) {
              HighlightingPass pass = r[i];
              if (pass instanceof ProgressableTextEditorHighlightingPass progr) {
                ProgressableTextEditorHighlightingPass created = mainDocumentPasses.putIfAbsent(Pair.create(document, progr.getClass()), progr);
                if (created != null) {
                  // When the document-bound pass was already created for this document,
                  // do not create additional instances of it, but reuse the first created one for all other file editors.
                  // Thus, we can distinguish whether we run the first copy of this pass (and should call collectInformation()), or
                  // we are running a duplicate (in which case we should wait for the first copy to complete),
                  // see ProgressableTextEditorHighlightingPass.waitMyJob()
                  r[i] = created;
                }
              }
            }
            // wait for heavy processing to stop, re-schedule daemon but not too soon
            if (heavyProcessIsRunning()) {
              //noinspection SSBasedInspection
              r = Registry.is("ide.dumb.mode.check.awareness") ? ContainerUtil.findAllAsArray(r, o -> DumbService.isDumbAware(o)) :
                  ContainerUtil.findAllAsArray(r, o -> o.isDumbAware() || o instanceof DumbAware);
              if (LOG.isDebugEnabled()) {
                LOG.debug("submitInBackground: heavyProcessIsRunning=true, so only these passes are created: " + Arrays.toString(r));
              }
            }
            return r;
          }
        });
        boolean hasPasses = passes.length != 0;
        if (!hasPasses) {
          // will be re-scheduled by HeavyLatch or some other listener in DaemonListeners
          stopAndRestartMyProcess(progress, null, " no passes created");
          return;
        }
        // synchronize on TextEditorHighlightingPassRegistrarImpl instance to avoid concurrent modification of TextEditorHighlightingPassRegistrarImpl.nextAvailableId
        synchronized (TextEditorHighlightingPassRegistrar.getInstance(myProject)) {
          myPassExecutorService.submitPasses(document, session.getCodeInsightContext(), virtualFile, psiFile, fileEditor, passes, progress);
        }
        ProgressManager.checkCanceled();
      }), progress);
    }
    catch (ProcessCanceledException e) {
      String reason = LOG.isDebugEnabled() ? ExceptionUtil.getThrowableText(e) : "PCE";
      stopAndRestartMyProcess(progress, e.getCause(), reason);
    }
    catch (Throwable e) {
      // make it manifestable in tests
      String reason = LOG.isDebugEnabled() ? ExceptionUtil.getThrowableText(e) : "PCE";
      stopAndRestartMyProcess(progress, e, reason);
      throw e;
    }
  }

  // return true if heavy operation is running
  private static boolean heavyProcessIsRunning() {
    if (DumbServiceImpl.ALWAYS_SMART) {
      return false;
    }
    // VFS refresh is OK
    return HeavyProcessLatch.INSTANCE.isRunningAnythingBut(HeavyProcessLatch.Type.Syncing);
  }

  private synchronized @NotNull DaemonProgressIndicator createUpdateProgress(@NotNull FileEditor fileEditor) {
    myUpdateProgress.entrySet().removeIf(entry -> !entry.getKey().isValid());
    DaemonProgressIndicator progress = new MyDaemonProgressIndicator(fileEditor);
    progress.setModalityProgress(null);
    progress.start();
    if (isRestartToCompleteEssentialHighlightingRequested()) {
      progress.putUserData(COMPLETE_ESSENTIAL_HIGHLIGHTING_KEY, true);
    }
    DaemonProgressIndicator oldProgress = myUpdateProgress.put(fileEditor, progress);
    if (oldProgress != null && !oldProgress.isCanceled()) {
      oldProgress.cancel(new Throwable(), "Old indicator: " + oldProgress);
    }
    if (PassExecutorService.LOG.isDebugEnabled()) {
      PassExecutorService.log(progress, null, "createUpdateProgress(" + fileEditor + "); oldProgress=" + oldProgress);
    }
    myDaemonListenerPublisher.daemonStarting(List.of(fileEditor));
    return progress;
  }

  private final class MyDaemonProgressIndicator extends DaemonProgressIndicator {
    private final FileEditor myFileEditor;

    MyDaemonProgressIndicator(@NotNull FileEditor fileEditor) {
      myFileEditor = fileEditor;
    }

    @Override
    public void onCancelled(@NotNull String reason) {
      removeIndicatorFromMap(myFileEditor, this);
      myDaemonListenerPublisher.daemonCanceled(reason, List.of(myFileEditor));
    }

    @Override
    public void onStop() {
      Document document = myFileEditor instanceof TextEditor text ? text.getEditor().getDocument() : FileDocumentManager.getInstance().getCachedDocument(myFileEditor.getFile());
      if (document != null && myFileStatusMap.allDirtyScopesAreNull(document)) {
        // dispose composite dirty range before firing daemon listeners because some of them count on the getfileDirtyRange()==null (e.g. OptimizeImportRestarter)
        myFileStatusMap.disposeDirtyDocumentRangeStorage(document);
      }
      removeIndicatorFromMap(myFileEditor, this);
      myDaemonListenerPublisher.daemonFinished(List.of(myFileEditor));
      HighlightingSessionImpl.clearAllHighlightingSessions(this);
      completeEssentialHighlightingRequested = false;
    }
  }

  @Override
  public void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    assertFileFromMyProject(psiFile.getProject(), psiFile);
    for (ReferenceImporter importer : ReferenceImporter.EP_NAME.getExtensionList()) {
      if (importer.isAddUnambiguousImportsOnTheFlyEnabled(psiFile) && importer.autoImportReferenceAtCursor(editor, psiFile)) break;
    }
  }

  @TestOnly
  public synchronized @Unmodifiable @NotNull Map<FileEditor, DaemonProgressIndicator> getUpdateProgress() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return Map.copyOf(myUpdateProgress);
  }

  private @NotNull Collection<? extends FileEditor> getSelectedEditors() {
    ThreadingAssertions.assertEventDispatchThread();
    // editors in modal context
    List<? extends Editor> editors = EditorTracker.Companion.getInstance(myProject).getActiveEditors();
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
    // Can't just check the editor's window, though, because the active window might be something else, e.g. a detached Project View,
    // see IDEA-343992.
    Window editorWindow = SwingUtilities.getWindowAncestor(editor.getComponent());
    var editorProject = getProject(editorWindow);
    for (Window window : Window.getWindows()) {
      if (!window.isActive()) {
        continue;
      }
      if (window == editorWindow || getProject(window) == editorProject) {
        return true;
      }
    }
    // Project should be active in a headless case (see FL-25764)
    return editorWindow == null;
  }

  private static @Nullable Project getProject(@Nullable Window window) {
    return ProjectUtil.getProjectForComponent(window);
  }

  /**
   * This API is made {@code Internal} intentionally as it could lead to unpredictable highlighting performance behavior.
   *
   * @param flag if {@code true}: enables code insight passes serialization:
   *             Injected fragments {@link InjectedGeneralHighlightingPass} highlighting and Inspections run after
   *             completion of Syntax analysis {@link GeneralHighlightingPass}.
   *             if {@code false} (default behavior) code insight passes are running in parallel
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

  // tell the next restarted highlighting that it should start in the "full mode" and run all inspections/external annotators/etc
  void requestRestartToCompleteEssentialHighlighting() {
    restart("restartToCompleteEssentialHighlighting");
    completeEssentialHighlightingRequested = true;
  }
  public boolean isRestartToCompleteEssentialHighlightingRequested() {
    return completeEssentialHighlightingRequested;
  }
  @TestOnly
  public void waitForUpdateFileStatusBackgroundQueueInTests() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myListeners.waitForUpdateFileStatusQueue();
  }

  @Override
  @RequiresBackgroundThread
  protected void rescheduleShowIntentionsPass(@NotNull PsiFile psiFile, @NotNull TextRange visibleRange) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }
    Editor editor = PsiEditorUtil.getInstance().findEditorByPsiElement(psiFile);
    if (editor != null) {
      TextEditorHighlightingPass showAutoImportPass = new ShowAutoImportPass(psiFile, editor, ProperTextRange.create(visibleRange.isProperRange() ? visibleRange : psiFile.getTextRange()));
      // have to restart ShowAutoImportPass manually because the highlighting session might very well be over by now
      ApplicationManager.getApplication().invokeLater(() -> {
        DaemonProgressIndicator sessionIndicator = new DaemonProgressIndicator();
        ProgressManager.getInstance().executeProcessUnderProgress(() -> showAutoImportPass.doApplyInformationToEditor(), sessionIndicator);
      }, __ -> editor.isDisposed() || psiFile.getProject().isDisposed());
    }
  }
}
