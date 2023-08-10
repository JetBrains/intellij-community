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

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiMethodReferenceCompatibilityConstraint implements ConstraintFormula {
  private static final Logger LOG = Logger.getInstance(PsiMethodReferenceCompatibilityConstraint.class);
  private final PsiMethodReferenceExpression myExpression;
  private PsiType myT;

  public PsiMethodReferenceCompatibilityConstraint(PsiMethodReferenceExpression expression, PsiType t) {
    myExpression = expression;
    myT = t;
  }

  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    if (!LambdaUtil.isFunctionalType(myT)) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.not.a.functional.interface", session.getPresentableText(myT)));
      return false;
    }

    final PsiType groundTargetType = myExpression.getGroundTargetType(myT);
    final PsiClassType.ClassResolveResult classResolveResult = PsiUtil.resolveGenericsClassInType(groundTargetType);
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(classResolveResult);
    if (interfaceMethod == null) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.no.valid.function.type.found", session.getPresentableText(myT)));
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

      PsiSubstitutor psiSubstitutor = getSubstitutor(signature, qualifierResolveResult, applicableMember, applicableMemberContainingClass, myExpression);

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
          final PsiType pType = signature.getParameterTypes()[0];
          constraints.add(new StrictSubtypingConstraint(session.substituteWithInferenceVariables(qualifierType), pType));
        }
        for (int i = 1; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(session.substituteWithInferenceVariables(psiSubstitutor.substitute(parameters[i - 1].getType())),
                                                          PsiUtil.captureToplevelWildcards(signature.getParameterTypes()[i], myExpression)));
        }
      }
      else if (targetParameters.length == parameters.length) {
        for (int i = 0; i < targetParameters.length; i++) {
          constraints.add(new TypeCompatibilityConstraint(session.substituteWithInferenceVariables(psiSubstitutor.substitute(parameters[i].getType())),
                                                          PsiUtil.captureToplevelWildcards(signature.getParameterTypes()[i], myExpression)));
        }
      }
      else {
        session.registerIncompatibleErrorMessage(
          JavaPsiBundle.message("error.incompatible.type.incompatible.parameter.types.in.method.reference"));
        return false;
      }
      if (!PsiTypes.voidType().equals(returnType) && returnType != null) {
        PsiType applicableMethodReturnType = null;
        if (applicableMember instanceof PsiMethod) {
          final PsiType getClassReturnType = PsiTypesUtil.patchMethodGetClassReturnType(myExpression, (PsiMethod)applicableMember);
          applicableMethodReturnType = getClassReturnType != null ? getClassReturnType : ((PsiMethod)applicableMember).getReturnType();
        }

        if (PsiTypes.voidType().equals(applicableMethodReturnType)) {
          session.registerIncompatibleErrorMessage(
            JavaPsiBundle.message("error.incompatible.type.incompatible.types.expected.not.void.got.void.method.reference"));
          return false;
        }

        if (applicableMethodReturnType == null && 
            (applicableMember instanceof PsiClass || applicableMember instanceof PsiMethod && ((PsiMethod)applicableMember).isConstructor())) {
          if (containingClass != null) {
            applicableMethodReturnType = JavaPsiFacade.getElementFactory(applicableMember.getProject()).createType(containingClass, PsiSubstitutor.EMPTY);
          } 
        }

        if (applicableMethodReturnType != null) {
          applicableMethodReturnType = psiSubstitutor.substitute(applicableMethodReturnType);
          applicableMethodReturnType = Registry.is("unsound.capture.conversion.java.spec.change") ? applicableMethodReturnType
                                                                                                  : PsiUtil.captureToplevelWildcards(applicableMethodReturnType, myExpression);
          constraints.add(new TypeCompatibilityConstraint(returnType, session.substituteWithInferenceVariables(applicableMethodReturnType)));
        }
      }
      return true;
    }

    //------   non exact method references   --------------------

    for (PsiType paramType : signature.getParameterTypes()) {
      if (!session.isProperType(paramType)) {
        //session.registerIncompatibleErrorMessage("Parameter type in not yet inferred: " + session.getPresentableText(paramType));
        return false;
      }
    }

    JavaResolveResult resolve = myExpression.advancedResolve(false);
    final PsiElement element = resolve.getElement();
    if (element == null || resolve instanceof MethodCandidateInfo && !((MethodCandidateInfo)resolve).isApplicable()) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.declaration.for.the.method.reference.not.found"));
      return false;
    }

    if (PsiTypes.voidType().equals(returnType) || returnType == null) {
      return true;
    }

    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      PsiType referencedMethodReturnType;
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null, method);
      PsiSubstitutor psiSubstitutor = getSubstitutor(signature, qualifierResolveResult, method, containingClass, myExpression);

      if (method.isConstructor()) {
        referencedMethodReturnType = JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass, PsiSubstitutor.EMPTY);
      }
      else {
        final PsiType getClassReturnType = PsiTypesUtil.patchMethodGetClassReturnType(myExpression, method);
        referencedMethodReturnType = getClassReturnType != null ? getClassReturnType : method.getReturnType();
      }
      LOG.assertTrue(referencedMethodReturnType != null, method);

      //if i) the method reference elides NonWildTypeArguments, 
      //  ii) the compile-time declaration is a generic method, and 
      // iii) the return type of the compile-time declaration mentions at least one of the method's type parameters;
      if (typeParameters.length == 0) {
        PsiTypeParameter[] methodTypeParameters = method.isConstructor() ? containingClass.getTypeParameters() : method.getTypeParameters();
        if (methodTypeParameters.length > 0) {
          final PsiClass interfaceClass = classResolveResult.getElement();
          LOG.assertTrue(interfaceClass != null);
          if (PsiTypesUtil.mentionsTypeParameters(referencedMethodReturnType,
                                                  ContainerUtil.newHashSet(methodTypeParameters))) {
            session.initBounds(myExpression, psiSubstitutor, methodTypeParameters);
            //the constraint reduces to the bound set B3 which would be used to determine the method reference's invocation type 
            //when targeting the return type of the function type, as defined in 18.5.2.
            session.collectApplicabilityConstraints(myExpression, ((MethodCandidateInfo)resolve), groundTargetType);
            session.registerReturnTypeConstraints(psiSubstitutor.substitute(referencedMethodReturnType), returnType, myExpression);
            return true;
          }
        }
      }

      if (PsiTypes.voidType().equals(referencedMethodReturnType)) {
        session.registerIncompatibleErrorMessage(
          JavaPsiBundle.message("error.incompatible.type.expected.non.void.got.void.method.reference"));
        return false;
      }
 
      int idx = 0;
      for (PsiTypeParameter param : method.getTypeParameters()) {
        if (idx < typeParameters.length) {
          psiSubstitutor = psiSubstitutor.put(param, typeParameters[idx++]);
        }
      }

      PsiClass qContainingClass = qualifierResolveResult.getContainingClass();
      if (qContainingClass != null && PsiUtil.isRawSubstitutor(qContainingClass, qualifierResolveResult.getSubstitutor())) {
        //15.13.1 If there exist a parameterization, then it would be used to search, the *raw type* would be used otherwise
        if (getParameterization(signature, qualifierResolveResult, method, myExpression, qContainingClass) == null) {
          if (!PsiMethodReferenceUtil.isSecondSearchPossible(signature.getParameterTypes(), qualifierResolveResult, myExpression)) {
            session.initBounds(myExpression, qContainingClass.getTypeParameters());
          }
          else {
            referencedMethodReturnType = TypeConversionUtil.erasure(referencedMethodReturnType);
          }
        }
      }

      referencedMethodReturnType = psiSubstitutor.substitute(referencedMethodReturnType);
      referencedMethodReturnType = Registry.is("unsound.capture.conversion.java.spec.change") ? referencedMethodReturnType
                                                                                              : PsiUtil.captureToplevelWildcards(referencedMethodReturnType, myExpression);
      constraints.add(new TypeCompatibilityConstraint(returnType, session.substituteWithInferenceVariables(referencedMethodReturnType)));
    }
    
    return true;
  }

  public static PsiSubstitutor getSubstitutor(MethodSignature signature,
                                              PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                              PsiMember member,
                                              @Nullable PsiClass containingClass,
                                              final PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiClass qContainingClass = qualifierResolveResult.getContainingClass();
    PsiSubstitutor psiSubstitutor = qualifierResolveResult.getSubstitutor();
    if (qContainingClass != null && containingClass != null) {
      // 15.13.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
      // the type to search is the result of capture conversion (5.1.10) applied to T;
      // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
      if (PsiUtil.isRawSubstitutor(qContainingClass, psiSubstitutor)) {
        PsiClassType parameterization = getParameterization(signature, qualifierResolveResult, member, methodReferenceExpression, qContainingClass);
        if (parameterization != null) {
          final PsiType pType = PsiUtil.captureToplevelWildcards(parameterization, methodReferenceExpression);
          psiSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(qContainingClass, (PsiClassType)pType);
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

  private static PsiClassType getParameterization(MethodSignature signature,
                                                  PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                                  PsiMember member,
                                                  PsiMethodReferenceExpression methodReferenceExpression,
                                                  PsiClass qContainingClass) {
    if (member instanceof PsiMethod &&
        PsiMethodReferenceUtil.isSecondSearchPossible(signature.getParameterTypes(), qualifierResolveResult, methodReferenceExpression)) {
      PsiClassType subclassType = StrictSubtypingConstraint.getSubclassType(qContainingClass, signature.getParameterTypes()[0], true);
      if (subclassType != null && !subclassType.isRaw()) {
        return subclassType;
      }
    }
    return null;
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
