// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;

/**
 * An item in the code completion list
 */
@NotNullByDefault
public interface CompletionItem {
  /**
   * @return the string searched for
   */
  @NlsSafe
  String mainLookupString();

  /**
   * @return set of additional lookup strings, if necessary
   */
  default Set<@NlsSafe String> additionalLookupStrings() {
    return Set.of();
  }

  /**
   * @return true if the element is still valid (e.g., all the PsiElement's it refers to are still valid)
   */
  default boolean isValid() {
    return true;
  }

  /**
   * @return a context object which could be used for item weighing
   */
  Object contextObject();

  /**
   * @return a presentation of the completion item
   */
  CompletionItemPresentation presentation();

  /**
   * @return explicit item priority; can be positive or negative. Default is 0.
   */
  default double priority() {
    return 0;
  }

  /**
   * @param actionContext action context where the completion is performed. 
   *                      The selection range denotes the prefix text inserted during the current completion session.
   *                      The command must ignore it, as at the time it will be applied, the selection range will be deleted. 
   * @param insertionContext an insertion context, which describes how exactly the user invoked the completion
   * @return the command to perform the completion (e.g., insert the lookup string). The command must assume that the
   * selection range is already deleted.
   */
  ModCommand perform(ActionContext actionContext, InsertionContext insertionContext);

  /**
   * Context for the item insertion
   * 
   * @param mode whether to insert or overwrite the existing text
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
