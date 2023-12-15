// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A helper to perform editor command when building the {@link ModCommand}. This helper is available inside the consumer provided by
 * {@link ModCommand#psiUpdate} overloads. It allows you to retrieve writable copies of physical files,
 * and record various navigation and template operations that will appear in the final command. It also has a concept of 'current file',
 * which is the file to perform any interactive operations in. By default, the current file is a file where starting element
 * is located, or which action context points at, depending on the used {@code psiUpdate} overload.
 * 
 * @see ModCommand#psiUpdate(PsiElement, BiConsumer)
 * @see ModCommand#psiUpdate(ActionContext, Consumer) 
 */
@ApiStatus.Experimental
public interface ModPsiUpdater extends ModPsiNavigator {
  /**
   * Returns a copy of this element inside a writable non-physical file, whose changes are tracked and will be added to the final command.
   * If {@code element} is a {@link PsiDirectory}, a non-physical copy is returned, which allows you to create new files inside that directory.
   * Other write operations on the directory may not work.
   * <p>
   * This method must be called before any writes to the returned non-physical file are performed. Otherwise, 
   * the copy of original element may not exist anymore. It's better to get all the writable copies before doing any writes.  
   * 
   * @param element element to update
   * @param <E> type of the element
   * @return a copy of this element inside a writable non-physical file.
   * @throws IllegalStateException if the element is located inside the file, whose copy was already modified.
   */
  @Contract("null -> null; !null -> !null")
  <E extends PsiElement> E getWritable(E element) throws IllegalStateException;
  
  /**
   * Highlights given element as a search result. Does nothing when executed non-interactively.
   * The current file may be changed if the element is located in the different file.
   * 
   * @param element element to select
   */
  default void highlight(@NotNull PsiElement element) {
    highlight(element, EditorColors.SEARCH_RESULT_ATTRIBUTES);
  }
  
  /**
   * Highlights given element. Does nothing when executed non-interactively.
   * The current file may be changed if the element is located in the different file.
   * 
   * @param element element to select
   * @param attributesKey attributes to use for highlighting
   */
  void highlight(@NotNull PsiElement element, @NotNull TextAttributesKey attributesKey);

  /**
   * Highlights given range inside the current file. Does nothing when executed non-interactively.
   * 
   * @param range range to select
   * @param attributesKey attributes to use for highlighting
   */
  void highlight(@NotNull TextRange range, @NotNull TextAttributesKey attributesKey);

  /**
   * Displays the UI to rename a given element. Does nothing when executed non-interactively. 
   * The current file may be changed if the element is located in the different file.
   * 
   * @param element element to rename
   * @param suggestedNames names to suggest (user is free to type any other name as well)
   */
  void rename(@NotNull PsiNameIdentifierOwner element, @NotNull List<@NotNull String> suggestedNames);

  /**
   * Displays the UI to rename a given element. Does nothing when executed non-interactively. 
   * The current file may be changed if the element is located in the different file.
   *
   * @param element element to rename
   * @param nameIdentifier a descendant of element which represents the name identifier leaf.
   *                       Supplying null is discouraged, as some executors may not support it, and in this case rename will not start
   * @param suggestedNames names to suggest (user is free to type any other name as well)
   */
  void rename(@NotNull PsiNamedElement element, @Nullable PsiElement nameIdentifier, @NotNull List<@NotNull String> suggestedNames);

  /**
   * Tracks subsequent changes in a given declaration (e.g., method) and produce a command to 
   * update the references to the declaration (maybe displaying UI).
   * The current file may be changed if the declaration is located in the different file.
   * <p>
   *   This method must be called before you actually update the declaration (e.g., change method parameters).
   * </p>
   * 
   * @param declaration declaration to track
   */
  @ApiStatus.Experimental
  void trackDeclaration(@NotNull PsiElement declaration);

  /**
   * @return a builder that allows you to create a template.
   */
  @NotNull ModTemplateBuilder templateBuilder();
  
  /**
   * Cancels any changes done previously, displaying an error message with the given text instead.
   * The subsequent updates will be ignored.
   *
   * @param errorMessage the error message to display
   */
  void cancel(@NotNull @NlsContexts.Tooltip String errorMessage);

  /**
   * Records conflicts. All the recorded conflicts will be shown before any other modifications.
   * If user cancels the conflict view, then no actual modification will be done.
   * <p>
   *   Subsequent calls of this method add new conflicts instead of replacing the old ones.
   * </p>
   * <p>
   *   The PSI elements in the map must be physical elements or their writable copies obtained by
   *   previous {@link #getWritable(PsiElement)} call. No actual PSI modifications should be done prior to
   *   this call.
   * </p>
   *
   * @param conflicts conflicts to show.
   */
  void showConflicts(@NotNull Map<@NotNull PsiElement, ModShowConflicts.@NotNull Conflict> conflicts);

  /**
   * Display message
   * 
   * @param message message to display
   */
  void message(@NotNull @NlsContexts.Tooltip String message);

  /**
   * Selects given element. Does nothing when executed non-interactively.
   * The current file may be changed if the element is located in the different file.
   *
   * @param element element to select
   */
  @Override
  void select(@NotNull PsiElement element);

  /**
   * Selects given range in the current file. Does nothing when executed non-interactively.
   *
   * @param range range to select
   */
  @Override
  void select(@NotNull TextRange range);

  /**
   * Navigates to a given offset in the current file. Does nothing when executed non-interactively.
   *
   * @param offset offset to move to
   */
  @Override
  void moveCaretTo(int offset);

  /**
   * Navigates to a given element. Does nothing when executed non-interactively.
   * The current file may be changed if the element is located in the different file.
   *
   * @param element element to navigate to
   */
  @Override
  void moveCaretTo(@NotNull PsiElement element);

  /**
   * Moves caret to a previous occurrence of character ch in the current file. Do nothing if no such occurrence is found,
   * or when executed non-interactively.
   *
   * @param ch character to find
   */
  @Override
  @ApiStatus.Experimental
  void moveToPrevious(char ch);

  /**
   * @return current caret offset inside the current file. It may be based on the previous result of {@link #moveCaretTo(int)} 
   * or similar methods. The initial caret offset is taken from {@link ActionContext} 
   * if {@link ModCommand#psiUpdate(ActionContext, Consumer)} was used. Otherwise, it's zero. 
   */
  @Override
  int getCaretOffset();
}
