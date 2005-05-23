/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public interface PsiArrayAccessExpression extends PsiExpression {
  @NotNull
  PsiExpression getArrayExpression();

  @Nullable
  PsiExpression getIndexExpression();
}
