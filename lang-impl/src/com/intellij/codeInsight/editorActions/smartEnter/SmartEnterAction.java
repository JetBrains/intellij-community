package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
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

import java.util.List;

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
    return BaseCodeInsightAction.getInjectedEditor(editor.getProject(), editor);
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

      if (EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, caretOffset, psiFile.getFileType())) {
        EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        enterHandler.execute(editor, dataContext);
        return;
      }

      final Language language = PsiUtilBase.getLanguageInEditor(editor, project);
      if (language != null) {
        final List<SmartEnterProcessor> processors = SmartEnterProcessors.INSTANCE.forKey(language);
        if (processors.size() > 0) {
          for (SmartEnterProcessor processor : processors) {
            processor.process(project, editor, psiFile);
          }
        } else {
          plainEnter(editor, dataContext);
        }
      } else {
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

    private void plainEnter(Editor editor, DataContext dataContext) {
      getEnterHandler().execute(editor, dataContext);
    }

    private EditorActionHandler getEnterHandler() {
      return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
    }
  }
}

