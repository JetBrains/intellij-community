/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.Nullable;

public interface PsiFunctionalExpression extends PsiExpression, Iconable, NavigatablePsiElement {
  PsiFunctionalExpression[] EMPTY_ARRAY = new PsiFunctionalExpression[0];
  /**
   * @return SAM type the lambda expression corresponds to
   *         null when no SAM type could be found
   */
  @Nullable
  PsiType getFunctionalInterfaceType();

  /**
   * @return true if assignment SAM s = expr is correctly shaped
   */
  boolean isAcceptable(PsiType left);

  /**
   * Potentially compatible check takes into account the presence and "shape" of functional interface target types.
   *
   * JLS placement:
   * 15.12.2.1 Identify Potentially Applicable Methods
   * A member method is potentially applicable to a method invocation if and only if all of the following are true:
   *  The name of the member is identical to the name of the method in the method invocation.
   *  The member is accessible (§6.6) to the class or interface in which the method invocation appears.
   *  If the member is a fixed arity method with arity n, the arity of the method invocation is equal to n,
   *   and for all i (1 ≤ i ≤ n), the i'th argument of the method invocation is potentially compatible, as defined below,
   *   with the type of the i'th parameter of the method.
   *  If the member is a variable arity method with arity n, etc
   */
  boolean isPotentiallyCompatible(PsiType left);
}
