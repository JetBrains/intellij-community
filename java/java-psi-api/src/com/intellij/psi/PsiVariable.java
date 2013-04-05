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

import com.intellij.pom.PomRenameableTarget;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * Represents a Java local variable, method parameter or field.
 */
public interface PsiVariable extends PsiModifierListOwner, PsiNameIdentifierOwner, PsiTarget, PomRenameableTarget<PsiElement> {
  /**
   * Returns the type of the variable.
   *
   * @return the variable type.
   */
  @NotNull
  PsiType getType();

  /**
   * Returns the type element declaring the type of the variable.
   *
   * @return the type element for the variable type.
   */
  @Nullable
  PsiTypeElement getTypeElement();

  /**
   * Returns the initializer for the variable.
   *
   * @return the initializer expression, or null if it has no initializer.
   * @see {@link #hasInitializer()}
   */
  @Nullable
  PsiExpression getInitializer();

  /**
   * <p>Checks if the variable has an initializer.</p>
   * <p>Please note that even when {@link #hasInitializer()} returns true, {@link #getInitializer()} still can return null,
   * e.g. for implicit initializer in case of enum constant declaration.</p>
   *
   * @return true if the variable has an initializer, false otherwise.
   */
  boolean hasInitializer();

  /**
   * Ensures that the variable declaration is not combined in the same statement with
   * other declarations. Also, if the variable is an array, ensures that the array
   * brackets are used in Java style (<code>int[] a</code>)
   * and not in C style (<code> int a[]</code>).
   *
   * @throws IncorrectOperationException if the modification fails for some reason.
   */
  void normalizeDeclaration() throws IncorrectOperationException; // Q: split into normalizeBrackets and splitting declarations?

  /**
   * Calculates and returns the constant value of the variable initializer.
   *
   * @return the calculated value, or null if the variable has no initializer or
   *         the initializer does not evaluate to a constant.
   */
  @Nullable
  Object computeConstantValue();

  /**
   * Returns the identifier declaring the name of the variable.
   *
   * @return the variable name identifier.
   */
  @Override
  @Nullable
  PsiIdentifier getNameIdentifier();

  @Override
  PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException;

  /** @deprecated use {@link #getType()} (to remove in IDEA 13) */
  PsiType getTypeNoResolve();
}
