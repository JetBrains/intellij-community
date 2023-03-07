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

import java.util.Collection;
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
    TextRange fileRange = FileStatusMap.getDirtyTextRange(editor, Pass.UPDATE_ALL);
    if (fileRange == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(file.getProject(), editor.getDocument());
    Collection<TextRange> adjustedRanges = computeReducedRanges(file, editor);
    TextRange restrictRange = computeRestrictRange(adjustedRanges, fileRange);
    boolean updateAll = isUpdatingWholeFile(fileRange, adjustedRanges);
    ProperTextRange visibleRange = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();

    return new InjectedGeneralHighlightingPass(file, editor.getDocument(),
                                               // makes sense if more than one
                                               (adjustedRanges != null && adjustedRanges.size() == 1) ? null : adjustedRanges,
                                               restrictRange.getStartOffset(), restrictRange.getEndOffset(),
                                               updateAll, visibleRange, editor, new DefaultHighlightInfoProcessor());
  }

  private static boolean isUpdatingWholeFile(@NotNull TextRange fileRange, @Nullable Collection<@NotNull TextRange> ranges) {
    if (ranges == null) return true;
    if (ranges.size() == 1) {
      TextRange range = ranges.iterator().next();
      return fileRange.equalsToRange(range.getStartOffset(), range.getEndOffset());
    }

    return false;
  }

  /**
   * Restrict range - the overall area on which highlighting would be triggered.
   * All reducedRanges are parts of restrictRange
   */
  @NotNull
  private static TextRange computeRestrictRange(@Nullable Collection<@NotNull TextRange> reduced, @NotNull TextRange fileRange) {
    if (reduced == null) return fileRange;
    if (reduced.size() == 1) {
      TextRange first = reduced.iterator().next();
      return first.equalsToRange(fileRange.getStartOffset(), fileRange.getEndOffset()) ? fileRange : first;
    }
    int startOffSet = fileRange.getEndOffset();
    int endOffSet = fileRange.getStartOffset();
    for (TextRange range : reduced) {
      startOffSet = Math.min(startOffSet, range.getStartOffset());
      endOffSet = Math.max(endOffSet, range.getEndOffset());
    }

    return TextRange.create(startOffSet, endOffSet);
  }

  @Nullable
  private Collection<TextRange> computeReducedRanges(@NotNull PsiFile file, @NotNull Editor editor) {
    for (InjectedLanguageHighlightingRangeReducer reducer : myLanguageHighlightingRangeReducers) {
      Collection<TextRange> reduced = reducer.reduceRange(file, editor);
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
                                               highlightInfoProcessor);
  }
}
