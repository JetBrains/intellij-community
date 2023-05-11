// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * An interface for {@link PsiReference}-es that wraps another (multiple) references.
 * Is useful for cases when references need a complicated priority or range computations and/or lazy reference computation.
 * But in general wrapping references should not be considered as a good practice and should be used only in interaction with legacy code.
 */
@ApiStatus.Experimental
public interface PsiReferencesWrapper {

  @Unmodifiable @NotNull List<PsiReference> getReferences();
}
