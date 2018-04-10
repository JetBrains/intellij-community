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
  public boolean execute(@NotNull final AllOverridingMethodsSearch.SearchParameters p, @NotNull final Processor<Pair<PsiMethod, PsiMethod>> consumer) {
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
