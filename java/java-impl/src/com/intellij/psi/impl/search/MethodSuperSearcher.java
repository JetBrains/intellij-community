/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.List;

/**
 * @author ven
 */
public class MethodSuperSearcher implements QueryExecutor<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.MethodSuperSearcher");

  public boolean execute(final SuperMethodsSearch.SearchParameters queryParameters, final Processor<MethodSignatureBackedByPsiMethod> consumer) {
    final PsiClass parentClass = queryParameters.getPsiClass();
    final PsiMethod method = queryParameters.getMethod();
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
