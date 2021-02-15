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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CreateInnerClassFromNewFix extends CreateClassFromNewFix {
  private static final Logger LOG = Logger.getInstance(CreateInnerClassFromNewFix.class);

  public CreateInnerClassFromNewFix(final PsiNewExpression expr) {
    super(expr);
  }

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.inner.class.from.usage.text", getKind().getDescriptionAccusative(), varName);
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  @Override
  protected boolean isValidElement(PsiElement element) {
    PsiJavaCodeReferenceElement ref = element instanceof PsiNewExpression ? ((PsiNewExpression)element).getClassOrAnonymousClassReference() : null;
    return ref != null && ref.resolve() != null;
  }

  @Override
  protected boolean rejectContainer(PsiNewExpression qualifier) {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  private void invokeImpl(final PsiClass targetClass) {
    PsiNewExpression newExpression = getNewExpression();
    PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
    assert ref != null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(newExpression.getProject());
    PsiClass created = getKind().create(elementFactory, refName);
    created = (PsiClass)targetClass.add(created);

    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (PsiTreeUtil.isAncestor(targetClass, newExpression, true)) {
      if (targetClass.isInterface() || PsiUtil.isLocalOrAnonymousClass(targetClass)) {
        modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      } else {
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      }
    }

    if (!created.hasModifierProperty(PsiModifier.STATIC) &&
        newExpression.getQualifier() == null &&
        (!PsiTreeUtil.isAncestor(targetClass, newExpression, true) || PsiUtil.getEnclosingStaticElement(newExpression, targetClass) != null || isInThisOrSuperCall(newExpression))) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }

    setupGenericParameters(created, ref);
    setupClassFromNewExpression(created, newExpression);
  }

  private static boolean isInThisOrSuperCall(PsiNewExpression newExpression) {
    final PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(newExpression, PsiExpressionStatement.class);
    if (expressionStatement != null) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (JavaPsiConstructorUtil.isConstructorCall(expression)) {
        return true;
      }
    }
    return false;
  }
}