/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class ConvertEqualsMethodToStaticIntention extends BaseElementAtCaretIntentionAction {
  private static final Logger LOG = Logger.getInstance(ConvertEqualsMethodToStaticIntention.class);
  private static final String REPLACE_TEMPLATE = "java.util.Objects.equals(%s, %s)";
  public static final String TEXT = "Convert '.equals()' to 'java.util.Objects.equals()'";

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier)) {
      return false;
    }
    if (!PsiUtil.isLanguageLevel7OrHigher(element)) {
      return false;
    }
    final PsiElement referenceExpression = element.getParent();
    if (!(referenceExpression instanceof PsiReferenceExpression)) {
      return false;
    }
    if (!"equals".equals(((PsiReferenceExpression)referenceExpression).getReferenceName())) {
      return false;
    }
    final PsiElement methodCallExpression = referenceExpression.getParent();
    if (!(methodCallExpression instanceof PsiMethodCallExpression)) {
      return false;
    }
    final int argumentsCount = ((PsiMethodCallExpression)methodCallExpression).getArgumentList().getExpressions().length;
    if (argumentsCount != 1) {
      return false;
    }
    final PsiMethod method = ((PsiMethodCallExpression)methodCallExpression).resolveMethod();
    if (method == null) {
      return false;
    }
    PsiClass javaLangObject = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, element.getResolveScope());
    if (javaLangObject == null) {
      return false;
    }
    if (javaLangObject.isEquivalentTo(method.getContainingClass())) {
      return true;
    }
    final PsiMethod[] superMethods = method.findSuperMethods(javaLangObject);
    return superMethods.length == 1;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
      return;
    }
    final PsiElement parent = element.getParent().getParent();
    LOG.assertTrue(parent instanceof PsiMethodCallExpression);
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    final String qualifierText = qualifier == null ? PsiKeyword.THIS : qualifier.getText();
    final PsiExpression parameter = methodCall.getArgumentList().getExpressions()[0];
    final String expressionText = String.format(REPLACE_TEMPLATE, qualifierText, parameter.getText());
    methodCall.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(expressionText, null));
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return TEXT;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
