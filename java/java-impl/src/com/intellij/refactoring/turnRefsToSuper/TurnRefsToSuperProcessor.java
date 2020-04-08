// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class TurnRefsToSuperProcessor extends TurnRefsToSuperProcessorBase {
  private static final Logger LOG = Logger.getInstance(TurnRefsToSuperProcessor.class);

  private PsiClass mySuper;
  public TurnRefsToSuperProcessor(Project project,
                                  @NotNull PsiClass aClass,
                                  @NotNull PsiClass aSuper,
                                  boolean replaceInstanceOf) {
    super(project, replaceInstanceOf, aSuper.getName());
    myClass = aClass;
    mySuper = aSuper;
  }

  @Override
  @NotNull
  protected String getCommandName() {
    return JavaRefactoringBundle.message("turn.refs.to.super.command",
                                     DescriptiveNameUtil.getDescriptiveName(myClass), DescriptiveNameUtil.getDescriptiveName(mySuper));
  }

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new RefsToSuperViewDescriptor(myClass, mySuper);
  }

  private void setClasses(@NotNull final PsiClass aClass, @NotNull final PsiClass aSuper) {
    myClass = aClass;
    mySuper = aSuper;
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    final PsiReference[] refs = ReferencesSearch.search(myClass, GlobalSearchScope.projectScope(myProject), false).toArray(
      PsiReference.EMPTY_ARRAY);

    final ArrayList<UsageInfo> result = detectTurnToSuperRefs(refs, new ArrayList<>());

    final UsageInfo[] usageInfos = result.toArray(UsageInfo.EMPTY_ARRAY);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  protected void refreshElements(final PsiElement @NotNull [] elements) {
    LOG.assertTrue(elements.length == 2 && elements[0] instanceof PsiClass && elements[1] instanceof PsiClass);
    setClasses ((PsiClass) elements[0], (PsiClass) elements[1]);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && refUsages.get().length == 0) {
      String message = JavaRefactoringBundle.message("no.usages.can.be.replaced", myClass.getQualifiedName(), mySuper.getQualifiedName());
      Messages.showInfoMessage(myProject, message, TurnRefsToSuperHandler.getRefactoringName());
      return false;
    }

    return super.preprocessUsages(refUsages);
  }

  @Override
  protected boolean canTurnToSuper(final PsiElement refElement) {
    return super.canTurnToSuper(refElement) &&
           JavaPsiFacade.getInstance(myProject).getResolveHelper().isAccessible(mySuper, refElement, null);
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    try {
      final PsiClass aSuper = mySuper;
      processTurnToSuperRefs(usages, aSuper);

    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    performVariablesRenaming();
  }

  @Override
  protected boolean isInSuper(PsiElement member) {
    if (!(member instanceof PsiMember)) return false;
    if (InheritanceUtil.isInheritorOrSelf(mySuper, ((PsiMember)member).getContainingClass(), true)) return true;

    if (member instanceof PsiField) {
      final PsiClass containingClass = ((PsiField) member).getContainingClass();
      if (PsiUtil.isArrayClass(containingClass)) {
        return true;
      }
    } else if (member instanceof PsiMethod) {
      return mySuper.findMethodBySignature((PsiMethod) member, true) != null;
    }

    return false;
  }

  @Override
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

  @Override
  @NotNull
  protected Collection<? extends PsiElement> getElementsToWrite(@NotNull final UsageViewDescriptor descriptor) {
    return Collections.emptyList(); // neither myClass nor mySuper are subject to change, it's just references that are going to change
  }
}