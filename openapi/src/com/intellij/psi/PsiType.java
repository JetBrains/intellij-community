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

/**
 * Representation of Java type.
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
   * Returns text of this type taht can be presented to user.
   */
  public abstract String getPresentableText();

  public abstract String getCanonicalText();

  public abstract String getInternalCanonicalText();

  public abstract boolean isValid();

  /**
   * Checks whether values of type <code>type</code> can be assigned to rvalues of this type.
   */
  public boolean isAssignableFrom(PsiType type) {
    return TypeConversionUtil.isAssignable(this, type);
  }

  /**
   * Checks whether values of type <code>type</code> can be casted to this type.
   */
  public boolean isConvertibleFrom(PsiType type) {
    return TypeConversionUtil.areTypesConvertible(type, this);
  }


  public abstract boolean equalsToText(String text);

  public static PsiClassType getJavaLangObject(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", resolveScope);
  }

  public static PsiClassType getJavaLangClass(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Class", resolveScope);
  }

  public static PsiClassType getJavaLangTrowable(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Throwable", resolveScope);
  }

  public static PsiClassType getJavaLangString(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.String", resolveScope);
  }

  public static PsiClassType getJavaLangError(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Error", resolveScope);
  }

  public static PsiClassType getJavaLangRuntimeException(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.RuntimeException", resolveScope);
  }

  public abstract <A> A accept(PsiTypeVisitor<A> visitor);

  public final int getArrayDimensions() {
    PsiType type = this;
    int dims = 0;
    while (type instanceof PsiArrayType) {
      dims++;
      type = ((PsiArrayType)type).getComponentType();
    }
    return dims;
  }

  public final PsiType getDeepComponentType() {
    PsiType type = this;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
    }
    return type;
  }
  
  @Nullable(documentation = "for primitives and arrays of primitives")
  public abstract GlobalSearchScope getResolveScope();

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "PsiType:" + getPresentableText();
  }

  public abstract PsiType[] getSuperTypes();
}
