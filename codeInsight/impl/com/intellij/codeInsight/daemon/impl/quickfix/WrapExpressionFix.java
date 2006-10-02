package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class WrapExpressionFix implements IntentionAction {

  private PsiExpression myExpression;
  private PsiClassType myExpectedType;

  public WrapExpressionFix(PsiClassType expectedType, PsiExpression expression) {
    myExpression = expression;
    myExpectedType = expectedType;
  }

  @NotNull
  public String getText() {
    PsiMethod wrapper = findWrapper(myExpression.getType(), myExpectedType);
    PsiClass aClass = wrapper.getContainingClass();
    String methodPresentation = aClass.getName() + "." + wrapper.getName();
    return QuickFixBundle.message("wrap.expression.using.static.accessor.text", methodPresentation);
  }

  private static PsiMethod findWrapper(PsiType type, PsiClassType expectedType) {
    PsiClass aClass = expectedType.resolve();
    if (aClass != null) {
      PsiMethod[] methods = aClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.STATIC) &&
            method.getParameterList().getParameters().length == 1 &&
            method.getParameterList().getParameters()[0].getType().equals(type) &&
            method.getReturnType() != null &&
            expectedType.equals(method.getReturnType())) {
          return method;
        }
      }
    }

    return null;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.expression.using.static.accessor.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myExpression.isValid() || !myExpression.getManager().isInProject(myExpression) ||
        !myExpectedType.isValid() || myExpression.getType() == null) return false;
    return findWrapper(myExpression.getType(), myExpectedType) != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiMethod wrapper = findWrapper(myExpression.getType(), myExpectedType);
    PsiElementFactory factory = file.getManager().getElementFactory();
    @NonNls String methodCallText = "Foo." + wrapper.getName() + "()";
    PsiMethodCallExpression call = (PsiMethodCallExpression) factory.createExpressionFromText(methodCallText,
                                                                                              null);
    call.getArgumentList().add(myExpression);
    ((PsiReferenceExpression) call.getMethodExpression().getQualifierExpression()).bindToElement(
      wrapper.getContainingClass());
    myExpression.replace(call);
  }

  public boolean startInWriteAction() {
    return true;
  }

  public static void registerWrapAction (JavaResolveResult[] candidates, PsiExpression[] expressions, HighlightInfo highlightInfo) {
    PsiClassType expectedType = null;
    PsiExpression expr = null;

    nextMethod:
    for (int i = 0; i < candidates.length && expectedType == null; i++) {
      JavaResolveResult candidate = candidates[i];
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      final PsiElement element = candidate.getElement();
      assert element != null;
      PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();
      if (parameters.length != expressions.length) continue;
      for (int j = 0; j < expressions.length; j++) {
        PsiExpression expression = expressions[j];
        if (expression.getType() != null) {
          PsiType paramType = parameters[j].getType();
          paramType = substitutor != null ? substitutor.substitute(paramType) : paramType;
          if (paramType.isAssignableFrom(expression.getType())) continue;
          if (paramType instanceof PsiClassType) {
            if (expectedType == null && findWrapper(expression.getType(), (PsiClassType) paramType) != null) {
              expectedType = (PsiClassType) paramType;
              expr = expression;
            } else {
              expectedType = null;
              expr = null;
              continue nextMethod;
            }
          }
        }
      }
    }

    if (expectedType != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, expr.getTextRange(), new WrapExpressionFix(expectedType, expr), null, null);
    }
  }

}
