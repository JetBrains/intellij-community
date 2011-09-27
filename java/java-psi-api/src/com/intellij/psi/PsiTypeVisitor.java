/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Visitor which can be used to visit Java types.
 * 
 * @author dsl
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

  public A visitDisjunctionType(PsiDisjunctionType disjunctionType) {
    return visitType(disjunctionType);
  }
}
