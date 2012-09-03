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

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;

public abstract class HomeEndHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;
  boolean myIsHomeHandler;

  public HomeEndHandler(final EditorActionHandler originalHandler, boolean isHomeHandler) {
    myOriginalHandler = originalHandler;
    myIsHomeHandler = isHomeHandler;
  }

  @Override
  public void execute(Editor editor, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      final TextRange range = templateState.getCurrentVariableRange();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (range != null && range.getStartOffset() <= caretOffset && caretOffset <= range.getEndOffset()) {
        int offsetToMove = myIsHomeHandler ? range.getStartOffset() : range.getEndOffset();
        if (offsetToMove != caretOffset) {
          editor.getCaretModel().moveToOffset(offsetToMove);
        }
        editor.getSelectionModel().removeSelection();
      } else {
        myOriginalHandler.execute(editor, dataContext);
      }
    } else {
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}
