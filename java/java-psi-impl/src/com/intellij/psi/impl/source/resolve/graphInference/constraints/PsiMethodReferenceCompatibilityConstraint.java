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
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
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

    final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(myT);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
    if (interfaceMethod == null) {
      return false;
    }

    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, classResolveResult);
    final PsiParameter[] targetParameters = interfaceMethod.getParameterList().getParameters();
    final PsiType returnType = substitutor.substitute(interfaceMethod.getReturnType());
    LOG.assertTrue(returnType != null, interfaceMethod);
    if (!myExpression.isExact()) {
      for (PsiParameter parameter : targetParameters) {
        if (!session.isProperType(substitutor.substitute(parameter.getType()))) {
          return false;
        }
      }
    } else {
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(myExpression);
      final PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();
      final PsiMember applicableMember = ((PsiMethodReferenceExpressionImpl)myExpression).getPotentiallyApplicableMember();
      LOG.assertTrue(applicableMember != null);
      final PsiParameter[] parameters = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getParameterList().getParameters() : PsiParameter.EMPTY_ARRAY;
      if (targetParameters.length == parameters.length + 1) {
        final PsiTypeElement qualifierTypeElement = myExpression.getQualifierType();
        final PsiExpression qualifierExpression = myExpression.getQualifierExpression();
        PsiType qualifierType;
        if (qualifierTypeElement != null) {
          qualifierType = qualifierTypeElement.getType();
        }
        else {
          LOG.assertTrue(qualifierExpression != null);
          qualifierType = qualifierExpression.getType();
          if (qualifierType == null && qualifierExpression instanceof PsiReferenceExpression) {
            final JavaResolveResult resolveResult = ((PsiReferenceExpression)qualifierExpression).advancedResolve(false);
            final PsiElement resolve = resolveResult.getElement();
            if (resolve instanceof PsiClass) {
              qualifierType = JavaPsiFacade.getElementFactory(resolve.getProject()).createType((PsiClass)resolve, resolveResult.getSubstitutor());
            }
          }
        }
        constraints.add(new SubtypingConstraint(qualifierType, GenericsUtil.eliminateWildcards(substitutor.substitute(targetParameters[0].getType())), true));
        for (int i = 1; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(psiSubstitutor.substitute(parameters[i - 1].getType()), GenericsUtil.eliminateWildcards(substitutor.substitute(targetParameters[i].getType()))));
        }
      } else {
        for (int i = 0; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(psiSubstitutor.substitute(parameters[i].getType()), GenericsUtil.eliminateWildcards(substitutor.substitute(targetParameters[i].getType()))));
        }
      }
      if (returnType != PsiType.VOID) {
        final PsiType applicableMethodReturnType = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getReturnType() : null;
        if (applicableMethodReturnType == PsiType.VOID) {
          return false;
        }

        if (applicableMethodReturnType != null) {
          constraints.add(new TypeCompatibilityConstraint(GenericsUtil.eliminateWildcards(returnType), psiSubstitutor.substitute(applicableMethodReturnType)));
        } else if (applicableMember instanceof PsiClass || applicableMember instanceof PsiMethod && ((PsiMethod)applicableMember).isConstructor()) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(applicableMember.getProject());
          final PsiClassType classType = elementFactory.createType(qualifierResolveResult.getContainingClass(), PsiSubstitutor.EMPTY);
          constraints.add(new TypeCompatibilityConstraint(GenericsUtil.eliminateWildcards(returnType), psiSubstitutor.substitute(classType)));
        }
      }
      return true;
    }

    final PsiElement resolve = myExpression.resolve();
    if (resolve == null) {
      return false;
    }

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
 
      constraints.add(new TypeCompatibilityConstraint(returnType, referencedMethodReturnType));
    }
    
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
  }
}
