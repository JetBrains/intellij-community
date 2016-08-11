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

/*
 * User: anna
 * Date: 10-Oct-2009
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.ReferencedElementsCollector;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
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
        PsiMethod methodCopy = JavaPsiFacade.getElementFactory(getProject()).createMethod("toInline", PsiType.VOID);
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
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final PsiElement element = getElement();
    if (element instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
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
          conflicts.putValue(superConstructor, CommonRefactoringUtil.capitalize(RefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow", "") + " of super constructor"));
        }
      }
    }
    return conflicts.isEmpty() ? null : conflicts.values().iterator().next(); //todo
  }
}
