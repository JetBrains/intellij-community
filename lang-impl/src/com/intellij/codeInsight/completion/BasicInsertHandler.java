package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;

/**
 * @author Mike
 */
public class BasicInsertHandler<T extends LookupElement> implements InsertHandler<T> {
  public void handleInsert(InsertionContext context, T item) {

    final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
    if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite(item, context.getCompletionChar())) {
      context.getEditor().getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
    }
  }
}
