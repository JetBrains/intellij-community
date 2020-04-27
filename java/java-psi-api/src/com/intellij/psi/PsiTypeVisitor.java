/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Visitor which can be used to visit Java types.
 * 
 * @author dsl
 */
public class PsiTypeVisitor<A> {
  public A visitType(@NotNull PsiType type) {
    return null;
  }

  public A visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
    return visitType(primitiveType);
  }

  public A visitArrayType(@NotNull PsiArrayType arrayType) {
    return visitType(arrayType);
  }

  public A visitClassType(@NotNull PsiClassType classType) {
    return visitType(classType);
  }

  public A visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
    return visitWildcardType(capturedWildcardType.getWildcard());
  }

  public A visitWildcardType(@NotNull PsiWildcardType wildcardType) {
    return visitType(wildcardType);
  }

  public A visitEllipsisType(@NotNull PsiEllipsisType ellipsisType) {
    return visitArrayType(ellipsisType);
  }

  public A visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
    return visitType(disjunctionType);
  }

  public A visitIntersectionType(@NotNull PsiIntersectionType intersectionType) {
    PsiType type = intersectionType.getConjuncts()[0];
    return type.accept(this);
  }

  public A visitDiamondType(@NotNull PsiDiamondType diamondType) {
    return visitType(diamondType);
  }
  
  public A visitLambdaExpressionType(@NotNull PsiLambdaExpressionType lambdaExpressionType) {
    final PsiLambdaExpression lambdaExpression = lambdaExpressionType.getExpression();
    final PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
    if (interfaceType != null && LambdaUtil.isFunctionalType(interfaceType)) return interfaceType.accept(this);
    return visitType(lambdaExpressionType);
  }
  
  public A visitMethodReferenceType(@NotNull PsiMethodReferenceType methodReferenceType) {
    final PsiMethodReferenceExpression expression = methodReferenceType.getExpression();
    final PsiType interfaceType = expression.getFunctionalInterfaceType();
    if (interfaceType != null && LambdaUtil.isFunctionalType(interfaceType)) return interfaceType.accept(this);
    return visitType(methodReferenceType);
  }
}
