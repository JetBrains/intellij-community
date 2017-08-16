/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class AdditionalParameter {
  @NotNull public final List<PsiReferenceExpression> myPatternUsages = new ArrayList<>();
  @NotNull public final PsiVariable myPatternVariable;
  @NotNull public final List<PsiReferenceExpression> myCandidateUsages = new ArrayList<>();
  @NotNull public final PsiVariable myCandidateVariable;
  @NotNull public final PsiType myType;

  AdditionalParameter(@NotNull PsiVariable patternVariable,
                      @NotNull PsiVariable candidateVariable,
                      @NotNull PsiType type) {
    myPatternVariable = patternVariable;
    myCandidateVariable = candidateVariable;
    myType = type;
  }

  void addUsage(@NotNull PsiReferenceExpression patternUsage,
                @NotNull PsiReferenceExpression candidateUsage) {
    myPatternUsages.add(patternUsage);
    myCandidateUsages.add(candidateUsage);
  }

  public static boolean match(PsiElement pattern, PsiElement candidate, @NotNull List<AdditionalParameter> additionalParameters) {
    if (pattern instanceof PsiReferenceExpression && candidate instanceof PsiReferenceExpression) {
      PsiReferenceExpression patternUsage = (PsiReferenceExpression)pattern;
      PsiReferenceExpression candidateUsage = (PsiReferenceExpression)candidate;
      PsiElement resolvedPattern = patternUsage.resolve();
      PsiElement resolvedCandidate = candidateUsage.resolve();
      if (resolvedPattern instanceof PsiVariable && resolvedCandidate instanceof PsiVariable) {
        PsiVariable patternVariable = (PsiVariable)resolvedPattern;
        PsiVariable candidateVariable = (PsiVariable)resolvedCandidate;
        if (isStaticOrLocal(patternVariable) && isStaticOrLocal(candidateVariable)) {

          for (AdditionalParameter additionalParameter : additionalParameters) {
            boolean samePattern = resolvedPattern.equals(additionalParameter.myPatternVariable);
            boolean sameCandidate = resolvedCandidate.equals(additionalParameter.myCandidateVariable);
            if (samePattern && sameCandidate) {
              additionalParameter.addUsage(patternUsage, candidateUsage);
              return true;
            }
            if (samePattern || sameCandidate) {
              return false;
            }
          }
          PsiType type = getParameterType(patternVariable, candidateVariable);
          if (type != null) {
            AdditionalParameter additionalParameter = new AdditionalParameter(patternVariable, candidateVariable, type);
            additionalParameters.add(additionalParameter);
            additionalParameter.addUsage(patternUsage, candidateUsage);
            return true;
          }
        }
      }
    }

    return false;
  }

  public static List<Match> getCompatibleMatches(List<Match> matches) {
    Set<PsiVariable> patternVariables = null;
    List<Match> result = new ArrayList<>();
    for (Match match : matches) {
      List<AdditionalParameter> parameters = match.getAdditionalParameters();
      if (patternVariables == null) {
        patternVariables = getPatternVariables(parameters);
        result.add(match);
      }
      else if (patternVariables.equals(getPatternVariables(parameters))) {
        result.add(match);
      }
    }
    return result;
  }

  @NotNull
  static Set<PsiVariable> getPatternVariables(@Nullable List<AdditionalParameter> parameters) {
    if (parameters != null) {
      return ContainerUtil.map2Set(parameters, parameter -> parameter.myPatternVariable);
    }
    return Collections.emptySet();
  }

  static boolean isStaticOrLocal(@NotNull PsiVariable variable) {
    if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC)) {
      return true;
    }
    return variable instanceof PsiLocalVariable || variable instanceof PsiParameter;
  }

  @Nullable
  static PsiType getParameterType(@NotNull PsiVariable patternVariable, @NotNull PsiVariable candidateVariable) {
    PsiType patternType = patternVariable.getType();
    PsiType candidateType = candidateVariable.getType();
    if (patternType.isAssignableFrom(candidateType)) {
      return patternType;
    }
    if (candidateType.isAssignableFrom(patternType)) {
      return candidateType;
    }
    return null;
  }
}
