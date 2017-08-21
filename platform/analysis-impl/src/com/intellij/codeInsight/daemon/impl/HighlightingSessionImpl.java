/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TransferToEDTQueue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class HighlightingSessionImpl implements HighlightingSession {
  @NotNull private final PsiFile myPsiFile;
  @NotNull private final ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  @NotNull private final Project myProject;
  private final Document myDocument;
  private final Map<TextRange,RangeMarker> myRanges2markersCache = new THashMap<>();
  private final TransferToEDTQueue<Runnable> myEDTQueue;

  private HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                  @NotNull DaemonProgressIndicator progressIndicator,
                                  EditorColorsScheme editorColorsScheme) {
    myPsiFile = psiFile;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = psiFile.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    myEDTQueue = new TransferToEDTQueue<Runnable>("Apply highlighting results", runnable -> {
      runnable.run();
      return true;
    }, o -> myProject.isDisposed() || getProgressIndicator().isCanceled(), 200) {
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

  public static HighlightingSession getHighlightingSession(@NotNull PsiFile psiFile, @NotNull ProgressIndicator progressIndicator) {
    Map<PsiFile, HighlightingSession> map = ((DaemonProgressIndicator)progressIndicator).getUserData(HIGHLIGHTING_SESSION);
    return map == null ? null : map.get(psiFile);
  }

  @NotNull
  static HighlightingSession getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                                            @NotNull DaemonProgressIndicator progressIndicator,
                                                            @Nullable EditorColorsScheme editorColorsScheme) {
    HighlightingSession session = getHighlightingSession(psiFile, progressIndicator);
    if (session == null) {
      ConcurrentMap<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
      if (map == null) {
        map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, ContainerUtil.newConcurrentMap());
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


  @NotNull
  @Override
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @NotNull
  @Override
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  @Override
  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public EditorColorsScheme getColorsScheme() {
    return myEditorColorsScheme;
  }

  void queueHighlightInfo(@NotNull HighlightInfo info,
                          @NotNull TextRange restrictedRange,
                          int groupId) {
    myEDTQueue.offer(() -> {
      final EditorColorsScheme colorsScheme = getColorsScheme();
      UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, getDocument(), getPsiFile(), restrictedRange.getStartOffset(),
                                             restrictedRange.getEndOffset(),
                                             info, colorsScheme, groupId, myRanges2markersCache);
    });
  }

  void queueDisposeHighlighterFor(@NotNull HighlightInfo info) {
    RangeHighlighterEx highlighter = info.getHighlighter();
    if (highlighter == null) return;
    // that highlighter may have been reused for another info
    myEDTQueue.offer(() -> {
      Object actualInfo = highlighter.getErrorStripeTooltip();
      if (actualInfo == info && info.getHighlighter() == highlighter) highlighter.dispose();
    });
  }

  void waitForHighlightInfosApplied() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myEDTQueue.drain();
  }
}
