// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AddReturnFix extends PsiUpdateModCommandAction<PsiParameterListOwner> {

  public AddReturnFix(@NotNull PsiParameterListOwner methodOrLambda) {
    super(methodOrLambda);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.return.statement.text");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiParameterListOwner method) {
    if (!(method.getBody() instanceof PsiCodeBlock body) || body.getRBrace() == null) return null;
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiParameterListOwner method, @NotNull ModPsiUpdater updater) {
    if (invokeSingleExpressionLambdaFix(method)) {
      return;
    }
    String value = suggestReturnValue(method);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    PsiReturnStatement returnStatement = (PsiReturnStatement)factory.createStatementFromText("return " + value + ";", method);
    PsiCodeBlock body = ObjectUtils.tryCast(method.getBody(), PsiCodeBlock.class);
    assert body != null;
    returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

    updater.select(Objects.requireNonNull(returnStatement.getReturnValue()));
  }

  private static String suggestReturnValue(@NotNull PsiParameterListOwner owner) {
    PsiType type;
    if (owner instanceof PsiMethod method) {
      type = method.getReturnType();
    }
    else if (owner instanceof PsiLambdaExpression lambda) {
      type = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    }
    else {
      return JavaKeywords.NULL; // normally shouldn't happen
    }

    // first try to find suitable local variable
    List<PsiVariable> variables = getDeclaredVariables(owner);
    for (PsiVariable variable : variables) {
      PsiType varType = variable.getType();
      if (varType.equals(type)) {
        return variable.getName();
      }
    }
    // then try to find a conversion of local variable to the required type
    for (PsiVariable variable : variables) {
      String conversion = getConversionToType(owner, variable, type, true);
      if (conversion != null) {
        return conversion;
      }
    }
    for (PsiVariable variable : variables) {
      String conversion = getConversionToType(owner, variable, type, false);
      if (conversion != null) {
        return conversion;
      }
    }
    return PsiTypesUtil.getDefaultValueOfType(type, true);
  }

  private static @NonNls String getConversionToType(@NotNull PsiParameterListOwner method,
                                                    @NotNull PsiVariable variable,
                                                    @Nullable PsiType type,
                                                    boolean preciseTypeRequired) {
    PsiType varType = variable.getType();
    if (!(type instanceof PsiArrayType arrayType)) return null;
    PsiType arrayComponentType = arrayType.getComponentType();
    if (!(arrayComponentType instanceof PsiPrimitiveType) &&
        !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter) &&
        InheritanceUtil.isInheritor(varType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      PsiType erasedComponentType = TypeConversionUtil.erasure(arrayComponentType);
      if (!preciseTypeRequired || arrayComponentType.equals(erasedComponentType)) {
        PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(varType, method.getResolveScope());
        if (collectionItemType != null && erasedComponentType.isAssignableFrom(collectionItemType)) {
          if (erasedComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
            return variable.getName() + ".toArray()";
          }
          return variable.getName() + ".toArray(new " + erasedComponentType.getCanonicalText() + "[0])";
        }
      }
    }
    return null;
  }

  private static @NotNull List<PsiVariable> getDeclaredVariables(PsiParameterListOwner method) {
    List<PsiVariable> variables = new ArrayList<>();
    PsiCodeBlock body = ObjectUtils.tryCast(method.getBody(), PsiCodeBlock.class);
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      for (PsiStatement statement : statements) {
        if (statement instanceof PsiDeclarationStatement) {
          PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
          for (PsiElement declaredElement : declaredElements) {
            if (declaredElement instanceof PsiLocalVariable) variables.add((PsiVariable)declaredElement);
          }
        }
      }
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    ContainerUtil.addAll(variables, parameters);
    return variables;
  }

  private static boolean invokeSingleExpressionLambdaFix(@NotNull PsiParameterListOwner method) {
    if (!(method instanceof PsiLambdaExpression lambda)) return false;
    PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
    if (returnType == null) return false;
    PsiCodeBlock body = ObjectUtils.tryCast(lambda.getBody(), PsiCodeBlock.class);
    if (body == null) return false;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiExpressionStatement expressionStatement)) return false;
    PsiExpression expression = expressionStatement.getExpression();
    PsiType expressionType = expression.getType();
    if (expressionType == null || !returnType.isAssignableFrom(expressionType)) return false;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(lambda.getProject());
    PsiReturnStatement returnStatement = (PsiReturnStatement)factory.createStatementFromText("return 0;", lambda);
    Objects.requireNonNull(returnStatement.getReturnValue()).replace(expression);
    CommentTracker tracker = new CommentTracker();
    tracker.markUnchanged(expression);
    tracker.replaceAndRestoreComments(expressionStatement, returnStatement);
    return true;
  }
}
