/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceAddAllArrayToCollectionFix implements IntentionAction {
  private final PsiMethodCallExpression myMethodCall;

  public ReplaceAddAllArrayToCollectionFix(final PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  @NotNull
  public String getText() {
    return "Replace " + myMethodCall.getText() + " with " + getCollectionsMethodCall();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiReferenceExpression expression = myMethodCall.getMethodExpression();
    final PsiElement element = expression.resolve();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass collectionsClass = psiFacade.findClass("java.util.Collection", GlobalSearchScope.allScope(project));
      if (collectionsClass != null && InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), collectionsClass, true)) {
        if (Comparing.strEqual(method.getName(), "addAll") && method.getReturnType() == PsiType.BOOLEAN) {
          final PsiParameter[] psiParameters = method.getParameterList().getParameters();
          if (psiParameters.length == 1 &&
              psiParameters[0].getType() instanceof PsiClassType &&
              InheritanceUtil.isInheritorOrSelf(((PsiClassType)psiParameters[0].getType()).resolve(), collectionsClass, true)) {
            final PsiExpressionList list = myMethodCall.getArgumentList();
            final PsiExpression[] expressions = list.getExpressions();
            if (expressions.length == 1) {
              if (expressions[0].getType() instanceof PsiArrayType) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getCollectionsMethodCall(), myMethodCall);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(myMethodCall.replace(toReplace));
  }

  @NonNls
  private String getCollectionsMethodCall() {
    final PsiExpression qualifierExpression = myMethodCall.getMethodExpression().getQualifierExpression();
    return "java.util.Collections.addAll(" +
           (qualifierExpression != null ? qualifierExpression.getText() : "this") +
           ", " +
           myMethodCall.getArgumentList().getExpressions()[0]
               .getText() +
                          ")";
  }

  public boolean startInWriteAction() {
    return true;
  }
}