// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ExternalToolPassFactory implements TextEditorHighlightingPassFactory, MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    // start after PostHighlightingPass completion since it could report errors that can prevent us to run
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, true, Pass.EXTERNAL_TOOLS);
  }

  @Override
  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.EXTERNAL_TOOLS) == null ? null : file.getTextRange();
    if (textRange == null || !externalAnnotatorsDefined(file)) {
      return null;
    }
    return new ExternalToolPass(file, editor.getDocument(), editor, textRange.getStartOffset(), textRange.getEndOffset(), new DefaultHighlightInfoProcessor(), false);
  }

  private static boolean externalAnnotatorsDefined(@NotNull PsiFile file) {
    for (Language language : file.getViewProvider().getLanguages()) {
      List<ExternalAnnotator<?,?>> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, file);
      if (!externalAnnotators.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    TextRange range = file.getTextRange();
    if (range == null || !externalAnnotatorsDefined(file)) {
      return null;
    }
    return new ExternalToolPass(file, document, null, range.getStartOffset(), range.getEndOffset(), highlightInfoProcessor, true);
  }
}
