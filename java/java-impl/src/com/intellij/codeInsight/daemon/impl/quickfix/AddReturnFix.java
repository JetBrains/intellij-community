// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AddReturnFix implements IntentionActionWithFixAllOption, HighPriorityAction {
  private final PsiParameterListOwner myMethod;


  public AddReturnFix(@NotNull PsiParameterListOwner methodOrLambda) {
    myMethod = methodOrLambda;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.return.statement.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() &&
           BaseIntentionAction.canModify(myMethod) &&
           myMethod.getBody() instanceof PsiCodeBlock &&
           ((PsiCodeBlock)myMethod.getBody()).getRBrace() != null;
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myMethod;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (invokeSingleExpressionLambdaFix()) {
      return;
    }
    String value = suggestReturnValue();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
    PsiReturnStatement returnStatement = (PsiReturnStatement) factory.createStatementFromText("return " + value+";", myMethod);
    PsiCodeBlock body = ObjectUtils.tryCast(myMethod.getBody(), PsiCodeBlock.class);
    assert body != null;
    returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

    MethodReturnTypeFix.selectInEditor(returnStatement.getReturnValue(), editor);
  }

  private String suggestReturnValue() {
    PsiType type;
    if (myMethod instanceof PsiMethod) {
      type = ((PsiMethod)myMethod).getReturnType();
    }
    else if (myMethod instanceof PsiLambdaExpression) {
      type = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)myMethod);
    }
    else {
      return PsiKeyword.NULL; // normally shouldn't happen
    }

    // first try to find suitable local variable
    List<PsiVariable> variables = getDeclaredVariables(myMethod);
    for (PsiVariable variable : variables) {
      PsiType varType = variable.getType();
      if (varType.equals(type)) {
        return variable.getName();
      }
    }
    // then try to find a conversion of local variable to the required type
    for (PsiVariable variable : variables) {
      String conversion = getConversionToType(variable, type, true);
      if (conversion != null) {
        return conversion;
      }
    }
    for (PsiVariable variable : variables) {
      String conversion = getConversionToType(variable, type, false);
      if (conversion != null) {
        return conversion;
      }
    }
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  @NonNls
  private String getConversionToType(@NotNull PsiVariable variable, @Nullable PsiType type, boolean preciseTypeReqired) {
    PsiType varType = variable.getType();
    if (type instanceof PsiArrayType) {
      PsiType arrayComponentType = ((PsiArrayType)type).getComponentType();
      if (!(arrayComponentType instanceof PsiPrimitiveType) &&
          !(PsiUtil.resolveClassInType(arrayComponentType) instanceof PsiTypeParameter) &&
          InheritanceUtil.isInheritor(varType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        PsiType erasedComponentType = TypeConversionUtil.erasure(arrayComponentType);
        if (!preciseTypeReqired || arrayComponentType.equals(erasedComponentType)) {
          PsiType collectionItemType = JavaGenericsUtil.getCollectionItemType(varType, myMethod.getResolveScope());
          if (collectionItemType != null && erasedComponentType.isAssignableFrom(collectionItemType)) {
            if (erasedComponentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
              return variable.getName() + ".toArray()";
            }
            return variable.getName() + ".toArray(new " + erasedComponentType.getCanonicalText() + "[0])";
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<PsiVariable> getDeclaredVariables(PsiParameterListOwner method) {
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

  private boolean invokeSingleExpressionLambdaFix() {
    if (myMethod instanceof PsiLambdaExpression) {
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)myMethod);
      if (returnType != null) {
        PsiCodeBlock body = ObjectUtils.tryCast(myMethod.getBody(), PsiCodeBlock.class);
        if (body != null) {
          PsiStatement[] statements = body.getStatements();
          if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statements[0];
            PsiExpression expression = expressionStatement.getExpression();
            PsiType expressionType = expression.getType();

            if (expressionType != null && returnType.isAssignableFrom(expressionType)) {
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(myMethod.getProject());
              PsiReturnStatement returnStatement = (PsiReturnStatement)factory.createStatementFromText("return 0;", myMethod);
              Objects.requireNonNull(returnStatement.getReturnValue()).replace(expression);
              CommentTracker tracker = new CommentTracker();
              tracker.markUnchanged(expression);
              tracker.replaceAndRestoreComments(expressionStatement, returnStatement);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new AddReturnFix(PsiTreeUtil.findSameElementInCopy(myMethod, target));
  }
}
