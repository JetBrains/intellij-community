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
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * @author max
 */
public class JavaOverridingMethodsSearcher implements QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final OverridingMethodsSearch.SearchParameters parameters, @NotNull final Processor<PsiMethod> consumer) {
    final PsiMethod method = parameters.getMethod();

    Project project = ApplicationManager.getApplication().runReadAction((Computable<Project>)method::getProject);
    Collection<PsiMethod> cached = HighlightingCaches.getInstance(project).OVERRIDING_METHODS.get(method);
    if (cached == null) {
      cached = compute(method, project);
      HighlightingCaches.getInstance(project).OVERRIDING_METHODS.put(method, cached);
    }

    final SearchScope scope = parameters.getScope();

    for (final PsiMethod subMethod : cached) {
      ProgressManager.checkCanceled();
      if (!ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> PsiSearchScopeUtil.isInScope(scope, subMethod))) {
        continue;
      }
      if (!consumer.process(subMethod) || !parameters.isCheckDeep()) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static Collection<PsiMethod> compute(@NotNull PsiMethod method, @NotNull Project project) {
    Collection<PsiMethod> result = new LinkedHashSet<>();

    Application application = ApplicationManager.getApplication();
    final PsiClass containingClass = application.runReadAction((Computable<PsiClass>)method::getContainingClass);
    assert containingClass != null;
    Processor<PsiClass> inheritorsProcessor = inheritor -> {
      PsiMethod found = application.runReadAction((Computable<PsiMethod>)() -> findOverridingMethod(project, inheritor, method, containingClass));
      if (found != null) {
        result.add(found);
      }
      return true;
    };

    // use wider scope to handle public method defined in package-private class which is subclassed by public class in the same package which is subclassed by public class from another package with redefined method
    SearchScope allScope = GlobalSearchScope.allScope(project);
    boolean success = ClassInheritorsSearch.search(containingClass, allScope, true).forEach(inheritorsProcessor);
    assert success;
    return result;
  }

  @Nullable
  private static PsiMethod findOverridingMethod(@NotNull Project project,
                                                @NotNull PsiClass inheritor,
                                                @NotNull PsiMethod method,
                                                @NotNull PsiClass methodContainingClass) {
    String name = method.getName();
    if (inheritor.findMethodsByName(name, false).length > 0) {
      PsiMethod found = MethodSignatureUtil.findMethodBySuperSignature(inheritor, getSuperSignature(inheritor, methodContainingClass, method), false);
      if (found != null && isAcceptable(project, found, inheritor, method, methodContainingClass)) {
        return found;
      }
    }

    if (methodContainingClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
      final PsiClass superClass = inheritor.getSuperClass();
      if (superClass != null && !superClass.isInheritor(methodContainingClass, true) && superClass.findMethodsByName(name, true).length > 0) {
        MethodSignature signature = getSuperSignature(inheritor, methodContainingClass, method);
        PsiMethod derived = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
        if (derived != null && isAcceptable(project, derived, inheritor, method, methodContainingClass)) {
          return derived;
        }
      }
    }
    return null;
  }

  @NotNull
  private static MethodSignature getSuperSignature(PsiClass inheritor, @NotNull PsiClass parentClass, PsiMethod method) {
    PsiSubstitutor substitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(parentClass, inheritor, PsiSubstitutor.EMPTY, null);
    // if null, we have EJB custom inheritance here and still check overriding
    return method.getSignature(substitutor != null ? substitutor : PsiSubstitutor.EMPTY);
  }


  private static boolean isAcceptable(@NotNull Project project,
                                      @NotNull PsiMethod found,
                                      @NotNull PsiClass foundContainingClass,
                                      @NotNull PsiMethod method,
                                      @NotNull PsiClass methodContainingClass) {
    return !found.hasModifierProperty(PsiModifier.STATIC) &&
           (!method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
            JavaPsiFacade.getInstance(project).arePackagesTheSame(methodContainingClass, foundContainingClass));
  }
}
