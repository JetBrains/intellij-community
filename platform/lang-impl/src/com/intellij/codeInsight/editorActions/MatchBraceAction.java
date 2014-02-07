/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import gnu.trove.TIntHashSet;

/**
 * @author Denis Zhdanov
 * @since 10/24/12 11:10 AM
 */
public class MatchBraceAction extends EditorAction {

  private static final TIntHashSet OPEN_BRACES = new TIntHashSet(new int[] {  '(', '[', '{', '<' });
  private static final TIntHashSet CLOSE_BRACES = new TIntHashSet(new int[] { ')', ']', '}', '>' });

  public MatchBraceAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    public MyHandler() {
      super(true);
    }

    @Override
    public void execute(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        return;
      }

      CaretModel caretModel = editor.getCaretModel();
      int offset = caretModel.getOffset();
      CharSequence text = editor.getDocument().getCharsSequence();
      char c = text.charAt(offset);
      if (!OPEN_BRACES.contains(c) && !CLOSE_BRACES.contains(c)) {
        boolean canContinue = false;
        for (offset--; offset >= 0; offset--) {
          c = text.charAt(offset);
          if (OPEN_BRACES.contains(c) || CLOSE_BRACES.contains(c)) {
            canContinue = true;
            caretModel.moveToOffset(offset);
            break;
          }
        }
        if (!canContinue) {
          return;
        }
      }
      
      if (OPEN_BRACES.contains(c)) {
        CodeBlockUtil.moveCaretToCodeBlockEnd(project, editor, false);
      }
      else if (CLOSE_BRACES.contains(c)) {
        CodeBlockUtil.moveCaretToCodeBlockStart(project, editor, false);
      }
    }
  }
}
