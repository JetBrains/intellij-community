// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
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
  String mainLookupString();

  /**
   * @return set of additional lookup strings, if necessary
   */
  default Set<String> additionalLookupStrings() {
    return Set.of();
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
   * @param actionContext action context where the completion is performed
   * @param insertionContext an insertion context, which describes how exactly the user invoked the completion
   * @return the command to perform the completion (e.g., insert the lookup string)
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
