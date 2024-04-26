// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Advanced customization interface used in {@link TargetElementUtil} class to support specifics of various languages.
 * The exact API is not documented and is subject to change.
 * Please refer to {@link TargetElementUtil} for additional information.
 */
public abstract class TargetElementEvaluatorEx2 implements TargetElementEvaluator {
  public @Nullable PsiElement getNamedElement(@NotNull PsiElement element) {
    return null;
  }

  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return true;
  }

  public @Nullable PsiElement adjustElement(Editor editor, int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    return element;
  }

  public @Nullable PsiElement adjustTargetElement(Editor editor, int offset, int flags, @NotNull PsiElement targetElement) {
    return targetElement;
  }

  public @Nullable PsiElement adjustReferenceOrReferencedElement(@NotNull PsiFile file,
                                                                 @NotNull Editor editor,
                                                                 int offset,
                                                                 int flags,
                                                                 @Nullable PsiElement refElement) {
    return refElement;
  }

  public @Nullable PsiElement adjustReference(@NotNull PsiReference ref) {
    return null;
  }

  /**
   * Method customizing the default reference resolution and provides a way to return a different set of candidates, 
   * comparing to the default ref.resolve() and ref.multiResolve() methods.
   * The method is calling in places where the target element from the reference is required: e.g. GTD, GTTD, highlighting identifier pass and others  
   */
  public @Nullable Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    return null;
  }

  /**
   * Method customizing GTD navigation element. It is the last place where the navElement can be changed.
   * @apiNote this method is called for all elements, including the ones from GTD direct provides
   */
  public @Nullable PsiElement getGotoDeclarationTarget(final @NotNull PsiElement element, final @Nullable PsiElement navElement) {
    return null;
  }

  public @NotNull ThreeState isAcceptableReferencedElement(@NotNull PsiElement element, @Nullable PsiElement referenceOrReferencedElement) {
    return ThreeState.UNSURE;
  }

  @Override
  public boolean includeSelfInGotoImplementation(final @NotNull PsiElement element) {
    return true;
  }

  public boolean acceptImplementationForReference(@Nullable PsiReference reference, @NotNull PsiElement element) {
    return true;
  }

  /**
   * @return a scope where element's implementations (Goto/Show Implementations) should be searched.
   * If null is returned, default (module-with-dependents) scope will be used.
   */
  public @Nullable SearchScope getSearchScope(Editor editor, @NotNull PsiElement element) {
    return null;
  }
}
