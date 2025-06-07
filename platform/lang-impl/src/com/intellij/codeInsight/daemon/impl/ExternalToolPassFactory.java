// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.lang.ExternalLanguageAnnotators;
import com.intellij.lang.Language;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class ExternalToolPassFactory implements TextEditorHighlightingPassFactory, MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar,
                                               DumbAware {
  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    // start after PostHighlightingPass completion since it could report errors that can prevent us to run
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, true, Pass.EXTERNAL_TOOLS);
  }

  @Override
  public @Nullable TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.EXTERNAL_TOOLS) == null ? null : psiFile.getTextRange();
    if (textRange == null || !externalAnnotatorsDefined(psiFile)) {
      return null;
    }
    return new ExternalToolPass(psiFile, editor.getDocument(), editor, textRange.getStartOffset(), textRange.getEndOffset(), HighlightInfoProcessor.getEmpty());
  }

  private static boolean externalAnnotatorsDefined(@NotNull PsiFile psiFile) {
    for (Language language : psiFile.getViewProvider().getLanguages()) {
      List<ExternalAnnotator<?,?>> externalAnnotators = ExternalLanguageAnnotators.allForFile(language, psiFile);
      if (!externalAnnotators.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @Nullable TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile psiFile,
                                                                         @NotNull Document document,
                                                                         @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    TextRange range = psiFile.getTextRange();
    if (range == null || !externalAnnotatorsDefined(psiFile)) {
      return null;
    }
    return new ExternalToolPass(psiFile, document, null, range.getStartOffset(), range.getEndOffset(), highlightInfoProcessor);
  }
}
