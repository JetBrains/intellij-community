/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Inherit this interface if you want the reference to resolve to more than one element,
 * or if you want to provide resolve result(s) for a superset of valid resolve cases.
 * e.g. in java references in static context are resolved to nonstatic methods in case
 * there is no valid candidate. isValidResult() in this case should return false
 * for later analysis by highlighting pass.
 */
public interface PsiPolyVariantReference extends PsiReference {
  @NotNull ResolveResult[] multiResolve(final boolean incompleteCode);
}
