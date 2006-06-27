
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class JavaWithSynchronizedSurrounder extends JavaStatementsSurrounder{
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.synchronized.template");
  }

  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    @NonNls String text = "synchronized(a){\n}";
    PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)factory.createStatementFromText(text, null);
    synchronizedStatement = (PsiSynchronizedStatement)codeStyleManager.reformat(synchronizedStatement);

    synchronizedStatement = (PsiSynchronizedStatement)container.addAfter(synchronizedStatement, statements[statements.length - 1]);

    PsiCodeBlock synchronizedBlock = synchronizedStatement.getBody();
    synchronizedBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    synchronizedStatement = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(synchronizedStatement);
    TextRange range = synchronizedStatement.getLockExpression().getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }
}