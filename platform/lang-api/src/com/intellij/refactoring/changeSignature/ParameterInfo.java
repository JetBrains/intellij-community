// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a parameter of a method affected by the "Change Signature" refactoring.
 */
public interface ParameterInfo {

  int NEW_PARAMETER = -1;

  /**
   * Returns the name of the parameter after the refactoring.
   *
   * @return parameter name.
   */
  @NlsSafe
  String getName();

  /**
   * Returns the index of the parameter in the old parameter list, or {@link #NEW_PARAMETER} if the parameter
   * was added by the refactoring.
   *
   * @return old parameter index, or {@link #NEW_PARAMETER}.
   */
  int getOldIndex();

  /**
   * Returns {@code true} if the parameter was added by the refactoring.
   *
   * @return {@code true} if the parameter was added by the refactoring
   */
  default boolean isNew() {
    return getOldIndex() == NEW_PARAMETER;
  }

  /**
   * For added parameters, returns the string representation of the default parameter value.
   *
   * @return default value, or null if the parameter wasn't added.
   */
  @Nullable
  @NlsSafe
  String getDefaultValue();

  /**
   * For added parameters, returns expression which should be created at the call site.
   * By default it's expression based on {@link #getDefaultValue()} string representation or default value for a type
   * Could be overridden to provide values which depend on the call site
   */
  @Nullable
  default PsiElement getActualValue(PsiElement callExpression, Object substitutor) {
    return null;
  }

  /**
   * Set parameter new name (to be changed to during refactoring)
   *
   * @param name new name
   */
  void setName(@NlsSafe String name);

  /**
   * Returns parameter type text
   *
   * @return type text
   */
  @NlsSafe
  String getTypeText();

  /**
   * Flag whether refactoring should use any appropriate nearby variable as the default value
   *
   * @return flag value
   */
  boolean isUseAnySingleVariable();


  /**
   * Flag whether refactoring should use any appropriate nearby variable as the default value
   *
   * @param b new value
   */
  void setUseAnySingleVariable(boolean b);
}
