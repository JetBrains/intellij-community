// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmField;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java field or enum constant.
 */
public interface PsiField extends PsiMember, PsiVariable, PsiDocCommentOwner, JvmField {
  /**
   * The empty array of PSI fields which can be reused to avoid unnecessary allocations.
   */
  PsiField[] EMPTY_ARRAY = new PsiField[0];

  ArrayFactory<PsiField> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiField[count];

  /**
   * Adds initializer to the field declaration or, if {@code initializer} parameter is null,
   * removes the initializer from the field declaration.
   *
   * @param initializer the initializer to add.
   * @throws IncorrectOperationException if the modifications fails for some reason.
   * @since 5.0.2
   */
  @Override
  void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException;

  @Override
  @NotNull PsiIdentifier getNameIdentifier();

  /* This explicit declaration is required to force javac generate bridge method 'JvmType getType()'; without it calling
  JvmField#getType() method on instances which weren't recompiled against the new API will cause AbstractMethodError. */
  @NotNull
  @Override
  PsiType getType();
}
