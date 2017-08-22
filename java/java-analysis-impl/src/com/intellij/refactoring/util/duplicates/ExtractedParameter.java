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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class ExtractedParameter {
  @NotNull public final List<PsiReferenceExpression> myPatternUsages = new ArrayList<>();
  @NotNull public final PsiVariable myPatternVariable;
  @NotNull public final PsiReferenceExpression myCandidateUsage;
  @NotNull public final PsiVariable myCandidateVariable;
  @NotNull public final PsiType myType;

  ExtractedParameter(@NotNull PsiVariable patternVariable,
                     @NotNull PsiReferenceExpression patternUsage,
                     @NotNull PsiVariable candidateVariable,
                     @NotNull PsiReferenceExpression candidateUsage,
                     @NotNull PsiType type) {
    myPatternVariable = patternVariable;
    myPatternUsages.add(patternUsage);
    myCandidateVariable = candidateVariable;
    myCandidateUsage = candidateUsage;
    myType = type;
  }

  public static boolean match(PsiElement pattern, PsiElement candidate, @NotNull List<ExtractedParameter> parameters) {
    if (pattern instanceof PsiReferenceExpression && candidate instanceof PsiReferenceExpression) {
      PsiReferenceExpression patternUsage = (PsiReferenceExpression)pattern;
      PsiReferenceExpression candidateUsage = (PsiReferenceExpression)candidate;
      PsiElement resolvedPattern = patternUsage.resolve();
      PsiElement resolvedCandidate = candidateUsage.resolve();
      if (resolvedPattern instanceof PsiVariable && resolvedCandidate instanceof PsiVariable) {
        PsiVariable patternVariable = (PsiVariable)resolvedPattern;
        PsiVariable candidateVariable = (PsiVariable)resolvedCandidate;
        if (isStaticOrLocal(patternVariable) && isStaticOrLocal(candidateVariable)) {

          for (ExtractedParameter parameter : parameters) {
            boolean samePattern = resolvedPattern.equals(parameter.myPatternVariable);
            boolean sameCandidate = resolvedCandidate.equals(parameter.myCandidateVariable);
            if (samePattern && sameCandidate) {
              parameter.myPatternUsages.add(patternUsage);
              return true;
            }
            if (samePattern || sameCandidate) {
              return false;
            }
          }
          PsiType type = getParameterType(patternVariable, candidateVariable);
          if (type != null) {
            parameters.add(new ExtractedParameter(patternVariable, patternUsage, candidateVariable, candidateUsage, type));
            return true;
          }
        }
      }
    }

    return false;
  }

  public static List<Match> getCompatibleMatches(List<Match> matches, PsiElement[] pattern) {
    Set<PsiVariable> patternVariables = null;
    List<Match> result = new ArrayList<>();
    for (Match match : matches) {
      List<ExtractedParameter> parameters = match.getExtractedParameters();
      if (patternVariables == null) {
        patternVariables = getPatternVariables(parameters);
        if (containsModifiedField(pattern, patternVariables)) {
          return Collections.emptyList();
        }
        result.add(match);
      }
      else if (patternVariables.equals(getPatternVariables(parameters))) {
        result.add(match);
      }
    }
    return result;
  }

  private static boolean containsModifiedField(PsiElement[] pattern, Set<PsiVariable> variables) {
    Set<PsiField> fields = StreamEx.of(variables)
      .select(PsiField.class)
      .filter(field -> !field.hasModifierProperty(PsiModifier.FINAL))
      .toSet();

    if (!fields.isEmpty()) {
      FieldModificationVisitor visitor = new FieldModificationVisitor(fields);
      for (PsiElement element : pattern) {
        element.accept(visitor);
        if (visitor.myModified) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  static Set<PsiVariable> getPatternVariables(@Nullable List<ExtractedParameter> parameters) {
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

  private static class FieldModificationVisitor extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> myFields;
    private boolean myModified;

    public FieldModificationVisitor(Set<PsiField> fields) {
      myFields = fields;
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);

      visitModifiedExpression(expression.getLExpression());
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);

      IElementType op = expression.getOperationTokenType();
      if (op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS) {
        visitModifiedExpression(expression.getOperand());
      }
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);

      IElementType op = expression.getOperationTokenType();
      if (op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS) {
        visitModifiedExpression(expression.getOperand());
      }
    }

    private void visitModifiedExpression(PsiExpression modifiedExpression) {
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(modifiedExpression);
      if (expression instanceof PsiReferenceExpression) {
        PsiField field = ObjectUtils.tryCast(((PsiReferenceExpression)expression).resolve(), PsiField.class);
        if (field != null && myFields.contains(field)) {
          myModified = true;
          stopWalking();
        }
      }
    }
  }
}
