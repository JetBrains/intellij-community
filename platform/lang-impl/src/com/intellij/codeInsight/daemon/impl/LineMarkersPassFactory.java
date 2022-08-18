// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.MarkupModelImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

final class LineMarkersPassFactory implements TextEditorHighlightingPassFactoryRegistrar, TextEditorHighlightingPassFactory {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this,
                                                 null,
                                                 new int[]{Pass.UPDATE_ALL}, false, Pass.LINE_MARKERS);
  }

  @NotNull
  static TextRange expandRangeToCoverWholeLines(@NotNull Document document, @NotNull TextRange textRange) {
    return MarkupModelImpl.roundToLineBoundaries(document, textRange.getStartOffset(), textRange.getEndOffset());
  }

  @NotNull
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    boolean serializeCodeInsightPasses =
      ((TextEditorHighlightingPassRegistrarImpl)TextEditorHighlightingPassRegistrar.getInstance(file.getProject())).isSerializeCodeInsightPasses();
    LineMarkersPass.Mode myMode = serializeCodeInsightPasses ? LineMarkersPass.Mode.FAST : LineMarkersPass.Mode.ALL;
    return createLineMarkersPass(file, editor, myMode, Pass.LINE_MARKERS);
  }

  @NotNull
  static TextEditorHighlightingPass createLineMarkersPass(@NotNull PsiFile file, @NotNull Editor editor, @NotNull LineMarkersPass.Mode myMode,
                                                          int passId) {
    TextRange dirtyTextRange = FileStatusMap.getDirtyTextRange(editor, passId);
    Document document = editor.getDocument();
    Project project = file.getProject();
    if (dirtyTextRange == null || myMode == LineMarkersPass.Mode.NONE) {
      return new ProgressableTextEditorHighlightingPass.EmptyPass(project, document);
    }

    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
    TextRange priorityBounds = expandRangeToCoverWholeLines(document, visibleRange);
    TextRange restrictRange = expandRangeToCoverWholeLines(document, dirtyTextRange);
    return new LineMarkersPass(project, file, document, priorityBounds, restrictRange, myMode);
  }
}