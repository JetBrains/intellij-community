// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

public interface PsiRecordComponent extends PsiMember, PsiVariable {
  PsiRecordComponent[] EMPTY_ARRAY = new PsiRecordComponent[]{};

  /**
   * Checks if the record component accepts a variable number of arguments in canonical constructor.
   *
   * @return true if the record component is a vararg, false otherwise
   */
  boolean isVarArgs();
}
