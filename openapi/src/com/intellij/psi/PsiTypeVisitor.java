/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *  @author dsl
 */
public class PsiTypeVisitor<A> {
  public A visitType(PsiType type) {
    return null;
  }

  public A visitPrimitiveType(PsiPrimitiveType primitiveType) {
    return visitType(primitiveType);
  }

  public A visitArrayType(PsiArrayType arrayType) {
    return visitType(arrayType);
  }

  public A visitClassType(PsiClassType classType) {
    return visitType(classType);
  }

  public A visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
    return visitWildcardType(capturedWildcardType.getWildcard());
  }

  public A visitWildcardType(PsiWildcardType wildcardType) {
    return visitType(wildcardType);
  }

  public A visitEllipsisType(PsiEllipsisType ellipsisType) {
    return visitArrayType(ellipsisType);
  }
}
