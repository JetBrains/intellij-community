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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the type of a variable arguments array passed as a method parameter.
 *
 * @author ven
 */
public class PsiEllipsisType extends PsiArrayType {
  /**
   * Creates an ellipsis type instance with the specified component type.
   *
   * @param componentType the type of the varargs array component.
   */
  public PsiEllipsisType(@NotNull PsiType componentType) {
    super(componentType);
  }

  public PsiEllipsisType(@NotNull PsiType componentType, @NotNull PsiAnnotation[] annotations) {
    super(componentType, annotations);
  }

  @NotNull
  public static PsiType createEllipsis(@NotNull PsiType componentType, @NotNull PsiAnnotation[] annotations) {
    return new PsiEllipsisType(componentType, annotations);
  }

  @Override
  public String getPresentableText() {
    return StringUtil.joinOrNull(getComponentType().getPresentableText(), getAnnotationsTextPrefix(false, true, true), "...");
  }

  @Override
  public String getCanonicalText() {
    return StringUtil.joinOrNull(getComponentType().getCanonicalText(), "...");
  }

  @Override
  public String getInternalCanonicalText() {
    return StringUtil.joinOrNull(getComponentType().getInternalCanonicalText(), getAnnotationsTextPrefix(true, true, true), "...");
  }

  @Override
  public boolean equalsToText(String text) {
    return text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3)) ||
           super.equalsToText(text);
  }

  /**
   * Converts the ellipsis type to an array type with the same component type.
   *
   * @return the array type instance.
   */
  public PsiType toArrayType() {
    return getComponentType().createArrayType(getAnnotations());
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitEllipsisType(this);
  }

  public boolean equals(Object obj) {
    return obj instanceof PsiEllipsisType && super.equals(obj);
  }

  public int hashCode() {
    return super.hashCode() * 5;
  }
}
