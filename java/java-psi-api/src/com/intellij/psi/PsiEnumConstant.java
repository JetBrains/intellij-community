// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmEnumField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a constant in a Java enum type.
 *
 * @author dsl
 */
public interface PsiEnumConstant extends PsiField, PsiConstructorCall, JvmEnumField {
  /**
   * Returns the list of arguments passed to the constructor of the enum type to create the
   * instance of the constant.
   *
   * @return the list of arguments, or null
   */
  @Override
  @Nullable
  PsiExpressionList getArgumentList();

  /**
   * Returns the class body attached to the enum constant declaration.
   *
   * @return the enum constant class body, or null if
   * the enum constant does not have one.
   */
  @Nullable
  PsiEnumConstantInitializer getInitializingClass();

  @NotNull
  PsiEnumConstantInitializer getOrCreateInitializingClass();

}
