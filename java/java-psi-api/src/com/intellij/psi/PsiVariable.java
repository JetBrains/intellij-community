// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
   * @see #hasInitializer()
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
   * Adds initializer to the variable declaration statement or, if {@code initializer}
   * parameter is null, removes initializer from variable.
   *
   * @param initializer the initializer to add.
   * @throws IncorrectOperationException if the modifications fails, or if this variable does not support initializers (e.g. parameters).
   */
  default void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  /**
   * Ensures that the variable declaration is not combined in the same statement with
   * other declarations. Also, if the variable is an array, ensures that the array
   * brackets are used in Java style ({@code int[] a})
   * and not in C style ({@code int a[]}).
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
}
