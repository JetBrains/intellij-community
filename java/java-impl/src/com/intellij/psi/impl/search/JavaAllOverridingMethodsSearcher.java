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
package com.intellij.psi.impl.search;

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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class JavaAllOverridingMethodsSearcher implements QueryExecutor<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllOverridingMethodsSearch.SearchParameters p, @NotNull final Processor<Pair<PsiMethod, PsiMethod>> consumer) {
    final PsiClass psiClass = p.getPsiClass();

    PsiMethod[] methodsArray = psiClass.getMethods();
    final List<PsiMethod> methods = new ArrayList<PsiMethod>(methodsArray.length);
    for (PsiMethod method : methodsArray) {
      if (PsiUtil.canBeOverriden(method)) methods.add(method);
    }

    final SearchScope scope = p.getScope();

    Processor<PsiClass> inheritorsProcessor = new Processor<PsiClass>() {
      @Override
      public boolean process(PsiClass inheritor) {
        //could be null if not java inheritor, TODO only JavaClassInheritors are needed
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(psiClass, inheritor, PsiSubstitutor.EMPTY);
        if (substitutor == null) return true;

        for (PsiMethod method : methods) {
          if (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !JavaPsiFacade.getInstance(inheritor.getProject()).arePackagesTheSame(psiClass, inheritor)) continue;
          
          MethodSignature signature = method.getSignature(substitutor);
          PsiMethod inInheritor = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature, false);
          if (inInheritor == null || inInheritor.hasModifierProperty(PsiModifier.STATIC)) {
            if (psiClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
              final PsiClass superClass = inheritor.getSuperClass();
              if (superClass != null && !superClass.isInheritor(psiClass, true)) {
                inInheritor = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
                if (inInheritor != null && !inInheritor.hasModifierProperty(PsiModifier.STATIC)) {
                  if (!consumer.process(new Pair<PsiMethod, PsiMethod>(method, inInheritor))) return false;
                }
              }
            }
            continue;
          }
          if (!consumer.process(new Pair<PsiMethod, PsiMethod>(method, inInheritor))) return false;
        }

        return true;
      }
    };

    return ClassInheritorsSearch.search(psiClass, scope, true).forEach(inheritorsProcessor);
  }
}
