
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class JavaWithBlockSurrounder extends JavaStatementsSurrounder{
  public String getTemplateDescription() {
    return "{ }";
  }

  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    String text = "{\n}";
    PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText(text, null);
    blockStatement = (PsiBlockStatement)codeStyleManager.reformat(blockStatement);

    blockStatement = (PsiBlockStatement)container.addBefore(blockStatement, statements[0]);

    PsiCodeBlock body = blockStatement.getCodeBlock();
    body.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    TextRange range = blockStatement.getFirstChild().getTextRange();
    return new TextRange(range.getEndOffset(), range.getEndOffset());
  }
}