package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class AddNewArrayExpressionFix implements IntentionAction {
  private PsiArrayInitializerExpression myInitializer;

  public AddNewArrayExpressionFix(PsiArrayInitializerExpression initializer) {
    myInitializer = initializer;
  }

  public String getText() {
    PsiExpression expr = myInitializer.getInitializers()[0];
    PsiType type = expr.getType();
    return QuickFixBundle.message("add.new.array.text", type.getPresentableText());
  }

  public String getFamilyName() {
    return QuickFixBundle.message("add.new.array.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myInitializer.isValid() || !myInitializer.getManager().isInProject(myInitializer)) return false;
    PsiExpression[] initializers = myInitializer.getInitializers();
    return initializers.length > 0 && initializers[0].getType() != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiManager manager = file.getManager();
    PsiExpression expr = myInitializer.getInitializers()[0];
    PsiType type = expr.getType();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    @NonNls String text = "new " + type.getPresentableText() + "[]{}";
    PsiNewExpression newExpr = (PsiNewExpression) factory.createExpressionFromText(text, null);
    newExpr.getArrayInitializer().replace(myInitializer);
    newExpr = (PsiNewExpression) manager.getCodeStyleManager().reformat(newExpr);
    myInitializer.replace(newExpr);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
