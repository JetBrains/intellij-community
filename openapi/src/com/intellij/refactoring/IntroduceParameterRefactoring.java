/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiType;

/**
 * @author dsl
 */
public interface IntroduceParameterRefactoring extends Refactoring {
  int REPLACE_FIELDS_WITH_GETTERS_NONE = 0;
  int REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE = 1;
  int REPLACE_FIELDS_WITH_GETTERS_ALL = 2;

  void enforceParameterType(PsiType forcedType);
  void setFieldReplacementPolicy(int policy);

  PsiType getForcedType();
  int getFieldReplacementPolicy();
}
