// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/**
 * An item in the code completion list
 */
@NotNullByDefault
public interface ModCompletionItem {
  /**
   * @return the string searched for
   */
  @NlsSafe
  String mainLookupString();

  /**
   * @return set of additional lookup strings, if necessary
   */
  default @Unmodifiable Set<@NlsSafe String> additionalLookupStrings() {
    return Set.of();
  }

  /**
   * @return true if the element is still valid (e.g., all the PsiElement's it refers to are still valid)
   */
  default boolean isValid() {
    return true;
  }

  /**
   * @return a context object. Often, it's a PsiElement associated with the item to be inserted,
   * but it could be a type or any other object. It's used for item weighing, char filters, documentation providers, etc.
   */
  Object contextObject();

  /**
   * @return a presentation of the completion item
   */
  ModCompletionItemPresentation presentation();

  /**
   * @return explicit item priority; can be positive or negative. 
   * Higher priority means the item will appear closer to the top of the list. Default is 0.
   */
  default double priority() {
    return 0;
  }

  /**
   * Generates and returns the {@link ModCommand} necessary to insert this item into the document.
   * <p> 
   * It is strongly recommended to extend {@link PsiUpdateCompletionItem} and override
   * {@link PsiUpdateCompletionItem#update} instead of implementing this method directly.
   * <p>
   * If you implement this method directly and the command modifies text, you must account for the fact that
   * the selection range will already be deleted by the time the command is applied.
   * To handle this, use the {@code copyCleaner} parameter of
   * {@link ModCommand#psiUpdate(ActionContext, java.util.function.Consumer, java.util.function.Consumer)},
   * which lets you adjust the document copy to match the expected state.
   * If the document texts don't match, the command will not be applied.
   * 
   * @param actionContext    action context where the completion is performed.
   *                         The selection range denotes the prefix text inserted during the current completion session.
   *                         The command must ignore it, as at the time it will be applied, the selection range will be deleted.
   * @param insertionContext an insertion context, which describes how exactly the user invoked the completion.
   *                         If a completion character was used, this method must process it (e.g., insert to the document, if necessary).
   * @return the command to perform the completion (e.g., insert the lookup string). The command must assume that the
   * selection range is already deleted.
   */
  ModCommand perform(ActionContext actionContext, InsertionContext insertionContext);

  /**
   * @return the desired behavior when this item is the only one item in the completion popup.
   */
  default AutoCompletionPolicy autoCompletionPolicy() {
    return AutoCompletionPolicy.SETTINGS_DEPENDENT;
  }

  /**
   * Context for the item insertion
   *
   * @param mode               whether to insert or overwrite the existing text
   * @param insertionCharacter the character used to finish the completion
   */
  record InsertionContext(InsertionMode mode, char insertionCharacter) {

  }

  /**
   * Default insertion context for 'insert' mode
   */
  InsertionContext DEFAULT_INSERTION_CONTEXT = new InsertionContext(InsertionMode.INSERT, '\n');

  enum InsertionMode {
    /**
     * Insert mode: we should insert the text to the caret position
     */
    INSERT,

    /**
     * Overwrite mode: we should overwrite the existing text at the caret position
     * The exact behavior (e.g. overwrite to the end of current word) is up to the specific completion item.
     */
    OVERWRITE
  }
}
