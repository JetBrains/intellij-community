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

import com.intellij.lang.jvm.types.JvmArrayType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an array type.
 *
 * @author max
 */
public class PsiArrayType extends PsiType.Stub implements JvmArrayType {
  private final PsiType myComponentType;

  public PsiArrayType(@NotNull PsiType componentType) {
    this(componentType, TypeAnnotationProvider.EMPTY);
  }

  public PsiArrayType(@NotNull PsiType componentType, @NotNull PsiAnnotation[] annotations) {
    super(annotations);
    myComponentType = componentType;
  }

  public PsiArrayType(@NotNull PsiType componentType, @NotNull TypeAnnotationProvider provider) {
    super(provider);
    myComponentType = componentType;
  }

  @NotNull
  @Override
  public String getPresentableText(boolean annotated) {
    return getText(myComponentType.getPresentableText(), "[]", false, annotated);
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(myComponentType.getCanonicalText(annotated), "[]", true, annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getText(myComponentType.getInternalCanonicalText(), "[]", true, true);
  }

  protected String getText(@NotNull String prefix, @NotNull String suffix, boolean qualified, boolean annotated) {
    StringBuilder sb = new StringBuilder(prefix.length() + suffix.length());
    sb.append(prefix);
    if (annotated) {
      PsiAnnotation[] annotations = getAnnotations();
      if (annotations.length != 0) {
        sb.append(' ');
        PsiNameHelper.appendAnnotations(sb, annotations, qualified);
      }
    }
    sb.append(suffix);
    return sb.toString();
  }

  @Override
  public boolean isValid() {
    for (PsiAnnotation annotation : getAnnotations()) {
      if (!annotation.isValid()) return false;
    }
    return myComponentType.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.endsWith("[]") && myComponentType.equalsToText(text.substring(0, text.length() - 2));
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitArrayType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myComponentType.getResolveScope();
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    final PsiType[] superTypes = myComponentType.getSuperTypes();
    final PsiType[] result = createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      result[i] = superTypes[i].createArrayType();
    }
    return result;
  }

  /**
   * Returns the component type of the array.
   *
   * @return the component type instance.
   */
  @NotNull
  @Override
  public PsiType getComponentType() {
    return myComponentType;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PsiArrayType &&
           (this instanceof PsiEllipsisType == obj instanceof PsiEllipsisType) &&
           myComponentType.equals(((PsiArrayType)obj).getComponentType());
  }

  @Override
  public int hashCode() {
    return myComponentType.hashCode() * 3;
  }
}