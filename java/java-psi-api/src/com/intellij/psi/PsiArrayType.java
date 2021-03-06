// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.types.JvmArrayType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Contract;
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

  public PsiArrayType(@NotNull PsiType componentType, PsiAnnotation @NotNull [] annotations) {
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
    return getText(getDeepComponentType().getPresentableText(annotated), "[]", false, annotated);
  }

  @NotNull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(getDeepComponentType().getCanonicalText(annotated), "[]", true, annotated);
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getText(getDeepComponentType().getInternalCanonicalText(), "[]", true, true);
  }

  protected String getText(@NotNull String prefix, @NotNull String suffix, boolean qualified, boolean annotated) {
    int dimensions = getArrayDimensions();
    StringBuilder sb = new StringBuilder(prefix.length() + (dimensions - 1) * 2 + suffix.length());
    sb.append(prefix);
    PsiType current = this;
    for (int i = 0; i < dimensions; i++) {
      if (annotated) {
        PsiAnnotation[] annotations = current.getAnnotations();
        if (annotations.length != 0) {
          sb.append(' ');
          PsiNameHelper.appendAnnotations(sb, annotations, qualified);
        }
      }
      if (i == dimensions - 1) {
        sb.append(suffix);
      } else {
        sb.append("[]");
        current = ((PsiArrayType)current).getComponentType();
      }
    }
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
  public PsiType @NotNull [] getSuperTypes() {
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
  @Contract(pure = true)
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