/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

/**
 * Represents a method or constructor reference.
 */
public interface PsiMethodReferenceExpression extends PsiReferenceExpression, PsiFunctionalExpression {
  /**
   * Returns the type element used as the qualifier of the reference.
   *
   * @return the qualifier, or null if the reference is qualified by expression.
   */
  @Nullable
  PsiTypeElement getQualifierType();

  /**
   * @return if there is only one possible compile-time declaration with only one possible invocation type,
   *         regardless of the targeted function type return true, false otherwise
   */
  boolean isExact();

  /**
   * 15.12.2.1 Identify Potentially Applicable Methods
   * .................................................
   * A method reference (15.13) is potentially compatible with a functional interface type if, where the type's function type arity is n,
   * there exists at least one potentially-applicable method for the method reference at arity n (15.13.1), and one of the following is true:
   *   The method reference has the form ReferenceType::NonWildTypeArgumentsOpt Identifier and at least one potentially-applicable method either
   *      i) is declared static and supports arity n, or
   *      ii) is not declared static and supports arity n-1.
   *   The method reference has some other form and at least one potentially-applicable method is not declared static.
   */
  boolean isPotentiallyCompatible(PsiType functionalInterfaceType);

  /**
   * @return potential applicable member for exact reference, otherwise null
   */
  @Nullable
  PsiMember getPotentiallyApplicableMember();

  /**
   * @return true if reference is of form ClassType::new
   */
  boolean isConstructor();

  /**
   * Potentially compatible, and if exact - congruent
   */
  boolean isAcceptable(PsiType left);
}