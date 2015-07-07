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

import org.jetbrains.annotations.Nullable;

/**
 * Visitor which can be used to visit Java types.
 * 
 * @author dsl
 */
public class PsiTypeVisitor<A> {
  @Nullable
  public A visitType(PsiType type) {
    return null;
  }

  @Nullable
  public A visitPrimitiveType(PsiPrimitiveType primitiveType) {
    return visitType(primitiveType);
  }

  @Nullable
  public A visitArrayType(PsiArrayType arrayType) {
    return visitType(arrayType);
  }

  @Nullable
  public A visitClassType(PsiClassType classType) {
    return visitType(classType);
  }

  @Nullable
  public A visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
    return visitWildcardType(capturedWildcardType.getWildcard());
  }

  @Nullable
  public A visitWildcardType(PsiWildcardType wildcardType) {
    return visitType(wildcardType);
  }

  @Nullable
  public A visitEllipsisType(PsiEllipsisType ellipsisType) {
    return visitArrayType(ellipsisType);
  }

  @Nullable
  public A visitDisjunctionType(PsiDisjunctionType disjunctionType) {
    return visitType(disjunctionType);
  }

  @Nullable
  public A visitIntersectionType(PsiIntersectionType intersectionType) {
    PsiType type = intersectionType.getConjuncts()[0];
    return type.accept(this);
  }

  @Nullable
  public A visitDiamondType(PsiDiamondType diamondType) {
    return visitType(diamondType);
  }
  
  @Nullable
  public A visitLambdaExpressionType(PsiLambdaExpressionType lambdaExpressionType) {
    final PsiLambdaExpression lambdaExpression = lambdaExpressionType.getExpression();
    final PsiType interfaceType = lambdaExpression.getFunctionalInterfaceType();
    if (interfaceType != null && LambdaUtil.isFunctionalType(interfaceType)) return interfaceType.accept(this);
    return visitType(lambdaExpressionType);
  }
  
  public A visitMethodReferenceType(PsiMethodReferenceType methodReferenceType) {
    final PsiMethodReferenceExpression expression = methodReferenceType.getExpression();
    final PsiType interfaceType = expression.getFunctionalInterfaceType();
    if (interfaceType != null && LambdaUtil.isFunctionalType(interfaceType)) return interfaceType.accept(this);
    return visitType(methodReferenceType);
  }
}
