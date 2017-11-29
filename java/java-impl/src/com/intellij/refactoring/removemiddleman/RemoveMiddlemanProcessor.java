/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.removemiddleman;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.removemiddleman.usageInfo.DeleteMethod;
import com.intellij.refactoring.removemiddleman.usageInfo.InlineDelegatingCall;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RemoveMiddlemanProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(RemoveMiddlemanProcessor.class);

  private final PsiField field;
  private final PsiClass containingClass;
  private final List<MemberInfo> myDelegateMethodInfos;
  private PsiMethod getter;

  public RemoveMiddlemanProcessor(PsiField field, List<MemberInfo> memberInfos) {
    super(field.getProject());
    this.field = field;
    containingClass = field.getContainingClass();
    final String propertyName = PropertyUtilBase.suggestPropertyName(field);
    final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    getter = PropertyUtilBase.findPropertyGetter(containingClass, propertyName, isStatic, false);
    myDelegateMethodInfos = memberInfos;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usageInfos) {
    return new RemoveMiddlemanUsageViewDescriptor(field);
  }


  public void findUsages(@NotNull List<FixableUsageInfo> usages) {
    for (final MemberInfo memberInfo : myDelegateMethodInfos) {
      if (!memberInfo.isChecked()) continue;
      final PsiMethod method = (PsiMethod)memberInfo.getMember();
      final String getterName = GenerateMembersUtil.suggestGetterName(field);
      final int[] paramPermutation = DelegationUtils.getParameterPermutation(method);
      final PsiMethod delegatedMethod = DelegationUtils.getDelegatedMethod(method);
      LOG.assertTrue(!DelegationUtils.isAbstract(method));
      processUsagesForMethod(memberInfo.isToAbstract(), method, paramPermutation, getterName, delegatedMethod, usages);
    }
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    for (MemberInfo memberInfo : myDelegateMethodInfos) {
      if (memberInfo.isChecked() && memberInfo.isToAbstract()) {
        final PsiMember psiMember = memberInfo.getMember();
        if (psiMember instanceof PsiMethod && ((PsiMethod)psiMember).findDeepestSuperMethods().length > 0) {
          conflicts.putValue(psiMember, SymbolPresentationUtil.getSymbolPresentableText(psiMember) + " will be deleted. Hierarchy will be broken");
        }
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  private void processUsagesForMethod(final boolean deleteMethodHierarchy, PsiMethod method, int[] paramPermutation, String getterName, PsiMethod delegatedMethod,
                                        List<FixableUsageInfo> usages) {
    for (PsiReference reference : ReferencesSearch.search(method)) {
      final PsiElement referenceElement = reference.getElement();
      final PsiMethodCallExpression call = (PsiMethodCallExpression)referenceElement.getParent();
      final String access;
      if (call.getMethodExpression().getQualifierExpression() == null) {
        access = field.getName();
      } else {
        access = getterName + "()";
        if (getter == null) {
          getter = GenerateMembersUtil.generateGetterPrototype(field);
        }
      }
      usages.add(new InlineDelegatingCall(call, paramPermutation, access, delegatedMethod.getName()));
    }
    if (deleteMethodHierarchy) {
      usages.add(new DeleteMethod(method));
    }
  }

  protected void performRefactoring(@NotNull UsageInfo[] usageInfos) {
    if (getter != null) {
      try {
        if (containingClass.findMethodBySignature(getter, false) == null) {
          containingClass.add(getter);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    super.performRefactoring(usageInfos);
  }

  protected String getCommandName() {
    return RefactorJBundle.message("exposed.delegation.command.name", containingClass.getName(), '.', field.getName());
  }
}
