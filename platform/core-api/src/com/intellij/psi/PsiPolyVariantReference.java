/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Inherit this interface if you want the reference to resolve to more than one element,
 * or if you want to provide resolve result(s) for a superset of valid resolve cases.
 * e.g. in java references in static context are resolved to nonstatic methods in case
 * there is no valid candidate. isValidResult() in this case should return false
 * for later analysis by highlighting pass.
 *
 * @see PsiPolyVariantReferenceBase
 */
public interface PsiPolyVariantReference extends PsiReference {
  /**
   * Returns the results of resolving the reference.
   *
   * @param incompleteCode if true, the code in the context of which the reference is
   * being resolved is considered incomplete, and the method may return additional
   * invalid results.
   *
   * @return the array of results for resolving the reference.
   */
  @NotNull ResolveResult[] multiResolve(final boolean incompleteCode);
}
