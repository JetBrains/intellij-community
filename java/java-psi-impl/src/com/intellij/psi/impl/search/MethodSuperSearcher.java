// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MethodSuperSearcher extends QueryExecutorBase<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance(MethodSuperSearcher.class);

  public MethodSuperSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull SuperMethodsSearch.SearchParameters queryParameters,
                           @NotNull Processor<? super MethodSignatureBackedByPsiMethod> consumer) {
    PsiClass parentClass = queryParameters.getPsiClass();
    PsiMethod method = queryParameters.getMethod();
    HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();

    boolean checkBases = queryParameters.isCheckBases();
    boolean allowStaticMethod = queryParameters.isAllowStaticMethod();
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      if (MethodSignatureUtil.isSubsignature(superSignature, signature)) {
        if (!addSuperMethods(superSignature, method, parentClass, allowStaticMethod, checkBases, consumer)) return;
      }
    }
  }

  private static boolean addSuperMethods(final HierarchicalMethodSignature signature,
                                         final PsiMethod method,
                                         final PsiClass parentClass,
                                         final boolean allowStaticMethod,
                                         final boolean checkBases,
                                         final Processor<? super MethodSignatureBackedByPsiMethod> consumer) {
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
