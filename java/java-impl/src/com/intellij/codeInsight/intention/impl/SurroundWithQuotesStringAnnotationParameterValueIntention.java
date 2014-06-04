/*
* Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
* @author Dmitry Batkovich
*/
public class SurroundWithQuotesStringAnnotationParameterValueIntention extends PsiElementBaseIntentionAction {
  private static final Set<IElementType> SUITABLE_TYPES = ContainerUtil.newHashSet(JavaTokenType.LONG_LITERAL,
                                                                                   JavaTokenType.FLOAT_LITERAL,
                                                                                   JavaTokenType.INTEGER_LITERAL,
                                                                                   JavaTokenType.DOUBLE_LITERAL,
                                                                                   JavaTokenType.CHARACTER_LITERAL,
                                                                                   JavaTokenType.TRUE_KEYWORD,
                                                                                   JavaTokenType.FALSE_KEYWORD);

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    String newText = element.getText();
    if (((PsiJavaToken)element).getTokenType().equals(JavaTokenType.CHARACTER_LITERAL)) {
      newText = newText.substring(1, newText.length() - 1);
    }
    newText = "\"" + newText + "\"";
    PsiElement newToken = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newText, null).getFirstChild();
    final PsiElement newElement = element.replace(newToken);
    editor.getCaretModel().moveToOffset(newElement.getTextOffset() + newElement.getTextLength());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken && SUITABLE_TYPES.contains(((PsiJavaToken)element).getTokenType()))) {
      return false;
    }
    final PsiElement literalExpression = element.getParent();
    if (literalExpression == null) {
      return false;
    }
    final PsiElement nameValuePair = literalExpression.getParent();
    if (!(nameValuePair instanceof PsiNameValuePair)) {
      return false;
    }
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(nameValuePair, PsiAnnotation.class);
    if (annotation == null) {
      return false;
    }
    final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) {
      return false;
    }
    final PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass)) {
      return false;
    }
    final String parameterName = ((PsiNameValuePair)nameValuePair).getName();
    final PsiMethod[] methods =
      ((PsiClass)resolved).findMethodsByName(parameterName == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : parameterName, false);
    if (methods.length != 1) {
      return false;
    }
    final PsiType methodReturnType = methods[0].getReturnType();
    if (!(methodReturnType instanceof PsiClassType)) {
      return false;
    }
    final PsiClass returnTypeClass = ((PsiClassType)methodReturnType).resolve();
    return returnTypeClass != null && CommonClassNames.JAVA_LANG_STRING.equals(returnTypeClass.getQualifiedName());
  }


  @NotNull
  @Override
  public String getFamilyName() {
    return "Surround annotation parameter value with quotes";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
