// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


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
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(getElement(), psiFile);
    List<PsiClass> targetClasses = filterTargetClasses(element, project);
    if (targetClasses.isEmpty()) return IntentionPreviewInfo.EMPTY;
    invokeImpl(targetClasses.get(0));
    return IntentionPreviewInfo.DIFF;
  }

  private void invokeImpl(final PsiClass targetClass) {
    PsiNewExpression newExpression = getNewExpression();
    if (!targetClass.isPhysical()) {
      newExpression = PsiTreeUtil.findSameElementInCopy(newExpression, targetClass.getContainingFile());
    }
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