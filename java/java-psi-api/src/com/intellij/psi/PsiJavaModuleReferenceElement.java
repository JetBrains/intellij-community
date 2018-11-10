// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to some module inside a Java module declaration.
 *
 * @since 2016.3
 */
public interface PsiJavaModuleReferenceElement extends PsiElement {
  @NotNull String getReferenceText();
  @Override
  @Nullable PsiPolyVariantReference getReference();
}