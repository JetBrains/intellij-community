package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;

/**
 * @author Mike
 */
public class BasicInsertHandler implements InsertHandler {
  public void handleInsert(
      CompletionContext context,
      int startOffset,
      LookupData data,
      LookupItem item,
      boolean signatureSelected,
      char completionChar) {
    //context.shiftOffsets(item.getLookupString().length() - data.prefix.length() - (context.selectionEndOffset - context.startOffset));

    if (context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != context.getSelectionEndOffset()){
      if (!CompletionUtil.isOverwrite(item, completionChar)){
        final int lparenthOffset = -1;
        context.getOffsetMap().addOffset(JavaCompletionUtil.LPAREN_OFFSET, lparenthOffset, true);
        final int rparenthOffset = -1;
        context.getOffsetMap().addOffset(JavaCompletionUtil.RPAREN_OFFSET, rparenthOffset, true);
        final int argListEndOffset = -1;
        context.getOffsetMap().addOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET, argListEndOffset, true);
      }
      else{
        context.editor.getDocument().deleteString(context.getSelectionEndOffset(),
                                                  context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
      }
    }
  }
}
