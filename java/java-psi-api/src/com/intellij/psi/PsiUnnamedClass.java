// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Represents unnamed class from <a href="https://openjdk.org/jeps/445">JEP 445</a>.
 * Please note that it allows a bit more flexibility than the specification itself (e.g., it is allowed not to have the "main" method,
 * but have at least one member to be considered an unnamed class)
 */
@ApiStatus.Experimental
public interface PsiUnnamedClass extends PsiClass {
  @Contract("-> null")
  @Override
  @Nullable String getQualifiedName();

  @Contract("-> null")
  @Override
  @Nullable PsiIdentifier getNameIdentifier();

  @Contract("-> null")
  @Override
  @Nullable String getName();

  @Contract("-> null")
  @Override
  @Nullable PsiElement getLBrace();

  @Contract("-> null")
  @Override
  @Nullable PsiElement getRBrace();

  @Contract("-> null")
  @Override
  @Nullable PsiClass getContainingClass();
}