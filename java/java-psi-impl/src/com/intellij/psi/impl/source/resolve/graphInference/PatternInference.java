// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.graphInference;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
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
   * @param pattern pattern itself, which has no type arguments specified
   * @param recordClass record class of deconstruction pattern
   * @param type context type; type of the expression, which is matched against the pattern
   * @return updated {@link CandidateInfo} that contains a substitutor with inferred type arguments
   */
  public static @NotNull CandidateInfo inferPatternGenerics(@NotNull CandidateInfo resolveResult,
                                                            @NotNull PsiPattern pattern,
                                                            @NotNull PsiClass recordClass,
                                                            @Nullable PsiType type) {
    InferenceSession session = createSession(pattern, recordClass, type);
    if (session == null) return resolveResult;
    PsiSubstitutor substitutor = session.infer();
    return new PatternCandidateInfo(resolveResult, substitutor, ContainerUtil.getFirstItem(session.getIncompatibleErrorMessages()));
  }

  /**
   * Infers the type arguments of a pattern class from the type of the expression that the pattern is matched against.
   * Unlike {@link #inferPatternGenerics}, this method needs no resolve result, so a caller that only wants the type
   * arguments can use it. The inference keeps the nullability of the context type.
   *
   * @param pattern      pattern to infer the type arguments for
   * @param patternClass class of the pattern type
   * @param type         context type; type of the expression, which is matched against the pattern
   * @return the inferred substitutor, or {@link PsiSubstitutor#EMPTY} if the inference is not possible
   */
  public static @NotNull PsiSubstitutor inferPatternSubstitutor(@NotNull PsiPattern pattern,
                                                                @NotNull PsiClass patternClass,
                                                                @Nullable PsiType type) {
    InferenceSession session = createSession(pattern, patternClass, type);
    return session == null ? PsiSubstitutor.EMPTY : session.infer();
  }

  private static @Nullable InferenceSession createSession(@NotNull PsiPattern pattern,
                                                          @NotNull PsiClass recordClass,
                                                          @Nullable PsiType type) {
    // JLS 18.5.5
    if (type == null) return null;
    type = JavaVarTypeUtil.getUpwardProjection(type);
    Project project = recordClass.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiClassType recordRawType = factory.createType(recordClass);
    if (!recordRawType.isConvertibleFrom(type) || JavaGenericsUtil.isUncheckedCast(recordRawType, type)) {
      return null;
    }
    PsiTypeParameter[] parameters = recordClass.getTypeParameters();
    InferenceSession session = new InferenceSession(parameters, PsiSubstitutor.EMPTY, PsiManagerEx.getInstanceEx(project), pattern);
    return addConstraints(recordClass, type, factory, session) ? session : null;
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
      if (arguments.length == 0 && parameters.length != 0) {
        //18.5.5
        //An initial bound set, B0, is generated from the declared bounds of P1, ..., Pn, as described in §18.1.3.
        //remark: usually, it is covered by `arguments` (see later), but in this case, a set of arguments is empty
        PsiManager manager = recordClass.getManager();
        for (PsiTypeParameter parameter : parameters) {
          PsiReferenceList extendsList = parameter.getExtendsList();
          for (PsiClassType referencedType : extendsList.getReferencedTypes()) {
            wildcardTypeParams.add(parameter);
            wildcardTypes.add(PsiWildcardType.createExtends(manager, referencedType));
          }
        }
      }
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
