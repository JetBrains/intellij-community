/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  /** @deprecated use {@link #annotate(TypeAnnotationProvider)} (to be removed in IDEA 18) */
  public static PsiType createEllipsis(@NotNull PsiType componentType, @NotNull PsiAnnotation[] annotations) {
    return new PsiEllipsisType(componentType, annotations);
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