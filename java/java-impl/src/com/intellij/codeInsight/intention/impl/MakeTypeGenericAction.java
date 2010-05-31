/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.intention.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.*;
import org.jetbrains.annotations.*;

/**
 *  @author dsl
 */
public class MakeTypeGenericAction extends PsiElementBaseIntentionAction {
  private String variableName;
  private String newTypeName;

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.make.type.generic.family");
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.make.type.generic.text", variableName, newTypeName);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    if (!element.isWritable()) return false;
    return findVariable(element) != null;
  }

  private Pair<PsiVariable,PsiType> findVariable(final PsiElement element) {
    PsiVariable variable = null;
    if (element instanceof PsiIdentifier) {
      if (element.getParent() instanceof PsiVariable) {
        variable = (PsiVariable)element.getParent();
      }
    }
    else if (element instanceof PsiJavaToken) {
      final PsiJavaToken token = (PsiJavaToken)element;
      if (token.getTokenType() != JavaTokenType.EQ) return null;
      if (token.getParent() instanceof PsiVariable) {
        variable = (PsiVariable)token.getParent();
      }
    }
    if (variable == null) {
      return null;
    }
    variableName = variable.getName();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return null;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType)) return null;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    final PsiSubstitutor targetSubstitutor = TypeConversionUtil.getClassSubstitutor(variableResolveResult.getElement(), initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type =
      JavaPsiFacade.getInstance(variable.getProject()).getElementFactory().createType(variableResolveResult.getElement(), targetSubstitutor);
    newTypeName = type.getCanonicalText();
    return Pair.create(variable, type);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    final PsiElement element = file.findElementAt(position);
    Pair<PsiVariable, PsiType> pair = findVariable(element);
    if (pair == null) return;
    PsiVariable variable = pair.getFirst();
    PsiType type = pair.getSecond();

    variable.getTypeElement().replace(JavaPsiFacade.getInstance(variable.getProject()).getElementFactory().createTypeElement(type));
  }
}
