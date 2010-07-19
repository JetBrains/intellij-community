/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java expression.
 */
public interface PsiExpression extends PsiAnnotationMemberValue {
  /**
   * The empty array of PSI expressions which can be reused to avoid unnecessary allocations.
   */
  PsiExpression[] EMPTY_ARRAY = new PsiExpression[0];
  Function<PsiExpression,PsiType> EXPRESSION_TO_TYPE = new Function<PsiExpression, PsiType>() {
    public PsiType fun(final PsiExpression expression) {
      return expression.getType();
    }
  };

  /**
   * Returns the type of the expression.
   *
   * @return the expression type, or null if the type is not known.
   */
  @Nullable
  PsiType getType();
}
