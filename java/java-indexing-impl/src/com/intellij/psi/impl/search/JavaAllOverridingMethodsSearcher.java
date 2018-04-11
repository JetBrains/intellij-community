// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ven
 */
public class JavaAllOverridingMethodsSearcher implements QueryExecutor<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllOverridingMethodsSearch.SearchParameters p, @NotNull final Processor<? super Pair<PsiMethod, PsiMethod>> consumer) {
    final PsiClass psiClass = p.getPsiClass();

    final List<PsiMethod> potentials = ReadAction.compute(() -> ContainerUtil.filter(psiClass.getMethods(), PsiUtil::canBeOverridden));

    final SearchScope scope = p.getScope();

    Processor<PsiClass> inheritorsProcessor = inheritor -> {
      Project project = psiClass.getProject();
      for (PsiMethod superMethod : potentials) {
        ProgressManager.checkCanceled();
        if (superMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
            !JavaPsiFacade.getInstance(project).arePackagesTheSame(psiClass, inheritor)) continue;

        PsiMethod inInheritor = JavaOverridingMethodsSearcher.findOverridingMethod(inheritor, superMethod, psiClass);
        if (inInheritor != null && !consumer.process(Pair.create(superMethod, inInheritor))) return false;
      }

      return true;
    };

    return ClassInheritorsSearch.search(psiClass, scope, true).forEach(inheritorsProcessor);
  }
}
