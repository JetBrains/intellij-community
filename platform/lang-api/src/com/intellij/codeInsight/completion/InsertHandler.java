package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;

/**
 * @author Mike
 */
public interface InsertHandler<T extends LookupElement> {

  /**
   * Invoked inside atomic action.
   */
  void handleInsert(InsertionContext context, T item);
}
