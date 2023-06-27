// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A helper to perform editor command when building the {@link ModCommand}
 * 
 * @see com.intellij.codeInspection.ModCommands#psiUpdate(PsiElement, BiConsumer)
 */
@ApiStatus.Experimental
public interface ModPsiUpdater extends ModPsiNavigator {
  /**
   * @param e element to update
   * @return a copy of this element inside a writable non-physical file, whose changes are tracked and will be added to the final command.
   * If {@code e} is a {@link PsiDirectory}, a non-physical copy is returned, which allows you to create new files inside that directory.
   * Other write operations on the directory may not work.
   * @param <E> type of the element
   */
  @Contract("null -> null; !null -> !null")
  <E extends PsiElement> E getWritable(E e);
  
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
   * Suggest to rename a given element
   * 
   * @param element element to rename
   * @param suggestedNames names to suggest (user is free to type any other name as well)
   */
  void rename(@NotNull PsiNameIdentifierOwner element, @NotNull List<@NotNull String> suggestedNames);

  /**
   * Cancels any changes done previously, displaying an error message with the given text instead.
   * The subsequent updates will be ignored.
   *
   * @param errorMessage the error message to display
   */
  void cancel(@NotNull @NlsContexts.Tooltip String errorMessage);
}
