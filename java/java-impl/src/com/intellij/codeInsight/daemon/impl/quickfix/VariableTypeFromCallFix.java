// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VariableTypeFromCallFix implements IntentionAction {
  private final PsiType myExpressionType;
  private final PsiVariable myVar;

  private VariableTypeFromCallFix(@NotNull PsiType type, @NotNull PsiVariable var) {
    myExpressionType = PsiTypesUtil.removeExternalAnnotations(type);
    myVar = var;
  }

  @Override
  public @NotNull String getText() {
    return QuickFixBundle.message("fix.variable.type.text",
                                  JavaElementKind.fromElement(myVar).lessDescriptive().subject(),
                                  myVar.getName(),
                                  myExpressionType.getPresentableText());
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("fix.variable.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myExpressionType.isValid() && myVar.isValid() && PsiTypesUtil.allTypeParametersResolved(myVar, myExpressionType);
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    var scope = PsiSearchHelper.getInstance(project).getUseScope(myVar);
    var handler = CommonJavaRefactoringUtil.getRefactoringSupport().getChangeTypeSignatureHandler();
    handler.runHighlightingTypeMigrationSilently(project, editor, scope, myVar, myExpressionType);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }


  public static @NotNull List<IntentionAction> getQuickFixActions(@NotNull PsiMethodCallExpression methodCall,
                                                                  @NotNull PsiExpressionList list) {
    final JavaResolveResult result = methodCall.getMethodExpression().advancedResolve(false);
    PsiMethod method = (PsiMethod) result.getElement();
    final PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null) return Collections.emptyList();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != expressions.length) return Collections.emptyList();
    List<IntentionAction> actions = new ArrayList<>();
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression expression = expressions[i];
      PsiType expressionType = expression.getType();
      if (expressionType instanceof PsiPrimitiveType) {
        expressionType = ((PsiPrimitiveType)expressionType).getBoxedType(expression);
      }
      if (expressionType == null) continue;

      final PsiParameter parameter = parameters[i];
      final PsiType formalParamType = parameter.getType();
      final PsiType parameterType = substitutor.substitute(formalParamType);
      if (parameterType.isAssignableFrom(expressionType)) continue;

      final PsiExpression qualifierExpression =  PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression());
      if (qualifierExpression instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolved instanceof PsiVariable) {
          final PsiType varType = ((PsiVariable)resolved).getType();
          final PsiClass varClass = PsiUtil.resolveClassInType(varType);
          final Project project = expression.getProject();
          final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
          if (varClass != null) {
            PsiSubstitutor psiSubstitutor = resolveHelper.inferTypeArguments(varClass.getTypeParameters(),
                                                                                   parameters,
                                                                                   expressions, PsiSubstitutor.EMPTY, resolved,
                                                                                   DefaultParameterTypeInferencePolicy.INSTANCE);
            if (ContainerUtil.exists(psiSubstitutor.getSubstitutionMap().values(),
                                     t -> t != null && t.equalsToText(CommonClassNames.JAVA_LANG_VOID))) {
              continue;
            }
            PsiType appropriateVarType = GenericsUtil.getVariableTypeByExpressionType(
              JavaPsiFacade.getElementFactory(project).createType(varClass, psiSubstitutor));
            if (!varType.equals(appropriateVarType)) {
              PsiMethod resolvedMethod = methodCall.resolveMethod();
              if (resolvedMethod == null) continue;
              PsiType returnMethodType = resolvedMethod.getReturnType();
              if ((PsiUtil.resolveClassInClassTypeOnly(returnMethodType) instanceof PsiTypeParameter returnTypeParameter)) {
                PsiType oldReturnType = methodCall.getType();
                psiSubstitutor = psiSubstitutor.put(returnTypeParameter, oldReturnType);
                appropriateVarType = GenericsUtil.getVariableTypeByExpressionType(
                  JavaPsiFacade.getElementFactory(project).createType(varClass, psiSubstitutor));
                if (varType.equals(appropriateVarType)) {
                  continue;
                }
              }
              actions.add(new VariableTypeFromCallFix(appropriateVarType, (PsiVariable)resolved));
              break;
            }
          }
        }
      }
      actions.addAll(getParameterTypeChangeFixes(method, expression, parameterType));
    }
    return actions;
  }

  private static List<IntentionAction> getParameterTypeChangeFixes(@NotNull PsiMethod method,
                                                                   @NotNull PsiExpression expression,
                                                                   PsiType parameterType) {
    if (!(expression instanceof PsiReferenceExpression)) {
      return Collections.emptyList();
    }
    List<IntentionAction> result = new ArrayList<>();
    if (BaseIntentionAction.canModify(method)) {
      final PsiMethod[] superMethods = method.findDeepestSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        if (!BaseIntentionAction.canModify(superMethod)) return Collections.emptyList();
      }
      final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
      if (resolve instanceof PsiVariable) {
        PsiType varType = ((PsiVariable)resolve).getType();
        if (!varType.equals(GenericsUtil.getVariableTypeByExpressionType(parameterType))) {
          result.addAll(HighlightFixUtil.getChangeVariableTypeFixes((PsiVariable)resolve, parameterType));
        }
      }
    }
    return result;
  }
}
