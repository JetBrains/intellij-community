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

package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.text.CharArrayUtil;

/**
 * @author max
 */
public class SmartEnterAction extends EditorAction {
  public SmartEnterAction() {
    super(new Handler());
  }

  @Override
  protected Editor getEditor(final DataContext dataContext) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return null;
    Project project = editor.getProject();
    if (project == null) project = PlatformDataKeys.PROJECT.getData(dataContext);
    return project == null ? null : BaseCodeInsightAction.getInjectedEditor(project, editor);
  }

  private static class Handler extends EditorWriteActionHandler {
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      return getEnterHandler().isEnabled(editor, dataContext);
    }

    public void executeWriteAction(Editor editor, DataContext dataContext) {
      final Document doc = editor.getDocument();
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null || editor.isOneLineMode()) {
        plainEnter(editor, dataContext);
        return;
      }
      final int caretOffset = editor.getCaretModel().getOffset();
      if (isInPreceedingBlanks(editor)) {
        final int caretLine = doc.getLineNumber(caretOffset);
        if (caretLine > 0) {
          int prevLineEnd = doc.getLineEndOffset(caretLine - 1);
          editor.getCaretModel().moveToOffset(prevLineEnd);
        }
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        enterHandler.execute(editor, dataContext);
        return;
      }

      PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (psiFile == null) {
        plainEnter(editor, dataContext);
        return;
      }

      if (EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, caretOffset, psiFile.getFileType())) {
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        enterHandler.execute(editor, dataContext);
        return;
      }

      final Language language = PsiUtilBase.getLanguageInEditor(editor, project);
      boolean processed = false;
      if (language != null) {
        final SmartEnterProcessor processor = SmartEnterProcessors.INSTANCE.forLanguage(language);
        if (processor != null && processor.process(project, editor, psiFile)) {
          processed = true;
        }
      } 
      if (!processed) {
        plainEnter(editor, dataContext);
      }
    }

    private static boolean isInPreceedingBlanks(Editor editor) {
      int offset = editor.getCaretModel().getOffset();
      final Document doc = editor.getDocument();
      CharSequence chars = doc.getCharsSequence();
      if (offset == doc.getTextLength() || chars.charAt(offset) == '\n') return false;

      int newLineOffset = CharArrayUtil.shiftBackward(chars, offset - 1, " \t");
      return newLineOffset < 0 || chars.charAt(newLineOffset) == '\n';
    }
  }

  public static void plainEnter(Editor editor, DataContext dataContext) {
    getEnterHandler().execute(editor, dataContext);
  }

  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }
}

