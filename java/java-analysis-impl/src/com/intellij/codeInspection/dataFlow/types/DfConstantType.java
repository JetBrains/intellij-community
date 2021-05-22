// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a type that contains only one value
 */
public abstract class DfConstantType<T> implements DfType {
  private final T myValue;
  
  DfConstantType(T value) {
    myValue = value;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    return other.equals(this) || other == DfTypes.BOTTOM;
  }

  @NotNull
  public abstract PsiType getPsiType();

  @NotNull
  @Override
  public DfType meet(@NotNull DfType other) {
    return other.isSuperType(this) ? this : DfTypes.BOTTOM;
  }

  /**
   * @return a value representation (different constants have different representation)
   */
  public T getValue() {
    return myValue;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myValue);
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || 
           obj != null && obj.getClass() == getClass() && Objects.equals(((DfConstantType<?>)obj).myValue, myValue);
  }

  @Override
  public String toString() {
    return renderValue(myValue);
  }

  @Override
  public boolean isConst(@Nullable Object constant) {
    return Objects.equals(myValue, constant);
  }

  @Override
  public <C> @Nullable C getConstantOfType(@NotNull Class<C> clazz) {
    return ObjectUtils.tryCast(myValue, clazz);
  }

  /**
   * @param value constant value
   * @return human readable representation of the value
   */
  public static @NlsSafe String renderValue(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return '"' + StringUtil.escapeStringCharacters((String)value) + '"';
    if (value instanceof Float) return value + "f";
    if (value instanceof Long) return value + "L";
    if (value instanceof PsiField) {
      PsiField field = (PsiField)value;
      PsiClass containingClass = field.getContainingClass();
      return containingClass == null ? field.getName() : containingClass.getName() + "." + field.getName();
    }
    if (value instanceof PsiType) {
      return ((PsiType)value).getPresentableText();
    }
    return value.toString();
  }
}
