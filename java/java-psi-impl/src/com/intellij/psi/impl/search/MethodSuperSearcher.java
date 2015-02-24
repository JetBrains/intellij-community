/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ven
 */
public class MethodSuperSearcher implements QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.MethodSuperSearcher");

  @Override
  public boolean execute(@NotNull final SuperMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<MethodSignatureBackedByPsiMethod> consumer) {
    final PsiClass parentClass = queryParameters.getPsiClass();
    final PsiMethod method = queryParameters.getMethod();
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();

        final boolean checkBases = queryParameters.isCheckBases();
        final boolean allowStaticMethod = queryParameters.isAllowStaticMethod();
        final List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
        for (HierarchicalMethodSignature superSignature : supers) {
          if (MethodSignatureUtil.isSubsignature(superSignature, signature)) {
            if (!addSuperMethods(superSignature, method, parentClass, allowStaticMethod, checkBases, consumer)) return false;
          }
        }

        return true;
      }
    });
  }

  private static boolean addSuperMethods(final HierarchicalMethodSignature signature,
                                         final PsiMethod method,
                                         final PsiClass parentClass,
                                         final boolean allowStaticMethod,
                                         final boolean checkBases,
                                         final Processor<MethodSignatureBackedByPsiMethod> consumer) {
    PsiMethod signatureMethod = signature.getMethod();
    PsiClass hisClass = signatureMethod.getContainingClass();
    if (parentClass == null || InheritanceUtil.isInheritorOrSelf(parentClass, hisClass, true)) {
      if (isAcceptable(signatureMethod, method, allowStaticMethod)) {
        if (parentClass != null && !parentClass.equals(hisClass) && !checkBases) {
          return true;
        }
        LOG.assertTrue(signatureMethod != method, method); // method != method.getsuper()
        return consumer.process(signature); //no need to check super classes
      }
    }
    for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
      if (MethodSignatureUtil.isSubsignature(superSignature, signature)) {
        addSuperMethods(superSignature, method, parentClass, allowStaticMethod, checkBases, consumer);
      }
    }

    return true;
  }

  private static boolean isAcceptable(final PsiMethod superMethod, final PsiMethod method, final boolean allowStaticMethod) {
    boolean hisStatic = superMethod.hasModifierProperty(PsiModifier.STATIC);
    return hisStatic == method.hasModifierProperty(PsiModifier.STATIC) &&
           (allowStaticMethod || !hisStatic) &&
           JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(superMethod, method, null);
  }
}
