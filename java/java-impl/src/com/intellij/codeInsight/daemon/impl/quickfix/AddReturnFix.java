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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AddReturnFix implements IntentionAction {
  private final PsiMethod myMethod;

  public AddReturnFix(@NotNull PsiMethod method) {
    myMethod = method;
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
           myMethod.getManager().isInProject(myMethod) &&
           myMethod.getBody() != null &&
           myMethod.getBody().getRBrace() != null
        ;
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myMethod;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    String value = suggestReturnValue();
    PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
    PsiReturnStatement returnStatement = (PsiReturnStatement) factory.createStatementFromText("return " + value+";", myMethod);
    PsiCodeBlock body = myMethod.getBody();
    returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

    MethodReturnTypeFix.selectReturnValueInEditor(returnStatement, editor);
  }

  private String suggestReturnValue() {
    PsiType type = myMethod.getReturnType();
    // first try to find suitable local variable
    PsiVariable[] variables = getDeclaredVariables(myMethod);
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

  private static PsiVariable[] getDeclaredVariables(PsiMethod method) {
    List<PsiVariable> variables = new ArrayList<>();
    PsiStatement[] statements = method.getBody().getStatements();
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) variables.add((PsiVariable)declaredElement);
        }
      }
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    ContainerUtil.addAll(variables, parameters);
    return variables.toArray(new PsiVariable[variables.size()]);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
