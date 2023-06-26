// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface that provides navigation functionality for the editor.
 * Depending on the implementation, the commands can be directly applied to the editor,
 * or can be remembered and executed later.
 */
@ApiStatus.Experimental
public interface ModPsiNavigator {
  /**
   * Selects given element
   *
   * @param element element to select
   */
  void select(@NotNull PsiElement element);

  /**
   * Selects given range
   *
   * @param range range to select
   */
  void select(@NotNull TextRange range);

  /**
   * Navigates to a given offset
   *
   * @param offset offset to move to
   */
  void moveTo(int offset);

  /**
   * Navigates to a given element
   *
   * @param element element to navigate to
   */
  void moveTo(@NotNull PsiElement element);

  /**
   * Moves caret to a previous occurrence of character ch. Do nothing if no such occurrence is found 
   * @param ch character to find
   */
  void moveToPrevious(char ch);

  /**
   * @return current caret offset
   */
  int getCaretOffset();

  static @NotNull ModPsiNavigator fromEditor(@NotNull Editor editor) {
    return new ModPsiNavigator() {
      @Override
      public void select(@NotNull PsiElement element) {
        select(element.getTextRange());
      }

      @Override
      public void select(@NotNull TextRange range) {
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }

      @Override
      public void moveTo(int offset) {
        editor.getCaretModel().moveToOffset(offset);
      }

      @Override
      public void moveTo(@NotNull PsiElement element) {
        moveTo(element.getTextRange().getStartOffset());
      }

      @Override
      public void moveToPrevious(char ch) {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        CharSequence sequence = document.getCharsSequence();
        while (offset > 0 && sequence.charAt(offset) != ch) {
          offset--;
        }
        editor.getCaretModel().moveToOffset(offset);
      }

      @Override
      public int getCaretOffset() {
        return editor.getCaretModel().getOffset();
      }
    };
  }
}
