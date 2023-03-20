// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.ReferencedElementsCollector;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class InlineSuperCallUsageInfo extends FixableUsageInfo {
  private PsiCodeBlock myConstrBody;

  public InlineSuperCallUsageInfo(PsiMethodCallExpression methodCallExpression) {
    super(methodCallExpression);
  }

  public InlineSuperCallUsageInfo(PsiMethodCallExpression methodCallExpression, PsiCodeBlock constrBody) {
    super(methodCallExpression);
    myConstrBody = constrBody;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiElement element = getElement();
    if (element != null && myConstrBody != null) {
      assert !element.isPhysical();
      final PsiStatement statement = JavaPsiFacade.getElementFactory(getProject()).createStatementFromText("super();", myConstrBody);
      element = ((PsiExpressionStatement)myConstrBody.addBefore(statement, myConstrBody.getFirstBodyElement())).getExpression();
    }
    if (element instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
      final PsiMethod superConstructor = (PsiMethod)methodExpression.resolve();
      if (superConstructor != null) {
        PsiMethod methodCopy = JavaPsiFacade.getElementFactory(getProject()).createMethod("toInline", PsiTypes.voidType());
        final PsiCodeBlock constructorBody = superConstructor.getBody();
        if (constructorBody != null) {
          final PsiCodeBlock methodBody = methodCopy.getBody();
          assert methodBody != null;
          methodBody.replace(constructorBody);

          methodCopy.getParameterList().replace(superConstructor.getParameterList());
          methodCopy.getThrowsList().replace(superConstructor.getThrowsList());

          methodExpression = (PsiReferenceExpression)methodExpression.replace(JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(methodCopy.getName(), methodExpression));
          final PsiClass inliningClass = superConstructor.getContainingClass();
          assert inliningClass != null;
          methodCopy = (PsiMethod)inliningClass.add(methodCopy);
          final InlineMethodProcessor inlineMethodProcessor = new InlineMethodProcessor(getProject(), methodCopy, methodExpression, null, true);
          inlineMethodProcessor.inlineMethodCall(methodExpression);
          methodCopy.delete();
        }
      }
    }
  }

  @Override
  public String getConflictMessage() {
    final MultiMap<PsiElement, @Nls String> conflicts = new MultiMap<>();
    final PsiElement element = getElement();
    if (element instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiMethod superConstructor = methodCallExpression.resolveMethod();
      if (superConstructor != null) {
        InlineMethodProcessor.addInaccessibleMemberConflicts(superConstructor, new UsageInfo[]{new UsageInfo(methodCallExpression.getMethodExpression())}, new ReferencedElementsCollector(){
          @Override
          protected void checkAddMember(@NotNull PsiMember member) {
            if (!PsiTreeUtil.isAncestor(superConstructor.getContainingClass(), member, false)) {
              super.checkAddMember(member);
            }
          }
        }, conflicts);
        if (InlineMethodProcessor.checkBadReturns(superConstructor) && !InlineUtil.allUsagesAreTailCalls(superConstructor)) {
          conflicts.putValue(superConstructor, JavaRefactoringBundle.message("inline.super.no.return.in.super.ctor"));
        }
      }
    }
    return conflicts.isEmpty() ? null : conflicts.values().iterator().next(); //todo
  }
}
