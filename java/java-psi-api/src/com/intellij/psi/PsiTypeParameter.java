// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.lang.jvm.JvmTypeParameter;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the type parameter of a generic class, interface, method or constructor.
 *
 * @author dsl
 */
public interface PsiTypeParameter extends PsiClass, PsiAnnotationOwner, JvmTypeParameter {
  /**
   * The empty array of PSI type parameters which can be reused to avoid unnecessary allocations.
   */
  PsiTypeParameter[] EMPTY_ARRAY = new PsiTypeParameter[0];

  ArrayFactory<PsiTypeParameter> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiTypeParameter[count];

  /**
   * Returns the extends list of the type parameter.
   *
   * @return the extends list. For this particular kind of classes it never returns null.
   */
  @Override
  @NotNull
  PsiReferenceList getExtendsList();

  /**
   * Returns the element which is parameterized by the type parameter.
   *
   * @return the type parameter owner instance.
   */
  @Nullable
  @Override
  PsiTypeParameterListOwner getOwner();

  /**
   * Returns the position of this type parameter in the type parameter list of the owner element.
   *
   * @return the type parameter position.
   */
  int getIndex();

  @NotNull
  @Override
  default PsiAnnotation[] getAnnotations() {
    return PsiClass.super.getAnnotations();
  }

  @Override
  default boolean hasAnnotation(@NotNull @NonNls String fqn) {
    return PsiClass.super.hasAnnotation(fqn);
  }

  @NotNull
  @Override
  default JvmReferenceType[] getBounds() {
    return getExtendsList().getReferencedTypes();
  }

  @Nullable
  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return JvmTypeParameter.super.accept(visitor);
  }
}
