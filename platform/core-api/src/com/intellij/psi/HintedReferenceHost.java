/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Implementing this interface allows for better control over the references associated with a PSI element.
 * <p/>
 * Instead of {@link PsiElement#getReferences()}, clients can call {@link #getReferences(PsiReferenceService.Hints)} and the implementation
 * may use the hints for performance optimizations, e.g. to avoid finding and creating references that won't intersect the given offset
 * ({@link com.intellij.psi.PsiReferenceService.Hints#offsetInElement}) or that have no chance of resolving to a particular target
 * ({@link com.intellij.psi.PsiReferenceService.Hints#target}).
 *
 * @author peter
 * @since 144.*
 */
public interface HintedReferenceHost extends PsiElement {

  /**
   * Same as {@link PsiElement#getReferences()}, but the implementation may take hints into account and return only references that match these hints.
   * But it's not a hard requirement, so the clients should not rely that only matching references will be returned.
   *
   * @param hints the hints about the desired references
   * @return the array of references, or an empty array if the element has no associated references.
   */
  @NotNull
  PsiReference[] getReferences(@NotNull PsiReferenceService.Hints hints);

  /**
   * Normally in {@link PsiElement#findReferenceAt(int)}, all tree hierarchy is traversed bottom-up and each element is asked for references.
   * Quite often it's not needed, because references tend to be contained close to the tree leaves, and their ancestors won't return anything
   * useful for given offsets anyway. This method makes it possible to stop such bottom-up traversals early and thus improve performance,
   * if the implementation knows for sure that no tree ancestor of this element can contain references matching the specified hints.
   *
   * @param hints the hints about the desired references
   * @return false if there's no use in asking this element's ancestors for references with specified hints, true otherwise.
   */
  boolean shouldAskParentForReferences(@NotNull PsiReferenceService.Hints hints);
}
