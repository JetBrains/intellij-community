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

import java.util.*;

public class ExtractedParameter {
  @NotNull public final PsiType myType;
  @NotNull public final ExtractableExpressionPart myPattern;
  @NotNull public final ExtractableExpressionPart myCandidate;
  @NotNull public final Set<PsiExpression> myPatternUsages = new HashSet<>();

  public ExtractedParameter(@NotNull ExtractableExpressionPart patternPart,
                            @NotNull ExtractableExpressionPart candidatePart,
                            @NotNull PsiType type) {
    myType = type;
    myPattern = patternPart;
    myCandidate = candidatePart;
    addUsages(patternPart);
  }

  public static boolean match(@NotNull ExtractableExpressionPart patternPart,
                              @NotNull ExtractableExpressionPart candidatePart,
                              @NotNull List<? super ExtractedParameter> parameters) {
    PsiType type = ExtractableExpressionPart.commonType(patternPart, candidatePart);
    if (type == null) {
      return false;
    }
    parameters.add(new ExtractedParameter(patternPart, candidatePart, type));
    return true;
  }

  @NotNull
  public ExtractedParameter copyWithCandidateUsage(@NotNull PsiExpression candidateUsage) {
    ExtractedParameter result = new ExtractedParameter(myPattern, ExtractableExpressionPart.fromUsage(candidateUsage, myType), myType);
    result.myPatternUsages.addAll(myPatternUsages);
    return result;
  }

  @NotNull
  public String getLocalVariableTypeText() {
    PsiType type = GenericsUtil.getVariableTypeByExpressionType(myType);
    return type.getCanonicalText();
  }

  public void addUsages(@NotNull ExtractableExpressionPart patternPart) {
    myPatternUsages.add(patternPart.getUsage());
  }

  public static List<Match> getCompatibleMatches(@NotNull List<Match> matches,
                                                 PsiElement @NotNull [] pattern,
                                                 @NotNull List<PsiElement[]> candidates) {
    List<Match> result = new ArrayList<>();
    Set<PsiExpression> firstUsages = null;
    for (Match match : matches) {
      List<ExtractedParameter> parameters = match.getExtractedParameters();
      PsiElement[] candidateElements = ContainerUtil.find(candidates,
                                                          elements -> elements.length != 0 && match.getMatchStart() == elements[0]);
      Set<PsiVariable> candidateVariables = ContainerUtil.map2SetNotNull(parameters, parameter -> parameter.myCandidate.myVariable);
      if (candidateElements == null || containsModifiedField(candidateElements, candidateVariables)) {
        continue;
      }
      Set<PsiExpression> patternUsages = StreamEx.of(parameters).map(p -> p.myPattern.getUsage()).toSet();
      if (firstUsages == null) {
        Set<PsiVariable> patternVariables = ContainerUtil.map2SetNotNull(parameters, parameter -> parameter.myPattern.myVariable);
        if (containsModifiedField(pattern, patternVariables)) {
          return Collections.emptyList();
        }
        firstUsages = patternUsages;
        result.add(match);
      }
      else if (firstUsages.equals(patternUsages)) {
        result.add(match);
      }
    }
    return result;
  }

  private static boolean containsModifiedField(PsiElement @NotNull [] elements, @NotNull Set<PsiVariable> variables) {
    Set<PsiField> fields = StreamEx.of(variables)
      .select(PsiField.class)
      .filter(field -> !field.hasModifierProperty(PsiModifier.FINAL))
      .toSet();

    if (!fields.isEmpty()) {
      FieldModificationVisitor visitor = new FieldModificationVisitor(fields);
      for (PsiElement element : elements) {
        element.accept(visitor);
        if (visitor.myModified) {
          return true;
        }
      }
    }
    return false;
  }

  private static class FieldModificationVisitor extends JavaRecursiveElementWalkingVisitor {
    private final Set<PsiField> myFields;
    private boolean myModified;

    FieldModificationVisitor(Set<PsiField> fields) {
      myFields = fields;
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);

      visitModifiedExpression(expression.getLExpression());
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);

      IElementType op = expression.getOperationTokenType();
      if (op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS) {
        visitModifiedExpression(expression.getOperand());
      }
    }

    @Override
    public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
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

  @Override
  public String toString() {
    return myPattern + " -> " + myCandidate + " [" + myPatternUsages.size() + "] : " + myType.getPresentableText();
  }
}
