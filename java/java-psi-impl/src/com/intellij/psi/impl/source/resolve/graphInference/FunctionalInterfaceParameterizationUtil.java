// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

public final class FunctionalInterfaceParameterizationUtil {
  private static final Logger LOG = Logger.getInstance(FunctionalInterfaceParameterizationUtil.class);

  public static boolean isWildcardParameterized(@Nullable PsiType classType) {
    classType = LambdaUtil.normalizeFunctionalType(classType);
    if (classType == null) return false;
    if (classType instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)classType).resolveGenerics();
      final PsiClass aClass = result.getElement();
      if (aClass != null) {
        final PsiSubstitutor substitutor = result.getSubstitutor();
        for (PsiTypeParameter parameter : aClass.getTypeParameters()) {
          if (substitutor.substitute(parameter) instanceof PsiWildcardType) {
            return true;
          }
        }
      }
      return false;
    }
    return false;
  }

  @Nullable
  public static PsiType getGroundTargetType(@Nullable PsiType psiClassType) {
    return getGroundTargetType(psiClassType, null);
  }

  @Nullable
  public static PsiType getGroundTargetType(@Nullable PsiType psiClassType, @Nullable PsiLambdaExpression expr) {
    return getGroundTargetType(psiClassType, expr, true);
  }

  @Nullable
  public static PsiType getGroundTargetType(@Nullable PsiType psiClassType, @Nullable PsiLambdaExpression expr, boolean performFinalCheck) {
    psiClassType = LambdaUtil.normalizeFunctionalType(psiClassType);
    if (!isWildcardParameterized(psiClassType)) {
      return psiClassType;
    }

    if (expr != null && expr.hasFormalParameterTypes()) return getFunctionalTypeExplicit(psiClassType, expr, performFinalCheck);

    return psiClassType instanceof PsiClassType ? getNonWildcardParameterization((PsiClassType)psiClassType) : null;
  }

  /**
   * 18.5.3. Functional Interface Parameterization Inference
   */
  private static PsiType getFunctionalTypeExplicit(PsiType psiClassType, PsiLambdaExpression expr, boolean performFinalCheck) {
    final PsiParameter[] lambdaParams = expr.getParameterList().getParameters();
    LOG.assertTrue(psiClassType instanceof PsiClassType, "Unexpected type: " + psiClassType);
    final PsiType[] parameters = ((PsiClassType)psiClassType).getParameters();
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)psiClassType).resolveGenerics();
    PsiClass psiClass = resolveResult.getElement();

    if (psiClass != null) {

      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod == null) return null;
      final PsiClass samClass = interfaceMethod.getContainingClass();
      if (samClass == null) return null;

      PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      if (typeParameters.length != parameters.length) {
        return null;
      }
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      final PsiParameter[] targetMethodParams = interfaceMethod.getParameterList().getParameters();
      if (targetMethodParams.length != lambdaParams.length) {
        return null;
      }

      final PsiSubstitutor lambdaSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(samClass, psiClass, PsiSubstitutor.EMPTY);

      final InferenceSession session = new InferenceSession(typeParameters, PsiSubstitutor.EMPTY, expr.getManager(), expr);

      for (int i = 0; i < targetMethodParams.length; i++) {
        final PsiType qType = lambdaSubstitutor.substitute(targetMethodParams[i].getType());
        session.addConstraint(new TypeEqualityConstraint(lambdaParams[i].getType(), session.substituteWithInferenceVariables(qType)));
      }

      if (!session.repeatInferencePhases()) {
        return null;
      }

      final PsiSubstitutor substitutor = session.getInstantiations(session.getInferenceVariables());
      final PsiType[] newTypeParameters = new PsiType[parameters.length];
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        if (substitutor.getSubstitutionMap().containsKey(typeParameter)) {
          newTypeParameters[i] = substitutor.substitute(typeParameter);
        } else {
          newTypeParameters[i] = parameters[i];
        }
      }

      final PsiClassType parameterization = elementFactory.createType(psiClass, newTypeParameters);

      //If F<A'1, ..., A'm> is not a well-formed type (that is, the type arguments are not within their bounds),
      // or if F<A'1, ..., A'm> is not a subtype of F<A1, ..., Am>, no valid parameterization exists.
      if (!isWellFormed(psiClass, typeParameters, newTypeParameters) || performFinalCheck && !psiClassType.isAssignableFrom(parameterization)) {
        return null;
      }

      //Otherwise, the inferred parameterization is either F<A'1, ..., A'm>, if all the type arguments are types,
      if (!isWildcardParameterized(parameterization)) {
        return parameterization;
      }

      //or the non-wildcard parameterization (p9.8) of F<A'1, ..., A'm>, if one or more type arguments are still wildcards.
      return getNonWildcardParameterization(parameterization);
    }
    return null;
  }

  private static boolean isWellFormed(PsiClass psiClass, PsiTypeParameter[] typeParameters, PsiType[] newTypeParameters) {
    final PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.putAll(psiClass, newTypeParameters);
    for (int i = 0; i < typeParameters.length; i++) {
      for (PsiClassType bound : typeParameters[i].getExtendsListTypes()) {
        if (GenericsUtil.checkNotInBounds(newTypeParameters[i], substitutor.substitute(bound), false)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
     The function type of a parameterized functional interface, F<A1...An>, where one or more of A1...An is a wildcard, is the function type of the non-wildcard parameterization of F, F<T1...Tn> determined as follows.
     Let P1, ..., Pn be the type parameters of F and B1, ..., Bn be the corresponding bounds. For all i, 1 <= i <= n, Ti is derived according to the form of Ai:

     If Ai is a type, then Ti = Ai.
     If Ai is a wildcard, and the corresponding type parameter bound, Bi, mentions one of P1...Pn, then Ti is undefined and there is no function type.
     Otherwise:
     If Ai is an unbound wildcard ?, then Ti = Bi.
     If Ai is a upper-bounded wildcard ? extends Ui, then Ti = glb(Ui, Bi).
     If Ai is a lower-bounded wildcard ? super Li, then Ti = Li.
   */
  @Nullable
  public static PsiType getNonWildcardParameterization(PsiClassType psiClassType) {
    final PsiClassType.ClassResolveResult result = psiClassType.resolveGenerics();
    final PsiClass psiClass = result.getElement();
    if (psiClass != null) {
      final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
      final PsiType[] newParameters = new PsiType[typeParameters.length];

      final PsiSubstitutor substitutor = result.getSubstitutor();
      final HashSet<PsiTypeParameter> typeParametersSet = ContainerUtil.newHashSet(typeParameters);
      next: for (int i = 0; i < typeParameters.length; i++) {
        PsiType paramType = substitutor.substitute(typeParameters[i]);
        if (paramType instanceof PsiWildcardType) {
          final PsiType bound = ((PsiWildcardType)paramType).getBound();
          for (PsiClassType paramBound : typeParameters[i].getExtendsListTypes()) {
            if (PsiTypesUtil.mentionsTypeParameters(paramBound, typeParametersSet)) {
              if (bound == null) {
                return null;
              }
              newParameters[i] = bound;
              continue next;
            }
          }

          if (((PsiWildcardType)paramType).isSuper()) {
            newParameters[i] = bound;
          }
          else {
            newParameters[i] = bound != null ? bound : PsiType.getJavaLangObject(psiClass.getManager(), psiClassType.getResolveScope());
            for (PsiClassType paramBound : typeParameters[i].getExtendsListTypes()) {
              newParameters[i] = GenericsUtil.getGreatestLowerBound(newParameters[i], paramBound);
            }
          }
        } else {
          newParameters[i] = paramType;
        }
      }

      if (!isWellFormed(psiClass, typeParameters, newParameters)) {
        return null;
      }

      final PsiClassType parameterization = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass, newParameters);
      if (!psiClassType.isAssignableFrom(parameterization)) {
        return null;
      }
      return parameterization;
    }
    return null;
  }
}
