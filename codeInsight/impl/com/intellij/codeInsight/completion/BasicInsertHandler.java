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

    final int idEndOffset = context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET);
    if (idEndOffset != context.getSelectionEndOffset()){
      if (!CompletionUtil.isOverwrite(item, completionChar)){
        context.getOffsetMap().removeOffset(JavaCompletionUtil.LPAREN_OFFSET);
        context.getOffsetMap().removeOffset(JavaCompletionUtil.RPAREN_OFFSET);
        context.getOffsetMap().removeOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
      }
      else{
        context.editor.getDocument().deleteString(context.getSelectionEndOffset(), idEndOffset);
      }
    }
  }
}
