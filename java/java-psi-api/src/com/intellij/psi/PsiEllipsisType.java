// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the type of a variable arguments array passed as a method parameter.
 *
 * @author ven
 */
public class PsiEllipsisType extends PsiArrayType {
  public PsiEllipsisType(@NotNull PsiType componentType) {
    super(componentType);
  }

  public PsiEllipsisType(@NotNull PsiType componentType, @NotNull PsiAnnotation[] annotations) {
    super(componentType, annotations);
  }

  public PsiEllipsisType(@NotNull PsiType componentType, @NotNull TypeAnnotationProvider provider) {
    super(componentType, provider);
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    return getText(getComponentType().getPresentableText(), "...", false, annotated);
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(getComponentType().getCanonicalText(annotated), "...", true, annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getText(getComponentType().getInternalCanonicalText(), "...", true, true);
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3)) ||
           super.equalsToText(text);
  }

  /**
   * Converts the ellipsis type to an array type with the same component type.
   *
   * @return the array type instance.
   */
  @Contract(pure = true)
  public PsiType toArrayType() {
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