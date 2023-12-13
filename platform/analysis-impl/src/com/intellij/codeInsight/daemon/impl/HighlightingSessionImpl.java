// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
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
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class HighlightingSessionImpl implements HighlightingSession {
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  private final @NotNull Project myProject;
  private final Document myDocument;
  private final @NotNull ProperTextRange myVisibleRange;
  private final @NotNull CanISilentlyChange.Result myCanChangeFileSilently;
  private final Number myDaemonCancelEventCount;
  private final int myDaemonInitialCancelEventCount;
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
                                  @NotNull Number daemonCancelEventCount) {
    myPsiFile = psiFile;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = ReadAction.compute(() -> psiFile.getProject());
    myDocument = ReadAction.compute(() -> psiFile.getOriginalFile().getViewProvider().getDocument());
    myVisibleRange = visibleRange;
    myCanChangeFileSilently = canChangeFileSilently;
    myDaemonCancelEventCount = daemonCancelEventCount;
    myDaemonInitialCancelEventCount = daemonCancelEventCount.intValue();
    assert !(psiFile.getViewProvider() instanceof InjectedFileViewProvider) : "Expected top-level file, but got: " + psiFile.getViewProvider();
  }

  private static final Key<ConcurrentMap<PsiFile, HighlightingSession>> HIGHLIGHTING_SESSION = Key.create("HIGHLIGHTING_SESSION");

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
    Map<PsiFile, HighlightingSession> map = indicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      throw new IllegalStateException("No HighlightingSession stored in "+indicator);
    }
    HighlightingSession session = map.get(file);
    if (session == null) {
      String mapStr = map.entrySet().stream().map(e -> {
        PsiFile storedFile = e.getKey();
        return storedFile + ": " + System.identityHashCode(storedFile) + " (" + storedFile.getClass() + ") -> " + e.getValue();
      }).collect(Collectors.joining("; "));
      throw new IllegalStateException("No HighlightingSession found for " + file +  ": " + System.identityHashCode(file) + " (" + file.getClass() + ") in " + indicator + " in map: " + mapStr);
    }
    return session;
  }

  static void getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                             @NotNull DaemonProgressIndicator progressIndicator,
                                             @NotNull ProperTextRange visibleRange) {
    Map<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    HighlightingSession session = map == null ? null : map.get(psiFile);
    if (session == null) {
      createHighlightingSession(psiFile, progressIndicator, null, visibleRange, CanISilentlyChange.Result.UH_UH, 0);
    }
  }

  static @NotNull HighlightingSessionImpl createHighlightingSession(@NotNull PsiFile psiFile,
                                                                    @Nullable Editor editor,
                                                                    @Nullable EditorColorsScheme editorColorsScheme,
                                                                    @NotNull DaemonProgressIndicator progressIndicator,
                                                                    @NotNull Number daemonCancelEventCount) {
    ThreadingAssertions.assertEventDispatchThread();
    TextRange fileRange = psiFile.getTextRange();
    ProperTextRange visibleRange;
    visibleRange = editor == null ? ProperTextRange.create(ObjectUtils.notNull(fileRange, TextRange.EMPTY_RANGE))
                                  : editor.calculateVisibleRange();
    CanISilentlyChange.Result canChangeFileSilently = CanISilentlyChange.thisFile(psiFile);
    return createHighlightingSession(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently, daemonCancelEventCount);
  }

  static @NotNull HighlightingSessionImpl createHighlightingSession(@NotNull PsiFile psiFile,
                                                                    @NotNull DaemonProgressIndicator progressIndicator,
                                                                    @Nullable EditorColorsScheme editorColorsScheme,
                                                                    @NotNull ProperTextRange visibleRange,
                                                                    @NotNull CanISilentlyChange.Result canChangeFileSilently,
                                                                    @NotNull Number daemonCancelEventCount) {
    // no assertIsDispatchThread() is necessary
    Map<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, new ConcurrentHashMap<>());
    }
    HighlightingSessionImpl session = new HighlightingSessionImpl(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently, daemonCancelEventCount);
    map.put(psiFile, session);
    return session;
  }

  @ApiStatus.Internal
  public static void runInsideHighlightingSession(@NotNull PsiFile file,
                                                  @Nullable EditorColorsScheme editorColorsScheme,
                                                  @NotNull ProperTextRange visibleRange,
                                                  boolean canChangeFileSilently,
                                                  @NotNull Consumer<? super @NotNull HighlightingSession> runnable) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    HighlightingSessionImpl session = createHighlightingSession(file, indicator, editorColorsScheme, visibleRange, canChangeFileSilently
                                                                                                                 ? CanISilentlyChange.Result.UH_HUH
                                                                                                                 : CanISilentlyChange.Result.UH_UH,
                                                                0);
    session.additionalSetupFromBackground(file);
    runnable.accept(session);
  }

  static void waitForAllSessionsHighlightInfosApplied(@NotNull DaemonProgressIndicator progressIndicator) {
    ConcurrentMap<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map != null) {
      for (HighlightingSession session : map.values()) {
        ((HighlightingSessionImpl)session).applyFileLevelHighlightsRequests();
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
  boolean addInfoIncrementally(@NotNull HighlightInfo info, @NotNull TextRange restrictedRange, int groupId) {
    return BackgroundUpdateHighlightersUtil.addHighlighterToEditorIncrementally(this, getPsiFile(), getDocument(), restrictedRange,
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

  static void clearProgressIndicator(@NotNull DaemonProgressIndicator indicator) {
    indicator.putUserData(HIGHLIGHTING_SESSION, null);
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
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myIsEssentialHighlightingOnly = HighlightingLevelManager.getInstance(psiFile.getProject()).runEssentialHighlightingOnly(psiFile);
    VirtualFile virtualFile = psiFile.getVirtualFile();
    myInContent = virtualFile != null && ModuleUtilCore.projectContainsFile(psiFile.getProject(), virtualFile, false);
    extensionsAllowToChangeFileSilently = virtualFile == null ? ThreeState.UNSURE : SilentChangeVetoer.extensionsAllowToChangeFileSilently(getProject(), virtualFile);
  }

  public boolean isCanceled() {
    return myDaemonCancelEventCount.intValue() != myDaemonInitialCancelEventCount;
  }

  @Override
  public void updateFileLevelHighlights(@NotNull List<? extends HighlightInfo> fileLevelHighlights, int group, boolean cleanOldHighlights) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    PsiFile psiFile = getPsiFile();
    boolean shouldUpdate = !fileLevelHighlights.isEmpty() || codeAnalyzer.hasFileLevelHighlights(group, psiFile);
    if (shouldUpdate) {
      Future<?> future = EdtExecutorService.getInstance().submit(() -> {
        if (project.isDisposed() || isCanceled()) return;
        if (cleanOldHighlights) {
          codeAnalyzer.cleanFileLevelHighlights(group, psiFile);
        }
        for (HighlightInfo fileLevelInfo : fileLevelHighlights) {
          codeAnalyzer.addFileLevelHighlight(group, fileLevelInfo, psiFile, null);
        }
      });
      pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
    }
  }

  @Override
  public void removeFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    Future<?> future = EdtExecutorService.getInstance().submit(() -> {
      if (!project.isDisposed() && !isCanceled()) {
        codeAnalyzer.removeFileLevelHighlight(getPsiFile(), fileLevelHighlightInfo);
      }
    });
    pendingFileLevelHighlightRequests.add((RunnableFuture<?>)future);
  }
  @Override
  public void addFileLevelHighlight(@NotNull HighlightInfo fileLevelHighlightInfo, @Nullable RangeHighlighterEx toReuse) {
    Project project = getProject();
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    Future<?> future = EdtExecutorService.getInstance().submit(() -> {
      if (!project.isDisposed() && !isCanceled()) {
        codeAnalyzer.addFileLevelHighlight(Pass.LOCAL_INSPECTIONS, fileLevelHighlightInfo, getPsiFile(), toReuse);
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
}
