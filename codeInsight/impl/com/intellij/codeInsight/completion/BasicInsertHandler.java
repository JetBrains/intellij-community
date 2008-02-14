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

    if (context.getIdentifierEndOffset() != context.getSelectionEndOffset()){
      if (!CompletionUtil.isOverwrite(item, completionChar)){
        context.setLparenthOffset(-1);
        context.setRparenthOffset(-1);
        context.setArgListEndOffset(-1);
      }
      else{
        context.editor.getDocument().deleteString(context.getSelectionEndOffset(), context.getIdentifierEndOffset());
      }
    }
  }
}
