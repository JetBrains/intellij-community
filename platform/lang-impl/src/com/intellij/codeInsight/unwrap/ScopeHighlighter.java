/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 26-Sep-2008
 */
package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ScopeHighlighter {
  public static final NotNullFunction<PsiElement,TextRange> NATURAL_RANGER = new NotNullFunction<PsiElement, TextRange>() {
    @NotNull
    @Override
    public TextRange fun(PsiElement dom) {
      return dom.getTextRange();
    }
  };
  private final Editor myEditor;
  private final List<RangeHighlighter> myActiveHighliters = new ArrayList<RangeHighlighter>();
  private final NotNullFunction<PsiElement, TextRange> myRanger;

  public ScopeHighlighter(Editor editor) {
    this(editor, NATURAL_RANGER);
  }

  public ScopeHighlighter(Editor editor, NotNullFunction<PsiElement, TextRange> ranger) {
    myEditor = editor;
    myRanger = ranger;
  }

  public void highlight(PsiElement wholeAffected, List<PsiElement> toExtract) {
    dropHighlight();

    Pair<TextRange, List<TextRange>> ranges = collectTextRanges(wholeAffected, toExtract);

    TextRange wholeRange = ranges.first;

    List<TextRange> rangesToExtract = ranges.second;
    List<TextRange> rangesToRemove = RangeSplitter.split(wholeRange, rangesToExtract);

    for (TextRange r : rangesToRemove) {
      addHighliter(r, UnwrapHandler.HIGHLIGHTER_LEVEL, getTestAttributesForRemoval());
    }
    for (TextRange r : rangesToExtract) {
      addHighliter(r, UnwrapHandler.HIGHLIGHTER_LEVEL, UnwrapHandler.getTestAttributesForExtract());
    }
  }

  private Pair<TextRange, List<TextRange>> collectTextRanges(PsiElement wholeElement, List<PsiElement> elementsToExtract) {
    TextRange affectedRange = getRange(wholeElement);
    List<TextRange> rangesToExtract = new ArrayList<TextRange>();

    for (PsiElement e : elementsToExtract) {
      rangesToExtract.add(getRange(e));
    }

    return Pair.create(affectedRange, rangesToExtract);
  }

  private TextRange getRange(PsiElement e) {
    return myRanger.fun(e);
  }

  private void addHighliter(TextRange r, int level, TextAttributes attr) {
    myActiveHighliters.add(myEditor.getMarkupModel().addRangeHighlighter(
        r.getStartOffset(), r.getEndOffset(), level, attr, HighlighterTargetArea.EXACT_RANGE));
  }

  public void dropHighlight() {
    for (RangeHighlighter h : myActiveHighliters) {
      h.dispose();
    }
    myActiveHighliters.clear();
  }

  private static TextAttributes getTestAttributesForRemoval() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    return manager.getGlobalScheme().getAttributes(EditorColors.DELETED_TEXT_ATTRIBUTES);
  }
}
