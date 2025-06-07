// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.codeInsight.TypeNullability;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the type of a variable arguments array passed as a method parameter.
 */
public class PsiEllipsisType extends PsiArrayType {
  public PsiEllipsisType(@NotNull PsiType componentType) {
    super(componentType);
  }

  public PsiEllipsisType(@NotNull PsiType componentType, PsiAnnotation @NotNull [] annotations) {
    super(componentType, annotations);
  }

  public PsiEllipsisType(@NotNull PsiType componentType, @NotNull TypeAnnotationProvider provider) {
    super(componentType, provider);
  }
  
  private PsiEllipsisType(@NotNull PsiType componentType, @NotNull TypeAnnotationProvider provider, @NotNull TypeNullability nullability) {
    super(componentType, provider, nullability);
  }

  @Override
  public @NotNull String getPresentableText(boolean annotated) {
    return getText(getDeepComponentType().getPresentableText(annotated), "...", false, annotated);
  }

  @Override
  public @NotNull String getCanonicalText(boolean annotated) {
    return getText(getDeepComponentType().getCanonicalText(annotated), "...", true, annotated);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    return getText(getDeepComponentType().getInternalCanonicalText(), "...", true, true);
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3)) ||
           super.equalsToText(text);
  }

  @Override
  public @NotNull PsiEllipsisType withNullability(@NotNull TypeNullability nullability) {
    return new PsiEllipsisType(getComponentType(), getAnnotationProvider(), nullability);
  }

  /**
   * Converts the ellipsis type to an array type with the same component type.
   *
   * @return the array type instance.
   */
  @Contract(pure = true)
  public @NotNull PsiType toArrayType() {
    return new PsiArrayType(getComponentType(), getAnnotationProvider());
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitEllipsisType(this);
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 5;
  }
}