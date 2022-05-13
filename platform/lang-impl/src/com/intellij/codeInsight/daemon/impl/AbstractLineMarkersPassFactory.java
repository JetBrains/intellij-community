// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

abstract class AbstractLineMarkersPassFactory implements TextEditorHighlightingPassFactoryRegistrar {

  @NotNull
  private static TextRange expandRangeToCoverWholeLines(@NotNull Document document, @NotNull TextRange textRange) {
    return MarkupModelImpl.roundToLineBoundaries(document, textRange.getStartOffset(), textRange.getEndOffset());
  }

  static class Factory implements TextEditorHighlightingPassFactory {
    private final LineMarkersPass.Mode myMode;

    Factory(LineMarkersPass.Mode mode) {
      myMode = mode;
    }

    @NotNull
    @Override
    public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
      TextRange dirtyTextRange = FileStatusMap.getDirtyTextRange(editor, myMode == LineMarkersPass.Mode.SLOW ? Pass.SLOW_LINE_MARKERS : Pass.LINE_MARKERS);
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
}
