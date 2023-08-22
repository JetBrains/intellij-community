// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class SurroundWithArrayFix implements ModCommandAction {
  private final PsiCall myMethodCall;
  @Nullable private final PsiExpression myExpression;
  private boolean boxing;

  public SurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
    myMethodCall = methodCall;
    myExpression = expression;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("surround.with.array.initialization");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return getExpression(context.findLeaf()) == null ? null :
           Presentation.of(getFamilyName()).withFixAllOption(this).withPriority(PriorityAction.Priority.HIGH);
  }

  @Nullable
  protected PsiExpression getExpression(PsiElement element) {
    if (myMethodCall == null || !myMethodCall.isValid()) {
      return myExpression == null || !myExpression.isValid() ? null : myExpression;
    }
    if (element == null) return null;
    final PsiMethod method = myMethodCall.resolveMethod();
    if (method != null) {
      return checkMethod(element, method);
    } else if (myMethodCall instanceof PsiMethodCallExpression){
      final Collection<PsiElement> psiElements = TargetElementUtil.getInstance()
        .getTargetCandidates(((PsiMethodCallExpression)myMethodCall).getMethodExpression());
      for (PsiElement psiElement : psiElements) {
        if (psiElement instanceof PsiMethod) {
          final PsiExpression expression = checkMethod(element, (PsiMethod)psiElement);
          if (expression != null) return expression;
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiExpression checkMethod(final PsiElement element, final PsiMethod psiMethod) {
    final PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = myMethodCall.getArgumentList();
    int idx = 0;
    for (PsiExpression expression : argumentList.getExpressions()) {
      if (element != null && PsiTreeUtil.isAncestor(expression, element, false)) {
        if (psiParameters.length > idx) {
          final PsiType paramType = psiParameters[idx].getType();
          if (paramType instanceof PsiArrayType && !(paramType instanceof PsiEllipsisType)) {
            final PsiType expressionType = TypeConversionUtil.erasure(expression.getType());
            if (expressionType != null && PsiTypesUtil.isDenotableType(expressionType, element) && expressionType != PsiTypes.nullType() &&
                expressionType.getArrayDimensions() < paramType.getArrayDimensions()) {
              final PsiType componentType = ((PsiArrayType)paramType).getComponentType();
              if (TypeConversionUtil.isAssignable(componentType, expressionType)) {
                boxing = !(componentType instanceof PsiPrimitiveType) && expressionType instanceof PsiPrimitiveType;
                return expression;
              }
              final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
              if (ArrayUtilRt.find(psiMethod.getTypeParameters(), psiClass) != -1) {
                for (PsiClassType superType : psiClass.getSuperTypes()) {
                  if (TypeConversionUtil.isAssignable(superType, expressionType)) {
                    boxing = !(componentType instanceof PsiPrimitiveType) && expressionType instanceof PsiPrimitiveType;
                    return expression;
                  }
                }
              }
            }
          }
        }
      }
      idx++;
    }
    return null;
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    Project project = context.project();
    PsiElement element = context.findLeaf();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression expression = getExpression(element);
    assert expression != null;
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getArrayCreation(expression, boxing), element);
    return ModCommand.psiUpdate(expression, e -> JavaCodeStyleManager.getInstance(project).shortenClassReferences(e.replace(toReplace)));
  }

  @NonNls
  private static String getArrayCreation(@NotNull PsiExpression expression, boolean boxing) {
    final PsiType expressionType = expression.getType();
    assert expressionType != null;
    final PsiType arrayComponentType = TypeConversionUtil.erasure(expressionType);
    final String typeText = boxing
                            ? ((PsiPrimitiveType)arrayComponentType).getBoxedTypeName()
                            : arrayComponentType.getCanonicalText();
    return "new " + typeText + "[]{" + expression.getText() + "}";
  }
}