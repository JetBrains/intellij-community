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
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class TurnRefsToSuperProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor");

  private PsiClass mySuper;
  public TurnRefsToSuperProcessor(Project project,
                                  @NotNull PsiClass aClass,
                                  @NotNull PsiClass aSuper,
                                  boolean replaceInstanceOf) {
    super(project, replaceInstanceOf, aSuper.getName());
    myClass = aClass;
    mySuper = aSuper;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("turn.refs.to.super.command",
                                     DescriptiveNameUtil.getDescriptiveName(myClass), DescriptiveNameUtil.getDescriptiveName(mySuper));
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new RefsToSuperViewDescriptor(myClass, mySuper);
  }

  private void setClasses(@NotNull final PsiClass aClass, @NotNull final PsiClass aSuper) {
    myClass = aClass;
    mySuper = aSuper;
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiReference[] refs = ReferencesSearch.search(myClass, GlobalSearchScope.projectScope(myProject), false).toArray(
      PsiReference.EMPTY_ARRAY);

    final ArrayList<UsageInfo> result = detectTurnToSuperRefs(refs, new ArrayList<>());

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(@NotNull final PsiElement[] elements) {
    LOG.assertTrue(elements.length == 2 && elements[0] instanceof PsiClass && elements[1] instanceof PsiClass);
    setClasses ((PsiClass) elements[0], (PsiClass) elements[1]);
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && refUsages.get().length == 0) {
      String message = RefactoringBundle.message("no.usages.can.be.replaced", myClass.getQualifiedName(), mySuper.getQualifiedName());
      Messages.showInfoMessage(myProject, message, TurnRefsToSuperHandler.REFACTORING_NAME);
      return false;
    }

    return super.preprocessUsages(refUsages);
  }

  protected boolean canTurnToSuper(final PsiElement refElement) {
    return super.canTurnToSuper(refElement) &&
           JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible(mySuper, refElement, null);
  }

  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    try {
      final PsiClass aSuper = mySuper;
      processTurnToSuperRefs(usages, aSuper);

    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  protected boolean isInSuper(PsiElement member) {
    if (!(member instanceof PsiMember)) return false;
    final PsiManager manager = member.getManager();
    if (InheritanceUtil.isInheritorOrSelf(mySuper, ((PsiMember)member).getContainingClass(), true)) return true;

    if (member instanceof PsiField) {
      final PsiClass containingClass = ((PsiField) member).getContainingClass();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(member);
      if (manager.areElementsEquivalent(containingClass, JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().getArrayClass(languageLevel))) {
        return true;
      }
    } else if (member instanceof PsiMethod) {
      return mySuper.findMethodBySignature((PsiMethod) member, true) != null;
    }

    return false;
  }

  protected boolean isSuperInheritor(PsiClass aClass) {
    return InheritanceUtil.isInheritorOrSelf(mySuper, aClass, true);
  }

  public PsiClass getSuper() {
    return mySuper;
  }

  public PsiClass getTarget() {
    return myClass;
  }

  public boolean isReplaceInstanceOf() {
    return myReplaceInstanceOf;
  }

  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    return Collections.emptyList(); // neither myClass nor mySuper are subject to change, it's just references that are going to change
  }
}