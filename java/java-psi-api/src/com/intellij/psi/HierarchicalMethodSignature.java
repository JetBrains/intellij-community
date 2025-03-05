// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Data structure which allows efficient retrieval of super methods for a Java method.
 */
public abstract class HierarchicalMethodSignature extends MethodSignatureBackedByPsiMethod {
  public HierarchicalMethodSignature(@NotNull MethodSignatureBackedByPsiMethod signature) {
    super(signature.getMethod(), signature.getSubstitutor(), signature.isRaw(), 
          getParameterTypes(signature.getMethod()), signature.getTypeParameters());
  }

  private static PsiType @NotNull [] getParameterTypes(PsiMethod method) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] paramTypes = PsiType.createArray(parameters.length);
    for (int i = 0; i < paramTypes.length; i++) {
      paramTypes[i] = parameters[i].getType();
    }
    return paramTypes;
  }
  
  /**
   * Returns the list of super method signatures for the specified signature.
   *
   * @return the super method signature list.
   * Note that the list may include signatures for which isSubsignature() check returns false, but erasures are equal 
   */
  public abstract @NotNull List<HierarchicalMethodSignature> getSuperSignatures();

  public @NotNull List<HierarchicalMethodSignature> getInaccessibleSuperSignatures() {
    return Collections.emptyList();
  }
}
