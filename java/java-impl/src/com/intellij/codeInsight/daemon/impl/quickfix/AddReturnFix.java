/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AddReturnFix implements IntentionAction {
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
              ObjectUtils.notNull(returnStatement.getReturnValue()).replace(expression);
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

}
