// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.model.SymbolResolveResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Inherit this interface if you want the reference to resolve to more than one element,
 * or if you want to provide resolve result(s) for a superset of valid resolve cases.
 * e.g. in Java references in static context are resolved to non-static methods in case
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
  ResolveResult @NotNull [] multiResolve(boolean incompleteCode);

  @NotNull
  @Override
  default Collection<? extends SymbolResolveResult> resolveReference() {
    ResolveResult[] results = multiResolve(false);
    return ContainerUtil.filter(results, it -> it.getElement() != null);
  }
}
