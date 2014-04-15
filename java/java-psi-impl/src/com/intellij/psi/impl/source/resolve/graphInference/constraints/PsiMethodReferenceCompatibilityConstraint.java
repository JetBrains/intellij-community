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
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.Map;

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
    if (!LambdaUtil.isFunctionalType(myT)) {
      return false;
    }

    final PsiType groundTargetType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(myT);
    final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(groundTargetType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
    if (interfaceMethod == null) {
      return false;
    }

    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(interfaceMethod, classResolveResult);
    final PsiParameter[] targetParameters = interfaceMethod.getParameterList().getParameters();
    final PsiType interfaceMethodReturnType = interfaceMethod.getReturnType();
    final PsiType returnType = substitutor.substitute(interfaceMethodReturnType);
    final PsiType[] typeParameters = myExpression.getTypeParameters();
    if (!myExpression.isExact()) {
      for (PsiParameter parameter : targetParameters) {
        if (!session.isProperType(substitutor.substitute(parameter.getType()))) {
          return false;
        }
      }
    } else {
      final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(myExpression);
      PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();
      final PsiMember applicableMember = ((PsiMethodReferenceExpressionImpl)myExpression).getPotentiallyApplicableMember();
      LOG.assertTrue(applicableMember != null);
      PsiType applicableMethodReturnType = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getReturnType() : null;
      int idx = 0;
      for (PsiTypeParameter param : ((PsiTypeParameterListOwner)applicableMember).getTypeParameters()) {
        if (idx < typeParameters.length) {
          psiSubstitutor = psiSubstitutor.put(param, typeParameters[idx++]);
        }
      }
      final PsiParameter[] parameters = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getParameterList().getParameters() : PsiParameter.EMPTY_ARRAY;
      if (targetParameters.length == parameters.length + 1) {
        specialCase(session, constraints, substitutor, targetParameters);
        for (int i = 1; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(psiSubstitutor.substitute(parameters[i - 1].getType()), substitutor.substitute(targetParameters[i].getType())));
        }
      } else if (targetParameters.length == parameters.length) {
        for (int i = 0; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(psiSubstitutor.substitute(parameters[i].getType()), substitutor.substitute(targetParameters[i].getType())));
        }
      } else {
        return false;
      }
      if (returnType != PsiType.VOID && returnType != null) {
        if (applicableMethodReturnType == PsiType.VOID) {
          return false;
        }

        if (applicableMethodReturnType != null) {
          constraints.add(new TypeCompatibilityConstraint(returnType, psiSubstitutor.substitute(applicableMethodReturnType)));
        } else if (applicableMember instanceof PsiClass || applicableMember instanceof PsiMethod && ((PsiMethod)applicableMember).isConstructor()) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(applicableMember.getProject());
          final PsiClass containingClass = qualifierResolveResult.getContainingClass();
          if (containingClass != null) {
            final PsiClassType classType = elementFactory.createType(containingClass, psiSubstitutor);
            constraints.add(new TypeCompatibilityConstraint(returnType, classType));
          }
        }
      }
      return true;
    }

    final Map<PsiMethodReferenceExpression, PsiType> map = PsiMethodReferenceUtil.getFunctionalTypeMap();
    final PsiType added = map.put(myExpression, groundTargetType);
    final PsiElement resolve;
    try {
      resolve = myExpression.resolve();
    }
    finally {
      if (added == null) {
        map.remove(myExpression);
      }
    }
    if (resolve == null) {
      return false;
    }

    if (PsiType.VOID.equals(returnType) || returnType == null) {
      return true;
    }

    if (resolve instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolve;
      final PsiType referencedMethodReturnType;
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null, method);
      if (method.isConstructor()) {
        referencedMethodReturnType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, PsiSubstitutor.EMPTY);
      }
      else {
        referencedMethodReturnType = method.getReturnType();
      }
      LOG.assertTrue(referencedMethodReturnType != null, method);

      session.initBounds(method.getTypeParameters());

      if (!PsiTreeUtil.isContextAncestor(containingClass, myExpression, false) || 
          PsiUtil.getEnclosingStaticElement(myExpression, containingClass) != null) {
        session.initBounds(containingClass.getTypeParameters());
      }

      //if i) the method reference elides NonWildTypeArguments, 
      //  ii) the compile-time declaration is a generic method, and 
      // iii) the return type of the compile-time declaration mentions at least one of the method's type parameters;
      if (typeParameters.length == 0 && method.getTypeParameters().length > 0) {
        final PsiClass interfaceClass = classResolveResult.getElement();
        LOG.assertTrue(interfaceClass != null);
        if (PsiPolyExpressionUtil.mentionsTypeParameters(referencedMethodReturnType,
                                                         ContainerUtil.newHashSet(method.getTypeParameters()))) {
          //the constraint reduces to the bound set B3 which would be used to determine the method reference's invocation type 
          //when targeting the return type of the function type, as defined in 18.5.2.
          //as there is no parameters, only constraint for return types is left. Here you are:
          session.registerConstraints(referencedMethodReturnType, returnType);
          return true;
        }
      }

      if (PsiType.VOID.equals(referencedMethodReturnType)) {
        return false;
      }
 
      int idx = 0;
      PsiSubstitutor psiSubstitutor = PsiSubstitutor.EMPTY;
      for (PsiTypeParameter param : method.getTypeParameters()) {
        if (idx < typeParameters.length) {
          psiSubstitutor = psiSubstitutor.put(param, typeParameters[idx++]);
        }
      }

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (targetParameters.length == parameters.length + 1 && !method.isVarArgs() && 
          PsiPolyExpressionUtil.mentionsTypeParameters(referencedMethodReturnType, ContainerUtil.newHashSet(containingClass.getTypeParameters()))) { //todo specification bug?
        specialCase(session, constraints, substitutor, targetParameters);
      }
      constraints.add(new TypeCompatibilityConstraint(returnType, psiSubstitutor.substitute(referencedMethodReturnType)));
    }
    
    return true;
  }

  private void specialCase(InferenceSession session,
                           List<ConstraintFormula> constraints,
                           PsiSubstitutor substitutor,
                           PsiParameter[] targetParameters) {
    final PsiTypeElement qualifierTypeElement = myExpression.getQualifierType();
    final PsiExpression qualifierExpression = myExpression.getQualifierExpression();
    PsiType qualifierType;
    if (qualifierTypeElement != null) {
      qualifierType = qualifierTypeElement.getType();
      final PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
      if (qualifierClass != null) {
        qualifierType = JavaPsiFacade.getElementFactory(myExpression.getProject()).createType(qualifierClass, PsiSubstitutor.EMPTY);
      }
    }
    else {
      LOG.assertTrue(qualifierExpression != null);
      qualifierType = qualifierExpression.getType();
      if (qualifierType == null && qualifierExpression instanceof PsiReferenceExpression) {
        final JavaResolveResult resolveResult = ((PsiReferenceExpression)qualifierExpression).advancedResolve(false);
        final PsiElement res = resolveResult.getElement();
        if (res instanceof PsiClass) {
          PsiClass containingClass = (PsiClass)res;
          final boolean isRawSubst = !myExpression.isConstructor() &&
                                     PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor());
          qualifierType = JavaPsiFacade.getElementFactory(res.getProject()).createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : resolveResult.getSubstitutor());
        }
      }
    }

    final PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
    if (qualifierClass != null) {
      session.initBounds(qualifierClass.getTypeParameters());
      constraints.add(new StrictSubtypingConstraint(qualifierType, substitutor.substitute(targetParameters[0].getType())));
    }
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
  }

  @Override
  public String toString() {
    return myExpression.getText() + " -> " + myT.getPresentableText();
  }
}
