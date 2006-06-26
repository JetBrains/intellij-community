/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Representation of Java type (primitive type, array or class type).
 */
public abstract class PsiType {
  public static final PsiType BYTE = PsiPrimitiveType.BYTE;
  public static final PsiType CHAR = PsiPrimitiveType.CHAR;
  public static final PsiType DOUBLE = PsiPrimitiveType.DOUBLE;
  public static final PsiType FLOAT = PsiPrimitiveType.FLOAT;
  public static final PsiType INT = PsiPrimitiveType.INT;
  public static final PsiType LONG = PsiPrimitiveType.LONG;
  public static final PsiType SHORT = PsiPrimitiveType.SHORT;
  public static final PsiType BOOLEAN = PsiPrimitiveType.BOOLEAN;
  public static final PsiType VOID = PsiPrimitiveType.VOID;
  public static final PsiType NULL = PsiPrimitiveType.NULL;
  public static PsiType[] EMPTY_ARRAY = new PsiType[0];

  /**
   * Creates array type with this type as a component.
   */
  public PsiArrayType createArrayType() {
    return new PsiArrayType(this);
  }

  /**
   * Returns text of this type that can be presented to user.
   */
  public abstract String getPresentableText();

  public abstract String getCanonicalText();

  public abstract String getInternalCanonicalText();

  /**
   * Checks if the type is currently valid.
   *
   * @return true if the type is valid, false otherwise.
   * @see PsiElement#isValid()
   */
  public abstract boolean isValid();

  /**
   * Checks whether values of type <code>type</code> can be assigned to rvalues of this type.
   */
  public boolean isAssignableFrom(@NotNull PsiType type) {
    return TypeConversionUtil.isAssignable(this, type);
  }

  /**
   * Checks whether values of type <code>type</code> can be casted to this type.
   */
  public boolean isConvertibleFrom(@NotNull PsiType type) {
    return TypeConversionUtil.areTypesConvertible(type, this);
  }

  /**
   * Checks if the specified string is equivalent to the canonical text of the type.
   *
   * @param text the text to compare with.
   * @return true if the string is equivalent to the type, false otherwise
   */
  public abstract boolean equalsToText(@NonNls String text);

  /**
   * Returns the class type for the java.lang.Object class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangObject(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Class class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangClass(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Class", resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Throwable class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangTrowable(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Throwable", resolveScope);
  }

  /**
   * Returns the class type for the java.lang.String class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangString(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.String", resolveScope);
  }

  /**
   * Returns the class type for the java.lang.Error class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangError(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Error", resolveScope);
  }

  /**
   * Returns the class type for the java.lang.RuntimeException class.
   *
   * @param manager      the PSI manager used to create the class type.
   * @param resolveScope the scope in which the class is searched.
   * @return the class instance.
   */
  public static PsiClassType getJavaLangRuntimeException(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.RuntimeException", resolveScope);
  }

  /**
   * Passes the type to the specified visitor.
   *
   * @param visitor the visitor to accept the type.
   * @return the value returned by the visitor.
   */
  public abstract <A> A accept(PsiTypeVisitor<A> visitor);

  /**
   * Returns the number of array dimensions for the type.
   *
   * @return the number of dimensions, or 0 if the type is not an array type.
   */
  public final int getArrayDimensions() {
    PsiType type = this;
    int dims = 0;
    while (type instanceof PsiArrayType) {
      dims++;
      type = ((PsiArrayType)type).getComponentType();
    }
    return dims;
  }

  /**
   * Returns the innermost component type for an array type.
   *
   * @return the innermost (non-array) component of the type, or <code>this</code> if the type is not
   * an array type.
   */
  @NotNull
  public final PsiType getDeepComponentType() {
    PsiType type = this;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
    }
    return type;
  }

  /**
   * Returns the scope in which the reference to the underlying class of a class type is searched.
   *
   * @return the resolve scope instance, or null if the type is a primitive or an array of primitives.
   */
  @Nullable
  public abstract GlobalSearchScope getResolveScope();

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "PsiType:" + getPresentableText();
  }

  /**
   * Returns the list of superclass types for a class type.
   *
   * @return the array of superclass types, or an empty array if the type is not a class type.
   */
  @NotNull
  public abstract PsiType[] getSuperTypes();
}
