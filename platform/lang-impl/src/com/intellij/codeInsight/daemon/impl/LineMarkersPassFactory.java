// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.MarkupModelImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class LineMarkersPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory, DumbAware {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this,
                                                 null,
                                                 new int[]{Pass.UPDATE_ALL}, false, Pass.LINE_MARKERS);
  }

  static @NotNull TextRange expandRangeToCoverWholeLines(@NotNull Document document, @NotNull TextRange textRange) {
    return MarkupModelImpl.roundToLineBoundaries(document, textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(psiFile.getProject())).isSerializeCodeInsightPasses();
    LineMarkersPass.Mode myMode = serializeCodeInsightPasses ? LineMarkersPass.Mode.FAST : LineMarkersPass.Mode.ALL;
    return createLineMarkersPass(psiFile, editor, myMode, Pass.LINE_MARKERS);
  }

  static @NotNull TextEditorHighlightingPass createLineMarkersPass(@NotNull PsiFile psiFile, @NotNull Editor editor, @NotNull LineMarkersPass.Mode myMode,
                                                                   int passId) {
    TextRange dirtyTextRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, passId);
    Document document = editor.getDocument();
    Project project = psiFile.getProject();
    if (dirtyTextRange == null || myMode == LineMarkersPass.Mode.NONE) {
      return new ProgressableTextEditorHighlightingPass.EmptyPass(project, document);
    }

    HighlightingSession session = HighlightingSessionImpl.getFromCurrentIndicator(psiFile);
    ProperTextRange visibleRange = session.getVisibleRange();
    TextRange priorityBounds = expandRangeToCoverWholeLines(document, visibleRange);
    TextRange restrictRange = expandRangeToCoverWholeLines(document, dirtyTextRange);
    return new LineMarkersPass(project, psiFile, document, priorityBounds, restrictRange, myMode, session);
  }
}