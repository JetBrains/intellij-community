/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodInheritanceUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class MethodReturnAlwaysConstantInspection extends BaseGlobalInspection {

  @Override
  public CommonProblemDescriptor[] checkElement(
    @NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager,
    @NotNull GlobalInspectionContext globalContext,
    @NotNull ProblemDescriptionsProcessor processor) {
    if (!(refEntity instanceof RefMethod refMethod)) {
      return null;
    }

    //don't warn on overriders
    if (refMethod.hasSuperMethods()) {
      return null;
    }

    PsiElement element = refMethod.getPsiElement();
    if (!(element instanceof PsiMethod psiMethod)) {
      return null;
    }

    if (psiMethod.getBody() == null && refMethod.getDerivedReferences().isEmpty()) {
      return null;
    }

    final Set<RefOverridable> allScopeInheritors = MethodInheritanceUtils.calculateSiblingReferences(refMethod);
    for (RefOverridable siblingReference : allScopeInheritors) {
      PsiElement psi = siblingReference.getPsiElement();
      if (psi instanceof PsiMethod siblingPsiMethod) {
        if (siblingPsiMethod.getBody() != null && !alwaysReturnsConstant(siblingPsiMethod.getBody())) {
          return null;
        }
      }
      else if (psi instanceof PsiLambdaExpression siblingLambda) {
        if (siblingLambda.getBody() != null && !alwaysReturnsConstant(siblingLambda)) {
          return null;
        }
      }
      else if (psi instanceof PsiMethodReferenceExpression) {
        return null;
      }
    }
    for (RefOverridable siblingReference : allScopeInheritors) {
      PsiElement psi = siblingReference.getPsiElement();
      if (!(psi instanceof PsiMethod siblingMethod)) continue;
      final PsiIdentifier identifier = siblingMethod.getNameIdentifier();
      if (identifier == null) {
        continue;
      }
      processor.addProblemElement(siblingReference, manager.createProblemDescriptor(identifier,
                                                                                    InspectionGadgetsBundle.message(
                                                                                      "method.return.always.constant.problem.descriptor"), false, null,
                                                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
    }
    return null;
  }

  private static boolean alwaysReturnsConstant(@NotNull PsiCodeBlock body) {
    final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
    if (!(statement instanceof PsiReturnStatement returnStatement)) {
      return false;
    }
    final PsiExpression value = returnStatement.getReturnValue();
    return value != null && PsiUtil.isConstantExpression(value);
  }

  private static boolean alwaysReturnsConstant(@NotNull PsiLambdaExpression lambdaExpression) {
    PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiCodeBlock codeBlock) {
      return alwaysReturnsConstant(codeBlock);
    }
    if (body instanceof PsiExpression expression) {
      return PsiUtil.isConstantExpression(expression);
    }
    return false;
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override
      public void visitMethod(@NotNull final RefMethod refMethod) {
        if (processor.getDescriptions(refMethod) == null) return;
        if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
        globalContext.enqueueDerivedMethodsProcessor(refMethod, derivedMethod -> {
          processor.ignoreElement(refMethod);
          return false;
        });
      }
    });

    return false;
  }
}
