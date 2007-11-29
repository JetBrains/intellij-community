/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Trivial implementation of {@link ResolveResult}.
 */
public class PsiElementResolveResult implements ResolveResult{
  @NotNull private final PsiElement myElement;

  /**
   * Creates a resolve result with the specified resolve target.
   *
   * @param element the resolve target element.
   */
  public PsiElementResolveResult(@NotNull PsiElement element) {
    myElement = element;
  }

  @NotNull public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return true;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PsiElementResolveResult that = (PsiElementResolveResult)o;

    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  public int hashCode() {
    return myElement.hashCode();
  }

  @NonNls
  public String toString() {
    return "PsiElementResolveResult: " + myElement.getText();
  }
}
