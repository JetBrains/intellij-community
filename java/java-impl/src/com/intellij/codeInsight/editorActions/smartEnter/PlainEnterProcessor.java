package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 10:54:59 PM
 * To change this template use Options | File Templates.
 */
public class PlainEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    PsiCodeBlock block = getControlStatementBlock(editor.getCaretModel().getOffset(), psiElement);
    if (block != null) {
      PsiElement firstElement = block.getFirstBodyElement();
      if (firstElement == null) firstElement = block.getRBrace();
      editor.getCaretModel().moveToOffset(firstElement != null ?
                                          firstElement.getTextRange().getStartOffset() :
                                          block.getTextRange().getEndOffset());
    }

    getEnterHandler().execute(editor, ((EditorEx)editor).getDataContext());
    return true;
  }

  private EditorActionHandler getEnterHandler() {
    EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(
        IdeActions.ACTION_EDITOR_START_NEW_LINE
    );
    return enterHandler;
  }

  private PsiCodeBlock getControlStatementBlock(int caret, PsiElement element) {
    PsiStatement body = null;
    if (element instanceof PsiIfStatement) {
      body =  ((PsiIfStatement)element).getThenBranch();
      if (caret > body.getTextRange().getEndOffset()) {
        body = ((PsiIfStatement)element).getElseBranch();
      }
    }
    else if (element instanceof PsiWhileStatement) {
      body =  ((PsiWhileStatement)element).getBody();
    }
    else if (element instanceof PsiForStatement) {
      body =  ((PsiForStatement)element).getBody();
    }
    else if (element instanceof PsiForeachStatement) {
      body =  ((PsiForeachStatement)element).getBody();
    }
    else if (element instanceof PsiDoWhileStatement) {
      body =  ((PsiDoWhileStatement)element).getBody();
    }

    return body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : null;
  }
}
