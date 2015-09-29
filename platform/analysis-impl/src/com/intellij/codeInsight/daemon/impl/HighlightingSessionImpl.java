/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TransferToEDTQueue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class HighlightingSessionImpl implements HighlightingSession {
  @NotNull private final PsiFile myPsiFile;
  @Nullable private final Editor myEditor;
  @NotNull private final ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  @NotNull private final Project myProject;
  private final Document myDocument;
  private final Map<TextRange,RangeMarker> myRanges2markersCache = new THashMap<TextRange, RangeMarker>();

  private HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                  @Nullable Editor editor,
                                  @NotNull DaemonProgressIndicator progressIndicator,
                                  EditorColorsScheme editorColorsScheme) {
    myPsiFile = psiFile;
    myEditor = editor;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myProject = psiFile.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
  }

  private static final Key<ConcurrentMap<PsiFile, HighlightingSession>> HIGHLIGHTING_SESSION = Key.create("HIGHLIGHTING_SESSION");

  public static HighlightingSession getHighlightingSession(@NotNull PsiFile psiFile, @NotNull ProgressIndicator progressIndicator) {
    Map<PsiFile, HighlightingSession> map = ((DaemonProgressIndicator)progressIndicator).getUserData(HIGHLIGHTING_SESSION);
    return map == null ? null : map.get(psiFile);
  }

  @NotNull
  static HighlightingSession getOrCreateHighlightingSession(@NotNull PsiFile psiFile,
                                                            @Nullable Editor editor,
                                                            @NotNull DaemonProgressIndicator progressIndicator,
                                                            @Nullable EditorColorsScheme editorColorsScheme) {
    HighlightingSession session = getHighlightingSession(psiFile, progressIndicator);
    if (session == null) {
      session = new HighlightingSessionImpl(psiFile, editor, progressIndicator, editorColorsScheme);
      ConcurrentMap<PsiFile, HighlightingSession> map = progressIndicator.getUserData(HIGHLIGHTING_SESSION);
      if (map == null) {
        map = progressIndicator.putUserDataIfAbsent(HIGHLIGHTING_SESSION, ContainerUtil.<PsiFile, HighlightingSession>newConcurrentMap());
      }
      session = ConcurrencyUtil.cacheOrGet(map, psiFile, session);
    }
    return session;
  }

  @NotNull
  @Override
  public PsiFile getPsiFile() {
    return myPsiFile;
  }

  @Nullable
  @Override
  public Editor getEditor() {
    return myEditor;
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

  private final TransferToEDTQueue<Info> myAddHighlighterInEDTQueue = new TransferToEDTQueue<Info>("Apply highlighting results", new Processor<Info>() {
    @Override
    public boolean process(Info info) {
      final EditorColorsScheme colorsScheme = getColorsScheme();
      UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, getDocument(), getPsiFile(), info.myRestrictRange.getStartOffset(),
                                                                 info.myRestrictRange.getEndOffset(),
                                                                 info.myInfo, colorsScheme, info.myGroupId, myRanges2markersCache);

      return true;
    }
  }, new Condition<Object>() {
    @Override
    public boolean value(Object o) {
      return myProject.isDisposed() || getProgressIndicator().isCanceled();
    }
  }, 200);
  private final TransferToEDTQueue<RangeHighlighterEx> myDisposeHighlighterInEDTQueue = new TransferToEDTQueue<RangeHighlighterEx>("Dispose abandoned highlighter", new Processor<RangeHighlighterEx>() {
    @Override
    public boolean process(@NotNull RangeHighlighterEx highlighter) {
      highlighter.dispose();
      return true;
    }
  }, new Condition<Object>() {
    @Override
    public boolean value(Object o) {
      return myProject.isDisposed() || getProgressIndicator().isCanceled();
    }
  }, 200);


  void queueHighlightInfo(@NotNull HighlightInfo info,
                          @NotNull TextRange priorityRange,
                          @NotNull TextRange restrictedRange,
                          int groupId) {
    myAddHighlighterInEDTQueue.offer(new Info(info, restrictedRange, groupId));
  }

  void queueDisposeHighlighter(@Nullable RangeHighlighterEx highlighter) {
    if (highlighter == null) return;
    myDisposeHighlighterInEDTQueue.offer(highlighter);
  }

  private static class Info {
    @NotNull private final HighlightInfo myInfo;
    @NotNull private final TextRange myRestrictRange;
    private final int myGroupId;

    private Info(@NotNull HighlightInfo info, @NotNull TextRange restrictRange, int groupId) {
      myInfo = info;
      myRestrictRange = restrictRange;
      myGroupId = groupId;
    }
  }

  void waitForHighlightInfosApplied() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myAddHighlighterInEDTQueue.drain();
  }
}
