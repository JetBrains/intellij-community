// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a {@code provides} directive of a Java module declaration.
 */
public interface PsiProvidesStatement extends PsiStatement {
  PsiProvidesStatement[] EMPTY_ARRAY = new PsiProvidesStatement[0];

  @Nullable PsiJavaCodeReferenceElement getInterfaceReference();
  @Nullable PsiClassType getInterfaceType();
  @Nullable PsiReferenceList getImplementationList();
}