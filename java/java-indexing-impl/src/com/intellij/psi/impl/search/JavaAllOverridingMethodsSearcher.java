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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class JavaAllOverridingMethodsSearcher implements QueryExecutor<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllOverridingMethodsSearch.SearchParameters p, @NotNull final Processor<Pair<PsiMethod, PsiMethod>> consumer) {
    final PsiClass psiClass = p.getPsiClass();

    final MultiMap<String, PsiMethod> potentials = ApplicationManager.getApplication().runReadAction((Computable<MultiMap<String, PsiMethod>>)() -> {
        final MultiMap<String, PsiMethod> result = MultiMap.create();
        for (PsiMethod method : psiClass.getMethods()) {
          ProgressManager.checkCanceled();
          if (PsiUtil.canBeOverriden(method)) {
            result.putValue(method.getName(), method);
          }
        }
        return result;
      });


    final SearchScope scope = p.getScope();

    Processor<PsiClass> inheritorsProcessor = inheritor -> {
      PsiSubstitutor substitutor = null;

      for (String name : potentials.keySet()) {
        ProgressManager.checkCanceled();
        for (PsiMethod superMethod : potentials.get(name)) {
          ProgressManager.checkCanceled();
          if (superMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !JavaPsiFacade.getInstance(inheritor.getProject()).arePackagesTheSame(psiClass, inheritor)) continue;

          if (substitutor == null) {
            //could be null if not java inheritor, TODO only JavaClassInheritors are needed
            substitutor = TypeConversionUtil.getClassSubstitutor(psiClass, inheritor, PsiSubstitutor.EMPTY);
            if (substitutor == null) return true;
          }

          MethodSignature superSignature = superMethod.getSignature(substitutor);
          PsiMethod inInheritor = MethodSignatureUtil.findMethodBySuperSignature(inheritor, superSignature, false);
          if (inInheritor != null && !inInheritor.hasModifierProperty(PsiModifier.STATIC)) {
            if (!consumer.process(Pair.create(superMethod, inInheritor))) return false;
          }

          if (psiClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
            final PsiClass superClass = inheritor.getSuperClass();
            if (superClass != null && !superClass.isInheritor(psiClass, true)) {
              inInheritor = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, superSignature, true);
              if (inInheritor != null && !inInheritor.hasModifierProperty(PsiModifier.STATIC)) {
                if (!consumer.process(Pair.create(superMethod, inInheritor))) return false;
              }
            }
          }
        }
      }

      return true;
    };

    return ClassInheritorsSearch.search(psiClass, scope, true).forEach(inheritorsProcessor);
  }
}
