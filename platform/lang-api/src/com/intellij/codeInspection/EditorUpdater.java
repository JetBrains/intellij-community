// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
   * @param directory parent directory
   * @param name file name
   * @param type file type
   * @param content initial file content
   * @return a newly created editable non-physical file, whose changes will be added to the final command
   */
  @NotNull PsiFile createFile(@NotNull PsiDirectory directory, @NotNull String name, @NotNull FileType type, @NotNull String content);
  
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

  /**
   * Suggest to rename a given element
   * 
   * @param element element to rename
   * @param suggestedNames names to suggest (user is free to type any other name as well)
   */
  void rename(@NotNull PsiNameIdentifierOwner element, @NotNull List<@NotNull String> suggestedNames);
}
