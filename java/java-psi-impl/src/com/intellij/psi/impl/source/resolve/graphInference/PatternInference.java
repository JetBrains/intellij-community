// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.JavaVarTypeUtil;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.PatternCandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for pattern inference
 */
public final class PatternInference {
  /**
   * @param resolveResult result of deconstruction pattern type element resolve before the inference
   * @param pattern deconstruction pattern itself, which has no type arguments specified 
   * @param recordClass record class of deconstruction pattern
   * @param type context type; type of the expression, which is matched against the pattern
   * @return updated {@link CandidateInfo} that contains a substitutor with inferred type arguments
   */
  public static @NotNull CandidateInfo inferPatternGenerics(@NotNull CandidateInfo resolveResult,
                                                            @NotNull PsiDeconstructionPattern pattern,
                                                            @NotNull PsiClass recordClass,
                                                            @Nullable PsiType type) {
    // JLS 18.5.5
    if (type == null) return resolveResult;
    type = JavaVarTypeUtil.getUpwardProjection(type);
    Project project = recordClass.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiClassType recordRawType = factory.createType(recordClass);
    if (!recordRawType.isConvertibleFrom(type) || JavaGenericsUtil.isUncheckedCast(recordRawType, type)) {
      return resolveResult;
    }
    PsiTypeParameter[] parameters = recordClass.getTypeParameters();
    InferenceSession session = new InferenceSession(parameters, PsiSubstitutor.EMPTY, PsiManagerEx.getInstanceEx(project), pattern);
    if (!addConstraints(recordClass, type, factory, session)) {
      return resolveResult;
    }
    PsiSubstitutor substitutor = session.infer();
    return new PatternCandidateInfo(resolveResult, substitutor, ContainerUtil.getFirstItem(session.getIncompatibleErrorMessages()));
  }

  private static boolean addConstraints(@NotNull PsiClass recordClass,
                                        @NotNull PsiType type,
                                        PsiElementFactory factory,
                                        InferenceSession session) {
    if (type instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)type;
      PsiClassType.ClassResolveResult result = classType.resolveGenerics();
      PsiClass gClass = result.getElement();
      if (gClass == null) return false;
      List<PsiTypeParameter> wildcardTypeParams = new ArrayList<>();
      List<PsiWildcardType> wildcardTypes = new ArrayList<>();
      PsiType[] arguments = classType.getParameters();
      PsiTypeParameter[] parameters = gClass.getTypeParameters();
      for (int i = 0; i < arguments.length; i++) {
        PsiType argument = arguments[i];
        if (argument instanceof PsiWildcardType && i < parameters.length) {
          wildcardTypeParams.add(parameters[i]);
          wildcardTypes.add((PsiWildcardType)argument);
        }
      }
      if (!wildcardTypeParams.isEmpty()) {
        InferenceVariable[] variables =
          session.initOrReuseVariables(session.getContext(), wildcardTypeParams.toArray(PsiTypeParameter.EMPTY_ARRAY));
        PsiSubstitutor newSubstitutor = result.getSubstitutor();
        for (int i = 0; i < variables.length; i++) {
          PsiWildcardType wildcardType = wildcardTypes.get(i);
          if (wildcardType.isExtends()) {
            variables[i].addBound(wildcardType.getExtendsBound(), InferenceBound.UPPER, null);
          }
          else if (wildcardType.isSuper()) {
            variables[i].addBound(wildcardType.getExtendsBound(), InferenceBound.LOWER, null);
          }
          newSubstitutor = newSubstitutor.put(wildcardTypeParams.get(i), factory.createType(variables[i]));
        }
        type = factory.createType(gClass, newSubstitutor);
      }
      if (gClass instanceof PsiTypeParameter) {
        for (PsiClassType upperBound : gClass.getExtendsListTypes()) {
          if (!addConstraints(recordClass, upperBound, factory, session)) return false;
        }
      }
      else if (InheritanceUtil.isInheritorOrSelf(recordClass, gClass, true)) {
        PsiClassType rAType = factory.createType(recordClass, session.getInferenceSubstitution());
        PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(gClass, rAType);
        PsiType recordPrimeType = factory.createType(gClass, superClassSubstitutor);
        session.addConstraint(new TypeEqualityConstraint(type, recordPrimeType));
      }
    }
    if (type instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType)type).getConjuncts()) {
        if (!addConstraints(recordClass, conjunct, factory, session)) return false;
      }
    }
    return true;
  }
}
