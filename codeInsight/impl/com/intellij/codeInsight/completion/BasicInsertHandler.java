package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
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

    if (context.identifierEndOffset != context.selectionEndOffset){
      boolean overwrite = completionChar != 0
        ? completionChar == Lookup.REPLACE_SELECT_CHAR
        : item.getAttribute(LookupItem.OVERWRITE_ON_AUTOCOMPLETE_ATTR) != null;
      if (!overwrite){
        context.lparenthOffset = -1;
        context.rparenthOffset = -1;
        context.argListEndOffset = -1;
      }
      else{
        context.editor.getDocument().deleteString(context.selectionEndOffset, context.identifierEndOffset);
        int shift = -(context.identifierEndOffset - context.selectionEndOffset);
        context.shiftOffsets(shift);
        context.selectionEndOffset = context.identifierEndOffset;
      }
    }
  }
}
