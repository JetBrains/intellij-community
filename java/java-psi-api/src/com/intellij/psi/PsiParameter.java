// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the parameter of a Java method, foreach (enhanced for) statement or catch block.
 */
public interface PsiParameter extends PsiVariable, JvmParameter, PsiJvmModifiersOwner {
  /**
   * The empty array of PSI parameters which can be reused to avoid unnecessary allocations.
   */
  PsiParameter[] EMPTY_ARRAY = new PsiParameter[0];

  ArrayFactory<PsiParameter> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiParameter[count];

  /**
   * Returns the element (method, lambda expression, foreach statement or catch block) in which the
   * parameter is declared.
   *
   * @return the declaration scope for the parameter.
   */
  @NotNull
  PsiElement getDeclarationScope();

  /**
   * Checks if the parameter accepts a variable number of arguments.
   *
   * @return true if the parameter is a vararg, false otherwise
   */
  boolean isVarArgs();

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  PsiTypeElement getTypeElement();

  /* This explicit declaration is required to force javac generate bridge method 'JvmType getType()'; without it calling
  JvmParameter#getType() method on instances which weren't recompiled against the new API will cause AbstractMethodError. */
  @NotNull
  @Override
  PsiType getType();

  // binary compatibility
  @Override
  default PsiAnnotation @NotNull [] getAnnotations() {
    return PsiJvmModifiersOwner.super.getAnnotations();
  }

  @NotNull
  @Override
  @NlsSafe
  String getName();
}
