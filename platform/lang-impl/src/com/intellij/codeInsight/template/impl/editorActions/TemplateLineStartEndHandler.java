/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TemplateLineStartEndHandler extends EditorActionHandler {
  private final EditorActionHandler myOriginalHandler;
  private final boolean myIsHomeHandler;
  private final boolean myWithSelection;

  public TemplateLineStartEndHandler(final EditorActionHandler originalHandler, boolean isHomeHandler, boolean withSelection) {
    super(true);
    myOriginalHandler = originalHandler;
    myIsHomeHandler = isHomeHandler;
    myWithSelection = withSelection;
  }

  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      TextRange range = templateState.getCurrentVariableRange();
      int caretOffset = editor.getCaretModel().getOffset();
      if (range != null && range.containsOffset(caretOffset)) return true;
    }
    return myOriginalHandler.isEnabled(editor, caret, dataContext);
  }

  @Override
  protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      final TextRange range = templateState.getCurrentVariableRange();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (range != null && shouldStayInsideVariable(range, caretOffset)) {
        int selectionOffset = editor.getSelectionModel().getLeadSelectionOffset();
        int offsetToMove = myIsHomeHandler ? range.getStartOffset() : range.getEndOffset();
        editor.getCaretModel().moveToOffset(offsetToMove);
        EditorModificationUtil.scrollToCaret(editor);
        if (myWithSelection) {
          editor.getSelectionModel().setSelection(selectionOffset, offsetToMove);
        }
        else {
          editor.getSelectionModel().removeSelection();
        }
        return;
      }
    }
    myOriginalHandler.execute(editor, caret, dataContext);
  }

  private boolean shouldStayInsideVariable(TextRange varRange, int caretOffset) {
    return varRange.containsOffset(caretOffset) &&
           caretOffset != (myIsHomeHandler ? varRange.getStartOffset() : varRange.getEndOffset());
  }
}
