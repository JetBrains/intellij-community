// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.TransferToEDTQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HighlightingSessionImpl implements HighlightingSession {
  private final @NotNull PsiFile myPsiFile;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  private final @NotNull Project myProject;
  private final Document myDocument;
  private final Long2ObjectMap<RangeMarker> myRanges2markersCache = new Long2ObjectOpenHashMap<>();
  private final TransferToEDTQueue<Runnable> myEDTQueue;

  private HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                  @NotNull DaemonProgressIndicator progressIndicator,
                                  EditorColorsScheme editorColorsScheme) {
    myPsiFile = psiFile;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = psiFile.getProject();
    myDocument = psiFile.getOriginalFile().getViewProvider().getDocument();
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

  private static HighlightingSession getHighlightingSession(@NotNull PsiFile psiFile, @NotNull ProgressIndicator progressIndicator) {
    Map<PsiFile, HighlightingSession> map = ((DaemonProgressIndicator)progressIndicator).getUserData(HIGHLIGHTING_SESSION);
    return map == null ? null : map.get(psiFile);
  }

  static @NotNull HighlightingSession getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                                                     @NotNull DaemonProgressIndicator progressIndicator,
                                                                     @Nullable EditorColorsScheme editorColorsScheme) {
    HighlightingSession session = getHighlightingSession(psiFile, progressIndicator);
    if (session == null) {
      ConcurrentMap<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
      if (map == null) {
        map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, new ConcurrentHashMap<>());
      }
      session = ConcurrencyUtil.cacheOrGet(map, psiFile,
                                           new HighlightingSessionImpl(psiFile, progressIndicator, editorColorsScheme));
    }
    return session;
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
    applyInEDT(() -> {
      EditorColorsScheme colorsScheme = getColorsScheme();
      UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, getDocument(), getPsiFile(), restrictedRange.getStartOffset(),
                                             restrictedRange.getEndOffset(),
                                             info, colorsScheme, groupId, myRanges2markersCache);
    });
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
}
