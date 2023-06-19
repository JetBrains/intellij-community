// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * A helper to perform editor command when building the {@link com.intellij.modcommand.ModCommand}
 * 
 * @see ModCommands#psiUpdate(PsiElement, BiConsumer) 
 */
@ApiStatus.Experimental
public interface EditorUpdater {
  /**
   * @param e element to update
   * @return a copy of this element inside a writable non-physical file, whose changes are tracked and will be added to the final command
   * @param <E> type of the element
   */
  @Contract(value = "null -> null; !null -> !null")
  <E extends PsiElement> E getWritable(E e);
  
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
   * Highlight given element as a search result
   * 
   * @param element element to select
   */
  default void highlight(@NotNull PsiElement element) {
    highlight(element, EditorColors.SEARCH_RESULT_ATTRIBUTES);
  }
  
  /**
   * Highlight given element
   * 
   * @param element element to select
   * @param attributesKey attributes to use for highlighting
   */
  void highlight(@NotNull PsiElement element, @NotNull TextAttributesKey attributesKey);

  /**
   * Selects given range
   * 
   * @param range range to select
   * @param attributesKey attributes to use for highlighting
   */
  void highlight(@NotNull TextRange range, @NotNull TextAttributesKey attributesKey);

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
}
