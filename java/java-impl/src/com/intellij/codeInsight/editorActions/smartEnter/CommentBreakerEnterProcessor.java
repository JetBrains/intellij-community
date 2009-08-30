package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 2:57:38 PM
 * To change this template use Options | File Templates.
 */
public class CommentBreakerEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (isModified) return false;
    final PsiElement atCaret = psiElement.getContainingFile().findElementAt(editor.getCaretModel().getOffset());
    final PsiComment comment = PsiTreeUtil.getParentOfType(atCaret, PsiComment.class, false);
    if (comment != null) {
      plainEnter(editor);
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        EditorModificationUtil.insertStringAtCaret(editor, "// ");
      }
      return true;
    }
    return false;
  }

  private static void plainEnter(Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

}
