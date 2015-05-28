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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

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
    final MethodSignature signature = interfaceMethod.getSignature(substitutor);
    final PsiParameter[] targetParameters = interfaceMethod.getParameterList().getParameters();
    final PsiType interfaceMethodReturnType = interfaceMethod.getReturnType();
    final PsiType returnType = substitutor.substitute(interfaceMethodReturnType);
    final PsiType[] typeParameters = myExpression.getTypeParameters();

    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(myExpression);

    if (myExpression.isExact()) {
      final PsiMember applicableMember = myExpression.getPotentiallyApplicableMember();
      LOG.assertTrue(applicableMember != null);

      final PsiClass applicableMemberContainingClass = applicableMember.getContainingClass();
      final PsiClass containingClass = qualifierResolveResult.getContainingClass();

      PsiSubstitutor psiSubstitutor = getSubstitutor(signature, qualifierResolveResult, applicableMember, applicableMemberContainingClass);

      PsiType applicableMethodReturnType = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getReturnType() : null;
      int idx = 0;
      for (PsiTypeParameter param : ((PsiTypeParameterListOwner)applicableMember).getTypeParameters()) {
        if (idx < typeParameters.length) {
          psiSubstitutor = psiSubstitutor.put(param, typeParameters[idx++]);
        }
      }
      final PsiParameter[] parameters = applicableMember instanceof PsiMethod ? ((PsiMethod)applicableMember).getParameterList().getParameters() : PsiParameter.EMPTY_ARRAY;
      if (targetParameters.length == parameters.length + 1) {
        final PsiType qualifierType = PsiMethodReferenceUtil.getQualifierType(myExpression);
        final PsiClass qualifierClass = PsiUtil.resolveClassInType(qualifierType);
        if (qualifierClass != null) {
          session.initBounds(myExpression, qualifierClass.getTypeParameters());
          final PsiType pType = signature.getParameterTypes()[0];
          constraints.add(new StrictSubtypingConstraint(session.substituteWithInferenceVariables(qualifierType), pType));
        }
        for (int i = 1; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(session.substituteWithInferenceVariables(psiSubstitutor.substitute(parameters[i - 1].getType())),
                                                          signature.getParameterTypes()[i]));
        }
      } else if (targetParameters.length == parameters.length) {
        for (int i = 0; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(session.substituteWithInferenceVariables(psiSubstitutor.substitute(parameters[i].getType())),
                                                          signature.getParameterTypes()[i]));
        }
      } else {
        return false;
      }
      if (returnType != PsiType.VOID && returnType != null) {
        if (applicableMethodReturnType == PsiType.VOID) {
          return false;
        }

        if (applicableMethodReturnType != null) {
          constraints.add(new TypeCompatibilityConstraint(returnType,
                                                          session.substituteWithInferenceVariables(psiSubstitutor.substitute(applicableMethodReturnType))));
        }
        else if (applicableMember instanceof PsiClass || applicableMember instanceof PsiMethod && ((PsiMethod)applicableMember).isConstructor()) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(applicableMember.getProject());
          if (containingClass != null) {
            final PsiType classType = session.substituteWithInferenceVariables(elementFactory.createType(containingClass, psiSubstitutor));
            constraints.add(new TypeCompatibilityConstraint(returnType, classType));
          }
        }
      }
      return true;
    }

    //------   non exact method references   --------------------

    for (PsiType paramType : signature.getParameterTypes()) {
      if (!session.isProperType(paramType)) {
        return false;
      }
    }

    final Map<PsiElement, PsiType> map = LambdaUtil.getFunctionalTypeMap();
    final PsiType added = map.put(myExpression, session.startWithFreshVars(groundTargetType));
    final JavaResolveResult resolve;
    try {
      resolve = myExpression.advancedResolve(true);
    }
    finally {
      if (added == null) {
        map.remove(myExpression);
      }
    }
    final PsiElement element = resolve.getElement();
    if (element == null) {
      return false;
    }

    if (PsiType.VOID.equals(returnType) || returnType == null) {
      return true;
    }

    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final PsiType referencedMethodReturnType;
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null, method);
      PsiSubstitutor psiSubstitutor = getSubstitutor(signature, qualifierResolveResult, method, containingClass);

      if (method.isConstructor()) {
        referencedMethodReturnType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, PsiSubstitutor.EMPTY);
      }
      else {
        referencedMethodReturnType = method.getReturnType();
      }
      LOG.assertTrue(referencedMethodReturnType != null, method);

      if (!PsiTreeUtil.isContextAncestor(containingClass, myExpression, false) ||
          PsiUtil.getEnclosingStaticElement(myExpression, containingClass) != null) {
        session.initBounds(myExpression, containingClass.getTypeParameters());
      }

      session.initBounds(myExpression, method.getTypeParameters());

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
          session.collectApplicabilityConstraints(myExpression, ((MethodCandidateInfo)resolve), groundTargetType);
          session.registerReturnTypeConstraints(psiSubstitutor.substitute(referencedMethodReturnType), returnType);
          return true;
        }
      }

      if (PsiType.VOID.equals(referencedMethodReturnType)) {
        return false;
      }
 
      int idx = 0;
      for (PsiTypeParameter param : method.getTypeParameters()) {
        if (idx < typeParameters.length) {
          psiSubstitutor = psiSubstitutor.put(param, typeParameters[idx++]);
        }
      }

      constraints.add(new TypeCompatibilityConstraint(returnType,
                                                      session.substituteWithInferenceVariables(psiSubstitutor.substitute(referencedMethodReturnType))));
    }
    
    return true;
  }

  private PsiSubstitutor getSubstitutor(MethodSignature signature,
                                        PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                        PsiMember member,
                                        @Nullable PsiClass containingClass) {
    final PsiClass qContainingClass = qualifierResolveResult.getContainingClass();
    PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();
    if (qContainingClass != null && containingClass != null) {
      // 15.13.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T;
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if ( PsiUtil.isRawSubstitutor(qContainingClass, psiSubstitutor)) {
        if (member instanceof PsiMethod && PsiMethodReferenceUtil.isSecondSearchPossible(signature.getParameterTypes(), qualifierResolveResult, myExpression)) {
          final PsiType pType = signature.getParameterTypes()[0];
          PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(
            PsiImplUtil.normalizeWildcardTypeByPosition(pType, myExpression));
          PsiClass paramClass = resolveResult.getElement();
          LOG.assertTrue(paramClass != null);
          psiSubstitutor = TypeConversionUtil.getClassSubstitutor(qContainingClass, paramClass, resolveResult.getSubstitutor());
          LOG.assertTrue(psiSubstitutor != null);
        }
        else {
          psiSubstitutor = PsiSubstitutor.EMPTY;
        }
      }

      if (qContainingClass.isInheritor(containingClass, true)) {
        psiSubstitutor = TypeConversionUtil.getClassSubstitutor(containingClass, qContainingClass, psiSubstitutor);
        LOG.assertTrue(psiSubstitutor != null);
      }
    }
    return psiSubstitutor;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
  }

  @Override
  public String toString() {
    return myExpression.getText() + " -> " + myT.getPresentableText();
  }
}
