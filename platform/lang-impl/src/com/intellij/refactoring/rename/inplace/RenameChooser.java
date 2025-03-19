// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUsagesCollector;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class RenameChooser {
  private static final @NonNls String CODE_OCCURRENCES = "rename.string.select.code.occurrences";
  private static final @NonNls String ALL_OCCURRENCES = "rename.string.select.all.occurrences";
  public static final Key<Boolean> CHOOSE_ALL_OCCURRENCES_IN_TEST = Key.create("RenameChooser.CHOOSE_ALL_OCCURRENCES_IN_TEST");
  private final Set<RangeHighlighter> myRangeHighlighters = new HashSet<>();
  private final Editor myEditor;

  RenameChooser(Editor editor) {
    myEditor = editor;
  }

  protected abstract void runRenameTemplate(Collection<Pair<PsiElement, TextRange>> stringUsages);

  public void showChooser(final Collection<? extends PsiReference> refs,
                          final Collection<Pair<PsiElement, TextRange>> stringUsages) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runRenameTemplate(TestModeFlags.is(CHOOSE_ALL_OCCURRENCES_IN_TEST) ? stringUsages : new ArrayList<>());
      return;
    }

    JBPopupFactory.getInstance().createPopupChooserBuilder(List.of(CODE_OCCURRENCES, ALL_OCCURRENCES))
      .setItemSelectedCallback(selectedValue -> {
        if (selectedValue == null) return;
        dropHighlighters();
        final MarkupModel markupModel = myEditor.getMarkupModel();

        if (selectedValue.equals(ALL_OCCURRENCES)) {
          for (Pair<PsiElement, TextRange> pair : stringUsages) {
            final TextRange textRange = pair.second.shiftRight(pair.first.getTextOffset());
            final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
              EditorColors.SEARCH_RESULT_ATTRIBUTES, textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
              HighlighterTargetArea.EXACT_RANGE);
            myRangeHighlighters.add(rangeHighlighter);
          }
        }

        for (PsiReference reference : refs) {
          final TextRange textRange = reference.getAbsoluteRange();
          final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
            EditorColors.SEARCH_RESULT_ATTRIBUTES, textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1,
            HighlighterTargetArea.EXACT_RANGE);
          myRangeHighlighters.add(rangeHighlighter);
        }
      })
      .setTitle(RefactoringBundle.message("rename.string.occurrences.found.title"))
      .setRenderer(SimpleListCellRenderer.create("", RefactoringBundle::message))
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChosenCallback((selectedValue) -> {
        RenameUsagesCollector.localSearchInCommentsEvent.log(ALL_OCCURRENCES.equals(selectedValue));
        runRenameTemplate(ALL_OCCURRENCES.equals(selectedValue) ? stringUsages : new ArrayList<>());
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          dropHighlighters();
        }
      })
      .createPopup().showInBestPositionFor(myEditor);
  }



  private void dropHighlighters() {
    for (RangeHighlighter highlight : myRangeHighlighters) {
      highlight.dispose();
    }
    myRangeHighlighters.clear();
  }
}
