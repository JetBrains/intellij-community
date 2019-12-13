// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.lookup.CommaTailType;
import com.intellij.codeInsight.lookup.EqTailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please implement InsertHandler instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
public class DefaultInsertHandler implements InsertHandler {

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement lookupElement) {
    context.commitDocument();

    TailType tailType = getTailType(context.getCompletionChar(), (LookupItem)lookupElement);

    final Editor editor = context.getEditor();
    editor.getCaretModel().moveToOffset(context.getSelectionEndOffset());
    tailType.processTail(editor, context.getSelectionEndOffset());
    editor.getSelectionModel().removeSelection();

    if (tailType == TailType.DOT || context.getCompletionChar() == '.') {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(editor, null);
    }
  }

  private static TailType getTailType(final char completionChar, LookupItem item){
    switch(completionChar){
      case '.': return new CharTailType('.', false);
      case ',': return CommaTailType.INSTANCE;
      case ';': return TailType.SEMICOLON;
      case '=': return EqTailType.INSTANCE;
      case ' ': return TailType.SPACE;
      case ':': return TailType.CASE_COLON; //?
      case '<':
      case '>':
      case '\"':
    }
    final TailType attr = item.getTailType();
    return attr == TailType.UNKNOWN ? TailType.NONE : attr;
  }
}
