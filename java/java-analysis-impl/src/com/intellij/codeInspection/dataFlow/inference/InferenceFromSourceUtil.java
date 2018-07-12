// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InferenceFromSourceUtil {
  static boolean shouldInferFromSource(@NotNull PsiMethodImpl method) {
    return CachedValuesManager.getCachedValue(method, () -> CachedValueProvider.Result
      .create(calcShouldInferFromSource(method), method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT));
  }

  private static boolean calcShouldInferFromSource(@NotNull PsiMethod method) {
    if (isLibraryCode(method) ||
        method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        PsiUtil.canBeOverridden(method)) {
      return false;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)) return true;

    return !isUnusedInAnonymousClass(method);
  }

  private static boolean isUnusedInAnonymousClass(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (!(containingClass instanceof PsiAnonymousClass)) {
      return false;
    }

    if (containingClass.getParent() instanceof PsiNewExpression && 
        containingClass.getParent().getParent() instanceof PsiVariable && 
        !method.getHierarchicalMethodSignature().getSuperSignatures().isEmpty()) {
      // references outside anonymous class can still resolve to this method, see com.intellij.psi.scope.util.PsiScopesUtil.setupAndRunProcessor()
      return false;
    }

    return MethodReferencesSearch.search(method, new LocalSearchScope(containingClass), false).findFirst() == null;
  }

  private static boolean isLibraryCode(@NotNull PsiMethod method) {
    if (method instanceof PsiCompiledElement) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
    return virtualFile != null && FileIndexFacade.getInstance(method.getProject()).isInLibrarySource(virtualFile);
  }

  public static boolean suppressNullable(PsiMethod method) {
    if (method.getParameterList().isEmpty()) return false;

    for (StandardMethodContract contract : JavaMethodContractUtil.getMethodContracts(method)) {
      if (contract.getReturnValue().isNull()) {
        return true;
      }
    }
    return false;
  }
}
