/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the call of a Java class constructor (a simple or qualified class instance
 * creation expression, or an array creation expression).
 */
public interface PsiNewExpression extends PsiCallExpression, PsiConstructorCall {
  /**
   * Returns the qualifier (expression specifying instance of outer class) for a
   * qualified class instance creation expression.
   *
   * @return the qualifier, or null if the expression is not qualified.
   */
  @Nullable
  PsiExpression getQualifier();

  /**
   * Returns the expressions specifying the dimensions of the created array in
   * an array creation expression.
   *
   * @return the array of expressions for the dimensions, or an empty array if the
   *         <code>new</code> expression is not an array creation expression.
   */
  @NotNull
  PsiExpression[] getArrayDimensions();

  /**
   * Returns the expression specifying the initializer for the created array in
   * an array creation expression.
   *
   * @return the array initializer expression, or null if the <code>new</code>
   *         expression is not an array creation expression or has no initializer.
   */
  @Nullable
  PsiArrayInitializerExpression getArrayInitializer();

  /**
   * Returns the reference element specifying the class the instance of which is created.
   *
   * @return class reference, or null if the expression is incomplete.
   */
  @Nullable
  PsiJavaCodeReferenceElement getClassReference();

  /**
   * Returns the anonymous class created by the <code>new</code> expression.
   *
   * @return the anonymous class, or null if the expression does not create an anonymous class.
   */
  @Nullable
  PsiAnonymousClass getAnonymousClass();

  /**
   * Returns the reference element specifying the class the instance of which is created,
   * or, if it's an anonymous class creation, corresponding base class reference.
   *
   * @return class reference, or null if the expression is incomplete.
   */
  @Nullable
  PsiJavaCodeReferenceElement getClassOrAnonymousClassReference();

  /**
   * For type-annotated array creation expressions returns subtype of getType(),
   * to which an annotation belongs.
   *
   * @param annotation annotation to find the type for.
   * @return annotated subtype or null, if annotation is incorrectly placed.
   */
  @Nullable
  PsiType getOwner(@NotNull PsiAnnotation annotation);
}
