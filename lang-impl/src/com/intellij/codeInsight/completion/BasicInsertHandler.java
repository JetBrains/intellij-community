package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author Mike
 */
public class BasicInsertHandler implements InsertHandler {
  public void handleInsert(InsertionContext context, LookupElement item) {

    final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
    if (idEndOffset != context.getSelectionEndOffset() && CompletionUtil.isOverwrite((LookupItem)item, context.getCompletionChar())) {
      context.getEditor().getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
    }
  }
}
