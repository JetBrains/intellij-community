/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

public interface PsiAssertStatement extends PsiStatement{
  @Nullable
  PsiExpression getAssertCondition();

  @Nullable
  PsiExpression getAssertDescription();
}