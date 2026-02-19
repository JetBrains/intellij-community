// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.hierarchy.call;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * The CallHierarchyElementProvider ExtensionPoint is designed to augment the Call Hierarchy results (specifically the "Caller Hierarchy" view).
 * Its primary purpose is to allow plugins to provide "virtual" or "implicit" relationships between code elements that are not represented
 * by standard physical PsiReference objects in the source code.
 * This is particularly useful for frameworks that generate code or use bytecode manipulation (like Lombok),
 * where a method or field might be logically "called" or "used" by elements that the standard Java reference search cannot find.
 */
public interface CallHierarchyElementProvider {
  ExtensionPointName<CallHierarchyElementProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.hierarchy.elementProvider");

  /**
   * Collects augmented elements, which are not really present in code but virtually references to psiMember
   * @param psiMember some PsiMember to find referenced elements for
   * @return collection of virtually augmented elements references to psiMember
   */
  @NotNull
  Collection<PsiElement> provideReferencedMembers(@NotNull PsiMember psiMember);


  /**
   * Collects augmented PsiMethods, which are not really present in code but virtually references to methodToFind
   * @param methodToFind some PsiMethod to find virtually referenced other PsiMethods for
   ** @return collection of virtually augmented PsiMethods references to methodToFind
   */
  @NotNull
  Collection<PsiMethod> provideReferencedMethods(@NotNull PsiMethod methodToFind);
}
