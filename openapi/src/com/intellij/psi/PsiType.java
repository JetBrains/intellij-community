/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;

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

  /**
   * @deprecated use {@link #getJavaLangObject(PsiManager, GlobalSearchScope)}
   */
  public static PsiClassType getJavaLangObject(PsiManager manager) {
    return getJavaLangObject(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  public static PsiClassType getJavaLangObject(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Object", resolveScope);
  }

  /**
   * @deprecated use {@link #getJavaLangClass(PsiManager, GlobalSearchScope)}
   */
  public static PsiType getJavaLangClass(PsiManager manager) {
    return getJavaLangClass(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  public static PsiType getJavaLangClass(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Class", resolveScope);
  }

  /**
   * @deprecated use {@link #getJavaLangString(PsiManager, GlobalSearchScope)}
   */
  public static PsiType getJavaLangString(PsiManager manager) {
    return getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  public static PsiType getJavaLangString(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.String", resolveScope);
  }

  /**
   * @deprecated use {@link #getJavaLangError(PsiManager, GlobalSearchScope)}
   */
  public static PsiType getJavaLangError(PsiManager manager) {
    return getJavaLangError(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  public static PsiType getJavaLangError(PsiManager manager, GlobalSearchScope resolveScope) {
    return manager.getElementFactory().createTypeByFQClassName("java.lang.Error", resolveScope);
  }

  /**
   * @deprecated use {@link #getJavaLangRuntimeException(PsiManager, GlobalSearchScope)}
   */
  public static PsiType getJavaLangRuntimeException(PsiManager manager) {
    return getJavaLangRuntimeException(manager, GlobalSearchScope.allScope(manager.getProject()));
  }

  public static PsiType getJavaLangRuntimeException(PsiManager manager, GlobalSearchScope resolveScope) {
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

  /**
   * @MaybeNull(description = "null for primitives and arrays of primitives") 
   */
    public abstract GlobalSearchScope getResolveScope();

  public String toString() {
    return "PsiType:" + getPresentableText();
  }

  public abstract PsiType[] getSuperTypes();
}
