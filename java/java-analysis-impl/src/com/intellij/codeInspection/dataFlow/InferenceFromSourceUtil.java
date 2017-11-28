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
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  static boolean isReturnTypeCompatible(@Nullable PsiType returnType, @NotNull MethodContract.ValueConstraint returnValue) {
    if (returnValue == MethodContract.ValueConstraint.ANY_VALUE || returnValue == MethodContract.ValueConstraint.THROW_EXCEPTION) {
      return true;
    }
    if (PsiType.VOID.equals(returnType)) return false;

    if (PsiType.BOOLEAN.equals(returnType)) {
      return returnValue == MethodContract.ValueConstraint.TRUE_VALUE ||
             returnValue == MethodContract.ValueConstraint.FALSE_VALUE;
    }

    if (!(returnType instanceof PsiPrimitiveType)) {
      return returnValue == MethodContract.ValueConstraint.NULL_VALUE ||
             returnValue == MethodContract.ValueConstraint.NOT_NULL_VALUE;
    }

    return false;
  }

  static boolean suppressNullable(PsiMethod method) {
    if (method.getParameterList().getParametersCount() == 0) return false;

    for (StandardMethodContract contract : ControlFlowAnalyzer.getMethodContracts(method)) {
      if (contract.returnValue == MethodContract.ValueConstraint.NULL_VALUE) {
        return true;
      }
    }
    return false;
  }
}
