package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;

/**
 * @author Mike
 */
public interface InsertHandler {

  /**
   * Invoked inside atomic action.
   */
  void handleInsert(InsertionContext context, LookupElement item);
}
