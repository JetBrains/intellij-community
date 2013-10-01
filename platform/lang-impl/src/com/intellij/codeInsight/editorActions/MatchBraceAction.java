package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
