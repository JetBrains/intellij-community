/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.TransferToEDTQueue;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HighlightingSessionImpl implements HighlightingSession {
  @NotNull private final PsiFile myPsiFile;
  @Nullable private final Editor myEditor;
  @NotNull private final ProgressIndicator myProgressIndicator;
  private final EditorColorsScheme myEditorColorsScheme;
  private final int myPassId;
  @NotNull private final TextRange myRestrictRange;
  private final Project myProject;
  private final Document myDocument;
  private final Map<TextRange,RangeMarker> myRanges2markersCache = new THashMap<TextRange, RangeMarker>();

  public HighlightingSessionImpl(@NotNull PsiFile psiFile,
                                 @Nullable Editor editor,
                                 @NotNull ProgressIndicator progressIndicator,
                                 EditorColorsScheme editorColorsScheme,
                                 int passId,
                                 @NotNull TextRange restrictRange) {
    myPsiFile = psiFile;
    myEditor = editor;
    myProgressIndicator = progressIndicator;
    myEditorColorsScheme = editorColorsScheme;
    myPassId = passId;
    myRestrictRange = restrictRange;
    myProject = psiFile.getProject();
    myDocument = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
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

  @Override
  public Document getDocument() {
    return myDocument;
  }

  @NotNull
  @Override
  public ProgressIndicator getProgressIndicator() {
    return myProgressIndicator;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public EditorColorsScheme getColorsScheme() {
    return myEditorColorsScheme;
  }

  @Override
  public int getPassId() {
    return myPassId;
  }

  private final TransferToEDTQueue<HighlightInfo> myAddHighlighterInEDTQueue = new TransferToEDTQueue<HighlightInfo>("Apply highlighting results", new Processor<HighlightInfo>() {
    @Override
    public boolean process(HighlightInfo info) {
      final EditorColorsScheme colorsScheme = getColorsScheme();
      UpdateHighlightersUtil.addHighlighterToEditorIncrementally(myProject, myDocument, getPsiFile(), myRestrictRange.getStartOffset(),
                                                                 myRestrictRange.getEndOffset(),
                                                                 info, colorsScheme, Pass.UPDATE_ALL, myRanges2markersCache);

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

  void queueHighlightInfo(@NotNull HighlightInfo info) {
    myAddHighlighterInEDTQueue.offer(info);
  }

  void queueDisposeHighlighter(RangeHighlighterEx highlighter) {
    if (highlighter == null) return;
    myDisposeHighlighterInEDTQueue.offer(highlighter);
  }
}
