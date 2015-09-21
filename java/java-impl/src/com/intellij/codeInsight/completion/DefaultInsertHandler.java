/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CharTailType;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Editor;

/**
 * Please implement InsertHandler instead.
 * To remove after IDEA 16
 */
@Deprecated
public class DefaultInsertHandler implements InsertHandler {

  @Override
  public void handleInsert(final InsertionContext context, LookupElement lookupElement) {
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
      case ',': return TailType.COMMA;
      case ';': return TailType.SEMICOLON;
      case '=': return TailType.EQ;
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
