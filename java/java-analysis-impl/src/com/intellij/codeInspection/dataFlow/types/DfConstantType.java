// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

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
    return obj == this || obj instanceof DfConstantType && Objects.equals(((DfConstantType<?>)obj).myValue, myValue);
  }

  @Override
  public String toString() {
    return renderValue(myValue);
  }

  /**
   * @param dfType dfType to check
   * @param value constant value
   * @return true if given dfType represents a constant that is equal to given value
   */
  public static boolean isConst(@NotNull DfType dfType, @Nullable Object value) {
    return dfType instanceof DfConstantType && Objects.equals(((DfConstantType<?>)dfType).getValue(), value);
  }

  /**
   * @param dfType dfType to extract the constant value from
   * @param clazz desired constant class
   * @param <T> type of the constant
   * @return the constant of given type; null if the supplied dfType is not a constant or its type class differs from the supplied one.
   */
  @Nullable
  public static <T> T getConstantOfType(@NotNull DfType dfType, @NotNull Class<T> clazz) {
    return dfType instanceof DfConstantType ? ObjectUtils.tryCast(((DfConstantType<?>)dfType).getValue(), clazz) : null;
  }

  /**
   * @param value constant value
   * @return human readable representation of the value
   */
  public static String renderValue(Object value) {
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
