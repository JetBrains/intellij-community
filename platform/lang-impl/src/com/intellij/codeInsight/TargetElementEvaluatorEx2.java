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
  @Nullable 
  public PsiElement getNamedElement(@NotNull PsiElement element) {
    return null;
  }
  
  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return true;
  }
                   
  @Nullable
  public PsiElement adjustElement(Editor editor, int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    return element;
  }

  @Nullable
  public PsiElement adjustTargetElement(Editor editor, int offset, int flags, @NotNull PsiElement targetElement) {
    return targetElement;
  }

  @Nullable
  public PsiElement adjustReferenceOrReferencedElement(PsiFile file,
                                                       Editor editor,
                                                       int offset,
                                                       int flags,
                                                       @Nullable PsiElement refElement) {
    return refElement;
  }

  @Nullable
  public PsiElement adjustReference(@NotNull PsiReference ref) {
    return null; 
  }

  @Nullable
  public Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    return null;
  }

  @Nullable
  public PsiElement getGotoDeclarationTarget(@NotNull final PsiElement element, @Nullable final PsiElement navElement) {
    return null;
  }

  @NotNull
  public ThreeState isAcceptableReferencedElement(@NotNull PsiElement element, @Nullable PsiElement referenceOrReferencedElement) {
    return ThreeState.UNSURE;
  }

  public boolean includeSelfInGotoImplementation(@NotNull final PsiElement element) {
    return true;
  }
  
  public boolean acceptImplementationForReference(@Nullable PsiReference reference, @NotNull PsiElement element) {
    return true;  
  }

  @Nullable
  public SearchScope getSearchScope(Editor editor, @NotNull PsiElement element) {
    return null;
  }
}
