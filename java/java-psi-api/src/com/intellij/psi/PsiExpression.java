// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java expression.
 */
public interface PsiExpression extends PsiAnnotationMemberValue {
  /**
   * The empty array of PSI expressions which can be reused to avoid unnecessary allocations.
   */
  PsiExpression[] EMPTY_ARRAY = new PsiExpression[0];

  ArrayFactory<PsiExpression> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiExpression[count];

  Function<PsiExpression, PsiType> EXPRESSION_TO_TYPE = (NullableFunction<PsiExpression, PsiType>)expression -> expression.getType();

  /**
   * Returns the type of the expression.
   *
   * @return the expression type, or null if the type is not known.
   */
  @Nullable
  @Contract(pure = true)
  PsiType getType();
}
