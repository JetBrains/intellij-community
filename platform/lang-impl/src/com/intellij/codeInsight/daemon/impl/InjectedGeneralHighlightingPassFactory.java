// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class InjectedGeneralHighlightingPassFactory implements MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
  private final @NotNull List<InjectedLanguageHighlightingRangeReducer> myLanguageHighlightingRangeReducers;

  InjectedGeneralHighlightingPassFactory() {
    myLanguageHighlightingRangeReducers = InjectedLanguageHighlightingRangeReducer.EP_NAME.getExtensionList();
  }

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    boolean serialized = Registry.is("editor.injected.highlighting.serialization.allowed") &&
      ((TextEditorHighlightingPassRegistrarImpl)registrar).isSerializeCodeInsightPasses();
    int[] runAfterCompletionOf = serialized ? new int[]{Pass.UPDATE_ALL} : null;
    int[] runAfterStartingOf = serialized ? null : new int[]{Pass.UPDATE_ALL};
    registrar.registerTextEditorHighlightingPass(this, runAfterCompletionOf, runAfterStartingOf, false, -1);
  }

  @NotNull
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    TextRange textRange = computeRestrictRange(file, editor);
    if (textRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(file.getProject(), editor.getDocument());
    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
    return new InjectedGeneralHighlightingPass(file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), true, visibleRange, editor,
                                               new DefaultHighlightInfoProcessor());
  }

  @Nullable
  private TextRange computeRestrictRange(@NotNull PsiFile file, @NotNull Editor editor) {
    TextRange textRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (textRange == null) return null;
    for (InjectedLanguageHighlightingRangeReducer reducer : myLanguageHighlightingRangeReducers) {
      TextRange reduced = reducer.reduceRange(file, editor);
      if (reduced != null) {
        return reduced;
      }
    }
    return textRange;
  }

  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    return new InjectedGeneralHighlightingPass(file, document, 0, document.getTextLength(), true, new ProperTextRange(0,document.getTextLength()), null,
                                               highlightInfoProcessor);
  }
}
