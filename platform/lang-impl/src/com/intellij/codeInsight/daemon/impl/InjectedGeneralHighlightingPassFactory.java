// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    registrar.registerTextEditorHighlightingPass(this, runAfterCompletionOf, runAfterStartingOf, false, Pass.INJECTED_GENERAL);
  }

  @Override
  public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
    TextRange fileRange = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.INJECTED_GENERAL);
    if (fileRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(psiFile.getProject(), editor.getDocument());
    List<TextRange> adjustedRanges = computeReducedRanges(psiFile, editor);
    TextRange restrictRange = computeRestrictRange(adjustedRanges, fileRange);
    boolean updateAll = isUpdatingWholeFile(fileRange, adjustedRanges);
    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(psiFile).getVisibleRange();

    return new InjectedGeneralHighlightingPass(psiFile, editor.getDocument(),
                                               // makes sense if more than one
                                               (adjustedRanges != null && adjustedRanges.size() == 1) ? null : adjustedRanges,
                                               restrictRange.getStartOffset(), restrictRange.getEndOffset(),
                                               updateAll, visibleRange, editor,
                                               true, true, true, HighlightInfoUpdater.getInstance(psiFile.getProject()));
  }

  private static boolean isUpdatingWholeFile(@NotNull TextRange fileRange, @Nullable List<? extends @NotNull TextRange> ranges) {
    if (ranges == null) return true;
    if (ranges.size() == 1) {
      return fileRange.equals(ranges.get(0));
    }

    return false;
  }

  /**
   * Restrict range - the overall area on which highlighting would be triggered.
   * All reducedRanges are parts of restrictRange
   */
  private static @NotNull TextRange computeRestrictRange(@Nullable List<? extends @NotNull TextRange> reduced, @NotNull TextRange fileRange) {
    if (reduced == null) return fileRange;
    if (reduced.size() == 1) {
      return reduced.get(0);
    }
    int startOffSet = fileRange.getEndOffset();
    int endOffSet = fileRange.getStartOffset();
    for (TextRange range : reduced) {
      startOffSet = Math.min(startOffSet, range.getStartOffset());
      endOffSet = Math.max(endOffSet, range.getEndOffset());
    }

    return TextRange.create(startOffSet, endOffSet);
  }

  private @Nullable List<TextRange> computeReducedRanges(@NotNull PsiFile file, @NotNull Editor editor) {
    for (InjectedLanguageHighlightingRangeReducer reducer : myLanguageHighlightingRangeReducers) {
      List<TextRange> reduced = reducer.reduceRange(file, editor);
      if (reduced != null && !reduced.isEmpty()) {
        return reduced;
      }
    }
    return null;
  }


  @Override
  public TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                               @NotNull Document document,
                                                               @NotNull HighlightInfoProcessor highlightInfoProcessor) {
    return new InjectedGeneralHighlightingPass(file, document, null, 0, document.getTextLength(), true, new ProperTextRange(0,document.getTextLength()), null,
                                               true, true, true, HighlightInfoUpdater.EMPTY);
  }
}
