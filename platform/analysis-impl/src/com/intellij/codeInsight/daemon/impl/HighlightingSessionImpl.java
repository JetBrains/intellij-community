// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.TransferToEDTQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public final class HighlightingSessionImpl implements HighlightingSession {
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  private final @NotNull Project myProject;
  private final Document myDocument;
  @NotNull
  private final ProperTextRange myVisibleRange;
  @NotNull
  private final CanISilentlyChange.Result myCanChangeFileSilently;
  volatile boolean myIsEssentialHighlightingOnly;
  private final Long2ObjectMap<RangeMarker> myRanges2markersCache = new Long2ObjectOpenHashMap<>();
  private final TransferToEDTQueue<Runnable> myEDTQueue;

  private HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                  @NotNull DaemonProgressIndicator progressIndicator,
                                  @Nullable EditorColorsScheme editorColorsScheme,
                                  @NotNull ProperTextRange visibleRange,
                                  @NotNull CanISilentlyChange.Result canChangeFileSilently) {
    myPsiFile = psiFile;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = ReadAction.compute(() -> psiFile.getProject());
    myDocument = ReadAction.compute(() -> psiFile.getOriginalFile().getViewProvider().getDocument());
    myVisibleRange = visibleRange;
    myCanChangeFileSilently = canChangeFileSilently;
    myEDTQueue = new TransferToEDTQueue<>("Apply highlighting results", runnable -> {
      runnable.run();
      return true;
    }, __ -> myProject.isDisposed() || getProgressIndicator().isCanceled()) {
      @Override
      protected void schedule(@NotNull Runnable updateRunnable) {
        ApplicationManager.getApplication().invokeLater(updateRunnable, ModalityState.any());
      }
    };
  }

  private static final Key<ConcurrentMap<PsiFile, HighlightingSession>> HIGHLIGHTING_SESSION = Key.create("HIGHLIGHTING_SESSION");

  void applyInEDT(@NotNull Runnable runnable) {
    myEDTQueue.offer(runnable);
  }

  boolean canChangeFileSilently(boolean isInContent) {
    return myCanChangeFileSilently.canIReally(isInContent);
  }

  @NotNull
  static HighlightingSession getFromCurrentIndicator(@NotNull PsiFile file) {
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    Map<PsiFile, HighlightingSession> map = indicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      throw new IllegalStateException("No HighlightingSession stored in "+indicator);
    }
    HighlightingSession session = map.get(file);
    if (session == null) {
      String mapStr = map.entrySet().stream().map(e -> e.getKey() + " (" + e.getKey().getClass() + ") -> " + e.getValue()).collect(Collectors.joining("; "));
      throw new IllegalStateException("No HighlightingSession found for " + file + " (" + file.getClass() + ") in " + indicator + " in map: " + mapStr);
    }
    return session;
  }

  static void getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                             @NotNull DaemonProgressIndicator progressIndicator,
                                             @NotNull ProperTextRange visibleRange) {
    Map<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    HighlightingSession session = map == null ? null : map.get(psiFile);
    if (session == null) {
      createHighlightingSession(psiFile, progressIndicator, null, visibleRange, CanISilentlyChange.Result.UH_UH);
    }
  }

  @NotNull
  static HighlightingSessionImpl createHighlightingSession(@NotNull PsiFile psiFile,
                                                       @Nullable Editor editor,
                                                       @Nullable EditorColorsScheme editorColorsScheme,
                                                       @NotNull DaemonProgressIndicator progressIndicator) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    TextRange fileRange = psiFile.getTextRange();
    ProperTextRange visibleRange = editor == null ? ProperTextRange.create(ObjectUtils.notNull(fileRange, TextRange.EMPTY_RANGE)) : VisibleHighlightingPassFactory.calculateVisibleRange(editor);
    CanISilentlyChange.Result canChangeFileSilently = CanISilentlyChange.thisFile(psiFile);
    return (HighlightingSessionImpl)createHighlightingSession(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently);
  }

  @NotNull
  static HighlightingSession createHighlightingSession(@NotNull PsiFile psiFile,
                                                       @NotNull DaemonProgressIndicator progressIndicator,
                                                       @Nullable EditorColorsScheme editorColorsScheme,
                                                       @NotNull ProperTextRange visibleRange,
                                                       @NotNull CanISilentlyChange.Result canChangeFileSilently) {
    // no assertIsDispatchThread() is necessary
    Map<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map == null) {
      map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, new ConcurrentHashMap<>());
    }
    HighlightingSession session = new HighlightingSessionImpl(psiFile, progressIndicator, editorColorsScheme, visibleRange, canChangeFileSilently);
    map.put(psiFile, session);
    return session;
  }

  @ApiStatus.Internal
  public static void runInsideHighlightingSession(@NotNull PsiFile file,
                                                  @Nullable EditorColorsScheme editorColorsScheme,
                                                  @NotNull ProperTextRange visibleRange,
                                                  boolean canChangeFileSilently,
                                                  @NotNull Runnable runnable) {
    DaemonProgressIndicator indicator = GlobalInspectionContextBase.assertUnderDaemonProgress();
    createHighlightingSession(file, indicator, editorColorsScheme, visibleRange, canChangeFileSilently ? CanISilentlyChange.Result.UH_HUH : CanISilentlyChange.Result.UH_UH);
    runnable.run();
  }

  static void waitForAllSessionsHighlightInfosApplied(@NotNull DaemonProgressIndicator progressIndicator) {
    ConcurrentMap<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
    if (map != null) {
      for (HighlightingSession session : map.values()) {
        ((HighlightingSessionImpl)session).waitForHighlightInfosApplied();
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

  void queueHighlightInfo(@NotNull HighlightInfo info,
                          @NotNull TextRange restrictedRange,
                          int groupId) {
    applyInEDT(() ->
      UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, getDocument(), getPsiFile(), restrictedRange.getStartOffset(),
                                                                 restrictedRange.getEndOffset(),
                                                                 info, getColorsScheme(), groupId, myRanges2markersCache));
  }

  void queueDisposeHighlighter(@NotNull HighlightInfo info) {
    RangeHighlighterEx highlighter = info.getHighlighter();
    if (highlighter == null) return;
    // that highlighter may have been reused for another info
    applyInEDT(() -> {
      Object actualInfo = highlighter.getErrorStripeTooltip();
      if (actualInfo == info && info.getHighlighter() == highlighter) highlighter.dispose();
    });
  }

  void waitForHighlightInfosApplied() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myEDTQueue.drain();
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
    return "HighlightingSessionImpl: myVisibleRange:"+myVisibleRange+"; myPsiFile: "+myPsiFile+ (myIsEssentialHighlightingOnly ? "; essentialHighlightingOnly":"");
  }
}
