package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author Mike
 */
public interface InsertHandler {

  /**
   * Invoked inside atomic action.
   */
  void handleInsert(
      CompletionContext context,
      int startOffset,
      LookupData data,
      LookupItem item,
      boolean signatureSelected, char completionChar);
}
