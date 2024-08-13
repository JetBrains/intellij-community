// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class HighlightingSessionImpl implements HighlightingSession {
  private static final Logger LOG = Logger.getInstance(HighlightingSessionImpl.class);
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  private final @NotNull Project myProject;
  private final Document myDocument;
  private final @NotNull ProperTextRange myVisibleRange;
  private final @NotNull CanISilentlyChange.Result myCanChangeFileSilently;
  private final Number myDaemonCancelEventCount;
  private final int myDaemonInitialCancelEventCount;
  @NotNull private final TextRange myCompositeDocumentDirtyRange;
  private volatile boolean myIsEssentialHighlightingOnly;
  private final Long2ObjectMap<RangeMarker> myRange2markerCache = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
  private volatile boolean myInContent;
  private volatile ThreeState extensionsAllowToChangeFileSilently;
  private final List<RunnableFuture<?>> pendingFileLevelHighlightRequests = ContainerUtil.createLockFreeCopyOnWriteList();
  private volatile HighlightSeverity myMinimumSeverity;

  private HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                  @NotNull DaemonProgressIndicator progressIndicator,
                                  @Nullable EditorColorsScheme editorColorsScheme,
                                  @NotNull ProperTextRange visibleRange,
                                  @NotNull CanISilentlyChange.Result canChangeFileSilently,
                                  @NotNull Number daemonCancelEventCount,
                                  @NotNull TextRange compositeDocumentDirtyRange) {
    myPsiFile = psiFile;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = ReadAction.compute(() -> psiFile.getProject());
    myDocument = ReadAction.compute(() -> psiFile.getOriginalFile().getViewProvider().getDocument());
    myVisibleRange = visibleRange;
    myCanChangeFileSilently = canChangeFileSilently;
    myDaemonCancelEventCount = daemonCancelEventCount;
    myDaemonInitialCancelEventCount = daemonCancelEventCount.intValue();
    myCompositeDocumentDirtyRange = compositeDocumentDirtyRange;
    assert !(psiFile.getViewProvider() instanceof InjectedFileViewProvider) : "Expected top-level file, but got: " + psiFile.getViewProvider();
  }

  /**
   * PsiFile -> stack of recent HighlightSessions. May be more than one when several nested {@link #runInsideHighlightingSession} calls were made
   */
  private static final Key<Map<PsiFile, List<HighlightingSession>>> HIGHLIGHTING_SESSION = Key.create("HIGHLIGHTING_SESSION");

  boolean canChangeFileSilently() {
    return myCanChangeFileSilently.canIReally(myInContent, extensionsAllowToChangeFileSilently);
  }

  void setMinimumSeverity(@Nullable HighlightSeverity minimumSeverity) {
    myMinimumSeverity = minimumSeverity;
  }

  HighlightSeverity getMinimumSeverity() {
    return myMinimumSeverity;
  }

  static @NotNull HighlightingSession getFromCurrentIndicator(@NotNull PsiFile file) {
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    Map<PsiFile, List<HighlightingSession>> map = indicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      throw new IllegalStateException("No HighlightingSession stored in "+indicator);
    }
    List<HighlightingSession> sessions = map.get(file);
    if (sessions == null) {
      String mapStr = map.entrySet().stream().map(e -> {
        PsiFile storedFile = e.getKey();
        return storedFile + ": " + System.identityHashCode(storedFile) + " (" + storedFile.getClass() + ") -> " + e.getValue();
      }).collect(Collectors.joining("; "));
      throw new IllegalStateException("No HighlightingSession found for " + file +  ": " + System.identityHashCode(file) + " (" + file.getClass() + ") in " + indicator + " in map (" +map.size()+"): " + mapStr);
    }
    return ContainerUtil.getLastItem(sessions);
  }

  static void getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                             @NotNull DaemonProgressIndicator progressIndicator,
                                             @NotNull ProperTextRange visibleRange,
                                             @NotNull TextRange compositeDocumentDirtyRange) {
    Map<PsiFile, List<HighlightingSession>> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    List<HighlightingSession> sessions = map == null ? null : map.get(psiFile);
    if (sessions == null) {
      createHighlightingSession(psiFile, progressIndicator, null, visibleRange, CanISilentlyChange.Result.UH_UH, 0, compositeDocumentDirtyRange);
    }
  }

  static @NotNull HighlightingSessionImpl createHighlightingSession(@NotNull PsiFile psiFile,
                                                                    @Nullable Editor editor,
                                                                    @Nullable EditorColorsScheme editorColorsScheme,
                                                                    @NotNull DaemonProgressIndicator progressIndicator,
                                                                    @NotNull Number daemonCancelEventCount,
                                                                    @NotNull TextRange compositeDocumentDirtyRange) {
    ThreadingAssertions.assertEventDispatchThread();
    TextRange fileRange = psiFile.getTextRange();
    ProperTextRange visibleRange;
    visibleRange = editor == null ? ProperTextRange.create(ObjectUtils.notNull(fileRange, TextRange.EMPTY_RANGE))
                                  : editor.calculateVisibleRange();
    CanISilentlyChange.Result canChangeFileSilently = CanISilentlyChange.thisFile(psiFile);
    return createHighlightingSession(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently, daemonCancelEventCount, compositeDocumentDirtyRange);
  }

  private static @NotNull HighlightingSessionImpl createHighlightingSession(@NotNull PsiFile psiFile,
                                                                            @NotNull DaemonProgressIndicator progressIndicator,
                                                                            @Nullable EditorColorsScheme editorColorsScheme,
                                                                            @NotNull ProperTextRange visibleRange,
                                                                            @NotNull CanISilentlyChange.Result canChangeFileSilently,
                                                                            @NotNull Number daemonCancelEventCount,
                                                                            @NotNull TextRange compositeDocumentDirtyRange) {
    // no assertIsDispatchThread() is necessary
    Map<PsiFile, List<HighlightingSession>> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, new ConcurrentHashMap<>());
    }
    HighlightingSessionImpl session = new HighlightingSessionImpl(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently, daemonCancelEventCount, compositeDocumentDirtyRange);
    map.compute(psiFile, (__, oldSessions) -> ContainerUtil.append(ContainerUtil.notNullize(oldSessions), session));
    return session;
  }

  @ApiStatus.Internal
  public static void runInsideHighlightingSession(@NotNull PsiFile file,
                                                  @Nullable EditorColorsScheme editorColorsScheme,
                                                  @NotNull ProperTextRange visibleRange,
                                                  boolean canChangeFileSilently,
                                                  @NotNull Consumer<? super @NotNull HighlightingSession> runnable) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    HighlightingSessionImpl session = createHighlightingSession(file, indicator, editorColorsScheme, visibleRange, canChangeFileSilently
                                                                                                                 ? CanISilentlyChange.Result.UH_HUH
                                                                                                                 : CanISilentlyChange.Result.UH_UH,
                                                                0, TextRange.EMPTY_RANGE);
    try {
      session.additionalSetupFromBackground(file);
      runnable.accept(session);
    }
    finally {
      clearHighlightingSession(indicator, file, session);
    }
  }

  @ApiStatus.Internal
  static void runInsideHighlightingSessionInEDT(@NotNull PsiFile file,
                                                @Nullable EditorColorsScheme editorColorsScheme,
                                                @NotNull ProperTextRange visibleRange,
                                                boolean canChangeFileSilently,
                                                @NotNull Consumer<? super @NotNull HighlightingSession> runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!(ProgressIndicatorProvider.getGlobalProgressIndicator() instanceof DaemonProgressIndicator)) {
      ProgressManager.getInstance().executeProcessUnderProgress(()->runInsideHighlightingSessionInEDT(file, editorColorsScheme, visibleRange, canChangeFileSilently, runnable), new DaemonProgressIndicator());
      return;
    }
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    HighlightingSessionImpl session = createHighlightingSession(file, indicator, editorColorsScheme, visibleRange, canChangeFileSilently
                                                                                                                 ? CanISilentlyChange.Result.UH_HUH
                                                                                                                 : CanISilentlyChange.Result.UH_UH,
                                                                0, TextRange.EMPTY_RANGE);
    session.myInContent = true;
    try {
      runnable.accept(session);
    }
    finally {
      clearHighlightingSession(indicator, file, session);
    }
  }

  static void waitForAllSessionsHighlightInfosApplied(@NotNull DaemonProgressIndicator progressIndicator) {
    Map<PsiFile, List<HighlightingSession>> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map != null) {
      for (List<HighlightingSession> sessions : map.values()) {
        for (HighlightingSession session : sessions) {
          ((HighlightingSessionImpl)session).applyFileLevelHighlightsRequests();
        }
      }
    }
  }


  @Override
  public @NotNull PsiFile getPsiFile() {
    return myPsiFile;
  }

  @Override
  public @NotNull Document getDocument() {
    return myDocument;
  }

  @Override
  public @NotNull ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public EditorColorsScheme getColorsScheme() {
    return myEditorColorsScheme;
  }

  // return true if added
  @Deprecated
  void addInfoIncrementally(@NotNull HighlightInfo info, @NotNull TextRange restrictedRange, int groupId) {
    BackgroundUpdateHighlightersUtil.addHighlighterToEditorIncrementally(this, getPsiFile(), getDocument(), restrictedRange,
                                                                         info, getColorsScheme(), groupId, myRange2markerCache);
  }

  void applyFileLevelHighlightsRequests() {
    ThreadingAssertions.assertEventDispatchThread();
    List<RunnableFuture<?>> requests = new ArrayList<>(pendingFileLevelHighlightRequests);
    for (RunnableFuture<?> request : requests) {
      request.run();
    }
    pendingFileLevelHighlightRequests.removeAll(requests);
  }

  static void clearAllHighlightingSessions(@NotNull DaemonProgressIndicator indicator) {
    indicator.putUserData(HIGHLIGHTING_SESSION, null);
    if (LOG.isTraceEnabled()) {
      LOG.trace("HighlightingSessionImpl.clearAllHighlightingSessions");
    }
  }

  // clear references to psiFile from progressIndicator
  private static void clearHighlightingSession(@NotNull DaemonProgressIndicator progressIndicator,
                                               @NotNull PsiFile psiFile,
                                               @NotNull HighlightingSessionImpl session) {
    Map<PsiFile, List<HighlightingSession>> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map != null) {
      map.compute(psiFile, (__, oldSessions) -> ContainerUtil.getLastItem(oldSessions) == session ?
                                                ContainerUtil.nullize(List.copyOf(oldSessions.subList(0, oldSessions.size() - 1))) :
                                                oldSessions);
      if (LOG.isTraceEnabled()) {
        LOG.trace("HighlightingSessionImpl.clearHighlightingSession("+psiFile.getVirtualFile()+"); "+map.size()+" remain");
      }
    }
  }

  @Override
  public @NotNull ProperTextRange getVisibleRange() {
    return myVisibleRange;
  }

  @Override
  public boolean isEssentialHighlightingOnly() {
    return myIsEssentialHighlightingOnly;
  }

  @Override
  public String toString() {
    return "HighlightingSessionImpl: " +
           "myVisibleRange:"+myVisibleRange+
           "; myPsiFile: "+myPsiFile+ " (" + myPsiFile.getClass() + ")"+
           (myIsEssentialHighlightingOnly ? "; essentialHighlightingOnly":"");
  }

  // compute additional stuff in background thread
  void additionalSetupFromBackground(@NotNull PsiFile psiFile) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    myIsEssentialHighlightingOnly = ReadAction.compute(() -> HighlightingLevelManager.getInstance(psiFile.getProject()).runEssentialHighlightingOnly(psiFile));
    VirtualFile virtualFile = ReadAction.compute(() -> psiFile.getVirtualFile());
    myInContent = ReadAction.compute(() -> virtualFile != null && ModuleUtilCore.projectContainsFile(psiFile.getProject(), virtualFile, false));
    extensionsAllowToChangeFileSilently = virtualFile == null ? ThreeState.UNSURE : ReadAction.compute(() -> SilentChangeVetoer.extensionsAllowToChangeFileSilently(getProject(), virtualFile));
  }

  @Override
  public boolean isCanceled() {
    return myDaemonCancelEventCount.intValue() != myDaemonInitialCancelEventCount;
  }

  @Deprecated
  void updateFileLevelHighlights(@NotNull List<? extends HighlightInfo> fileLevelHighlights, int group, boolean cleanOldHighlights, @NotNull HighlighterRecycler recycler) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    PsiFile psiFile = getPsiFile();
    boolean shouldUpdate = !fileLevelHighlights.isEmpty() || codeAnalyzer.hasFileLevelHighlights(group, psiFile);
    if (shouldUpdate) {
      List<RangeHighlighter> reusedHighlighters = ContainerUtil.map(fileLevelHighlights, __->recycler.pickupFileLevelRangeHighlighter(psiFile.getTextLength()));

      Future<?> future = EdtExecutorService.getInstance().submit(() -> {
        if (project.isDisposed() || isCanceled()) return;
        if (cleanOldHighlights) {
          codeAnalyzer.cleanFileLevelHighlights(group, psiFile);
        }
        for (int i = 0; i < fileLevelHighlights.size(); i++) {
          HighlightInfo fileLevelInfo = fileLevelHighlights.get(i);
          RangeHighlighter reused = reusedHighlighters.get(i);
          codeAnalyzer.addFileLevelHighlight(group, fileLevelInfo, psiFile, reused);
        }
      });
      pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
    }
  }

  // removes the old HighlightInfo and adds the new one atomically, to avoid flicker
  void replaceFileLevelHighlight(@NotNull HighlightInfo oldFileLevelInfo, @NotNull HighlightInfo newFileLevelInfo, @Nullable RangeHighlighterEx toReuse) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    Future<?> future = EdtExecutorService.getInstance().submit(() -> {
      if (!project.isDisposed() && !isCanceled()) {
        codeAnalyzer.replaceFileLevelHighlight( oldFileLevelInfo, newFileLevelInfo, getPsiFile(), toReuse);
      }
    });
    pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
  }

  void removeFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    Future<?> future = EdtExecutorService.getInstance().submit(() -> {
      if (!project.isDisposed()) {
        codeAnalyzer.removeFileLevelHighlight(getPsiFile(), fileLevelHighlightInfo);
      }
    });
    pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
  }
  public void addFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo, @Nullable RangeHighlighterEx toReuse) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    Future<?> future = EdtExecutorService.getInstance().submit(() -> {
      if (!project.isDisposed() && !isCanceled()) {
        codeAnalyzer.addFileLevelHighlight(fileLevelHighlightInfo.getGroup(), fileLevelHighlightInfo, getPsiFile(), toReuse);
      }
    });
    pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
  }

  @NotNull
  static RangeMarker getOrCreateVisitingRangeMarker(@NotNull PsiFile psiFile, @NotNull Document document, long range) {
    // in the case of multi-roots provider, the session is stored in the main
    PsiFile mainRoot = psiFile.getViewProvider().getAllFiles().get(0);
    HighlightingSessionImpl session = (HighlightingSessionImpl)HighlightingSessionImpl.getFromCurrentIndicator(mainRoot);
    return session.myRange2markerCache.computeIfAbsent(range, r->document.createRangeMarker(TextRangeScalarUtil.create(r)));
  }

  @NotNull TextRange getCompositeDocumentDirtyRange() {
    return myCompositeDocumentDirtyRange;
  }
}
