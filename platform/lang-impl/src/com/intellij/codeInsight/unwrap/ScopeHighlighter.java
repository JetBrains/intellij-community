// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ScopeHighlighter {
  public static final NotNullFunction<PsiElement,TextRange> NATURAL_RANGER = dom -> dom.getTextRange();

  private final @NotNull Editor myEditor;
  private final @NotNull List<RangeHighlighter> myActiveHighliters = new ArrayList<>();
  private final @NotNull NotNullFunction<? super PsiElement, ? extends TextRange> myRanger;

  public ScopeHighlighter(@NotNull Editor editor) {
    this(editor, NATURAL_RANGER);
  }

  public ScopeHighlighter(@NotNull Editor editor, @NotNull NotNullFunction<? super PsiElement, ? extends TextRange> ranger) {
    myEditor = editor;
    myRanger = ranger;
  }

  public void highlight(@NotNull PsiElement wholeAffected, @NotNull List<? extends PsiElement> toExtract) {
    Pair<TextRange, List<TextRange>> ranges = collectTextRanges(wholeAffected, toExtract);

    highlight(ranges);
  }

  public void highlight(@NotNull Pair<TextRange, List<TextRange>> ranges) {
    dropHighlight();

    TextRange wholeRange = ranges.first;

    List<TextRange> rangesToExtract = ranges.second;
    List<TextRange> rangesToRemove = RangeSplitter.split(wholeRange, rangesToExtract);

    addHighlights(rangesToRemove, EditorColors.DELETED_TEXT_ATTRIBUTES);
    addHighlights(rangesToExtract, EditorColors.SEARCH_RESULT_ATTRIBUTES);
  }

  public void addHighlights(List<? extends TextRange> ranges, TextAttributesKey key) {
    for (TextRange r : ranges) {
      addHighlighter(r, UnwrapHandler.HIGHLIGHTER_LEVEL, key);
    }
  }

  private Pair<TextRange, List<TextRange>> collectTextRanges(PsiElement wholeElement, List<? extends PsiElement> elementsToExtract) {
    TextRange affectedRange = getRange(wholeElement);
    List<TextRange> rangesToExtract = new ArrayList<>();

    for (PsiElement e : elementsToExtract) {
      rangesToExtract.add(getRange(e));
    }

    return Pair.create(affectedRange, rangesToExtract);
  }

  private TextRange getRange(PsiElement e) {
    return myRanger.fun(e);
  }

  private void addHighlighter(TextRange r, int level, TextAttributesKey key) {
    MarkupModel markupModel = myEditor.getMarkupModel();
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(key, r.getStartOffset(), r.getEndOffset(), level,
                                                                   HighlighterTargetArea.EXACT_RANGE);
    myActiveHighliters.add(highlighter);
  }

  public void dropHighlight() {
    for (RangeHighlighter h : myActiveHighliters) {
      h.dispose();
    }
    myActiveHighliters.clear();
  }
}
