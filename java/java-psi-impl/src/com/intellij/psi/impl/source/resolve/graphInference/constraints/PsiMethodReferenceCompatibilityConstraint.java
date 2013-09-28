/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * User: anna
 */
public class PsiMethodReferenceCompatibilityConstraint implements ConstraintFormula {
  private static final Logger LOG = Logger.getInstance("#" + PsiMethodReferenceCompatibilityConstraint.class.getName());
  private final PsiMethodReferenceExpression myExpression;
  private PsiType myT;

  public PsiMethodReferenceCompatibilityConstraint(PsiMethodReferenceExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (LambdaHighlightingUtil.checkInterfaceFunctional(myT) != null) {
      return false;
    }

    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(myT);
    if (interfaceMethod == null) {
      return false;
    }
    
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, PsiUtil.resolveGenericsClassInType(myT));
    if (!myExpression.isExact()) {
      final PsiParameter[] parameters = interfaceMethod.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (!session.isProperType(substitutor.substitute(parameter.getType()))) {
          return false;
        }
      }
    }

    final PsiElement resolve = myExpression.resolve();
    if (resolve == null) {
      return false;
    }

    final PsiType returnType = interfaceMethod.getReturnType();
    LOG.assertTrue(returnType != null, interfaceMethod);
    if (PsiType.VOID.equals(returnType)) {
      return true;
    }

    if (resolve instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolve;
      final PsiType referencedMethodReturnType;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        LOG.assertTrue(containingClass != null, method);
        referencedMethodReturnType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass);
      }
      else {
        referencedMethodReturnType = method.getReturnType();
      }
      LOG.assertTrue(referencedMethodReturnType != null, method);

      if (myExpression.getTypeParameters().length == 0 &&
          ((PsiMethod)resolve).getTypeParameters().length > 0 && 
          PsiPolyExpressionUtil.mentionsTypeParameters(returnType, new HashSet<PsiTypeParameter>(Arrays.asList(interfaceMethod.getTypeParameters())))) {
        //todo target type constraint
        return true;
      }

      if (PsiType.VOID.equals(referencedMethodReturnType)) {
        return false;
      }
 
      constraints.add(new TypeCompatibilityConstraint(substitutor.substitute(returnType), referencedMethodReturnType));
    }
    
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
  }
}
