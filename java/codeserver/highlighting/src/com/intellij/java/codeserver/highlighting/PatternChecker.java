// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.codeserver.core.JavaPatternExhaustivenessUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

import static com.intellij.psi.PsiModifier.SEALED;
import static com.intellij.util.ObjectUtils.tryCast;

final class PatternChecker {

  private final @NotNull JavaErrorVisitor myVisitor;

  PatternChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkDeconstructionVariable(@NotNull PsiPatternVariable variable) {
    if (variable.getPattern() instanceof PsiDeconstructionPattern) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_VARIABLE.create(variable));
    }
  }

  void checkPatternVariableRequired(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult resultForIncompleteCode) {
    if (!(expression.getParent() instanceof PsiCaseLabelElementList)) return;
    PsiClass resolved = tryCast(resultForIncompleteCode.getElement(), PsiClass.class);
    if (resolved == null) return;
    myVisitor.report(JavaErrorKinds.PATTERN_TYPE_PATTERN_EXPECTED.create(expression, resolved));
  }

  void checkDeconstructionPattern(@NotNull PsiDeconstructionPattern deconstructionPattern) {
    PsiTreeUtil.processElements(deconstructionPattern.getTypeElement(), PsiAnnotation.class, annotation -> {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_ANNOTATION.create(annotation));
      return true;
    });
    PsiElement parent = deconstructionPattern.getParent();
    if (parent instanceof PsiForeachPatternStatement forEach) {
      myVisitor.checkFeature(deconstructionPattern, JavaFeature.RECORD_PATTERNS_IN_FOR_EACH);
      if (myVisitor.hasErrorResults()) return;
      PsiTypeElement typeElement = JavaPsiPatternUtil.getPatternTypeElement(deconstructionPattern);
      if (typeElement == null) return;
      PsiType patternType = typeElement.getType();
      PsiExpression iteratedValue = forEach.getIteratedValue();
      PsiType itemType = iteratedValue == null ? null : JavaGenericsUtil.getCollectionItemType(iteratedValue);
      if (itemType == null) return;
      checkForEachPatternApplicable(deconstructionPattern, patternType, itemType);
      if (myVisitor.hasErrorResults()) return;
      PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(itemType));
      if (selectorClass != null && (selectorClass.hasModifierProperty(SEALED) || selectorClass.isRecord())) {
        if (!JavaPatternExhaustivenessUtil.checkRecordExhaustiveness(Collections.singletonList(deconstructionPattern), patternType, forEach).isExhaustive()) {
          myVisitor.report(JavaErrorKinds.PATTERN_NOT_EXHAUSTIVE.create(
            deconstructionPattern, new JavaErrorKinds.PatternTypeContext(itemType, patternType)));
        }
      }
      else {
        myVisitor.report(JavaErrorKinds.PATTERN_NOT_EXHAUSTIVE.create(
          deconstructionPattern, new JavaErrorKinds.PatternTypeContext(itemType, patternType)));
      }
    }
    else {
      myVisitor.checkFeature(deconstructionPattern, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    }
  }

  boolean checkUncheckedPatternConversion(@NotNull PsiPattern pattern) {
    PsiType patternType = JavaPsiPatternUtil.getPatternType(pattern);
    if (patternType == null) return false;
    if (pattern instanceof PsiDeconstructionPattern subPattern) {
      PsiJavaCodeReferenceElement element = subPattern.getTypeElement().getInnermostComponentReferenceElement();
      if (element != null && element.getTypeParameterCount() == 0 && patternType instanceof PsiClassType classType) {
        patternType = classType.rawType();
      }
    }
    PsiType contextType = JavaPsiPatternUtil.getContextType(pattern);
    if (contextType == null) return false;
    if (contextType instanceof PsiWildcardType wildcardType) {
      contextType = wildcardType.getExtendsBound();
    }
    if (!JavaGenericsUtil.isUncheckedCast(patternType, contextType)) return false;
    myVisitor.report(JavaErrorKinds.PATTERN_UNSAFE_CAST.create(
      pattern, new JavaErrorKinds.PatternTypeContext(contextType, patternType)));
    return true;
  }

  private void checkForEachPatternApplicable(@NotNull PsiDeconstructionPattern pattern,
                                             @NotNull PsiType patternType,
                                             @NotNull PsiType itemType) {
    if (!TypeConversionUtil.areTypesConvertible(itemType, patternType) &&
        (!myVisitor.isIncompleteModel() || !IncompleteModelUtil.isPotentiallyConvertible(patternType, itemType, pattern))) {
      myVisitor.reportIncompatibleType(itemType, patternType, pattern);
      return;
    }
    checkUncheckedPatternConversion(pattern);
    if (myVisitor.hasErrorResults()) return;
    checkDeconstructionErrors(pattern);
  }

  void checkDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    PsiClassType.ClassResolveResult resolveResult =
      recordType instanceof PsiClassType classType ? classType.resolveGenerics() : PsiClassType.ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_REQUIRES_RECORD.create(typeElement));
      return;
    }
    if (resolveResult.getInferenceError() != null) {
      myVisitor.report(JavaErrorKinds.PATTERN_CANNOT_INFER_TYPE.create(typeElement, resolveResult.getInferenceError()));
      return;
    }
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] deconstructionComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean hasMismatchedPattern = false;
    for (int i = 0; i < Math.min(recordComponents.length, deconstructionComponents.length); i++) {
      PsiPattern deconstructionComponent = deconstructionComponents[i];
      PsiType recordComponentType = recordComponents[i].getType();
      PsiType substitutedRecordComponentType = substitutor.substitute(recordComponentType);
      PsiType deconstructionComponentType = JavaPsiPatternUtil.getPatternType(deconstructionComponent);
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(deconstructionPattern);
      if (!isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType, languageLevel)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          if (isApplicableForRecordComponent(substitutedRecordComponentType, deconstructionComponentType,
                                             JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel())) {
            myVisitor.report(JavaErrorKinds.UNSUPPORTED_FEATURE.create(deconstructionComponent, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS));
            continue;
          }
          else if ((substitutedRecordComponentType instanceof PsiPrimitiveType ||
                    deconstructionComponentType instanceof PsiPrimitiveType) &&
                   JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
            myVisitor.report(JavaErrorKinds.CAST_INCONVERTIBLE.create(
              deconstructionComponent, new JavaIncompatibleTypeErrorContext(substitutedRecordComponentType, deconstructionComponentType)));
            continue;
          }

          if (myVisitor.isIncompleteModel() &&
              (IncompleteModelUtil.hasUnresolvedComponent(substitutedRecordComponentType) ||
               IncompleteModelUtil.hasUnresolvedComponent(deconstructionComponentType))) {
            continue;
          }
          myVisitor.reportIncompatibleType(substitutedRecordComponentType, deconstructionComponentType, deconstructionComponent);
        }
      }
      else {
        hasMismatchedPattern |= checkUncheckedPatternConversion(deconstructionComponent);
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern deconstructionComponentPattern) {
        checkDeconstructionErrors(deconstructionComponentPattern);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      myVisitor.report(JavaErrorKinds.PATTERN_DECONSTRUCTION_COUNT_MISMATCH.create(
        deconstructionPattern.getDeconstructionList(), 
        new JavaErrorKinds.DeconstructionCountMismatchContext(deconstructionComponents, recordComponents, hasMismatchedPattern)));
    }
  }

  void checkMalformedDeconstructionPatternInCase(@NotNull PsiDeconstructionPattern pattern) {
    // We are checking the case when the pattern looks similar to method call in switch and want to show user-friendly message that here
    // only constant expressions are expected.
    // it is required to do it in deconstruction list because unresolved reference won't let any parents show any highlighting,
    // so we need element which is not parent
    PsiElement grandParent = pattern.getParent();
    if (!(grandParent instanceof PsiCaseLabelElementList)) return;
    PsiTypeElement typeElement = pattern.getTypeElement();
    PsiJavaCodeReferenceElement ref = PsiTreeUtil.getChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
    if (ref == null) return;
    if (ref.multiResolve(true).length == 0) {
      PsiElementFactory elementFactory = myVisitor.factory();
      if (pattern.getPatternVariable() == null && pattern.getDeconstructionList().getDeconstructionComponents().length == 0) {
        PsiClassType type = tryCast(pattern.getTypeElement().getType(), PsiClassType.class);
        if (type != null && ContainerUtil.exists(type.getParameters(), PsiWildcardType.class::isInstance)) return;
        PsiExpression expression = elementFactory.createExpressionFromText(pattern.getText(), grandParent);
        PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
        if (call == null) return;
        if (call.getMethodExpression().resolve() != null) {
          myVisitor.report(JavaErrorKinds.CALL_PARSED_AS_DECONSTRUCTION_PATTERN.create(pattern));
        }
      }
    }
  }

  void checkInstanceOfPatternSupertype(@NotNull PsiInstanceOfExpression expression) {
    @Nullable PsiPattern expressionPattern = expression.getPattern();
    PsiTypeTestPattern pattern = tryCast(expressionPattern, PsiTypeTestPattern.class);
    if (pattern == null) return;
    PsiPatternVariable variable = pattern.getPatternVariable();
    if (variable == null) return;
    PsiTypeElement typeElement = pattern.getCheckType();
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    PsiType expressionType = expression.getOperand().getType();
    if (expressionType != null && checkType.isAssignableFrom(expressionType)) {
      if (checkType.equals(expressionType)) {
        myVisitor.report(JavaErrorKinds.PATTERN_INSTANCEOF_EQUALS.create(pattern, checkType));
      }
      else {
        myVisitor.report(JavaErrorKinds.PATTERN_INSTANCEOF_SUPERTYPE.create(
          pattern, new JavaIncompatibleTypeErrorContext(checkType, expressionType)));
      }
    }
  }

  /**
   * Checks if the given record component type is applicable for the pattern type based on the specified language level.
   * For example:
   * <pre><code>
   *  record SomeClass(RecordComponentType component)
   *  (a instanceof SomeClass(PatternType obj))
   * </code></pre>
   *
   * @param recordComponentType the type of the record component
   * @param patternType         the type of the pattern
   * @param languageLevel       the language level to consider
   * @return true if the record component type is applicable for the pattern type, false otherwise
   */
  private static boolean isApplicableForRecordComponent(@NotNull PsiType recordComponentType,
                                                        @Nullable PsiType patternType,
                                                        @NotNull LanguageLevel languageLevel) {
    if ((recordComponentType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) &&
        !JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel)) {
      return recordComponentType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordComponentType, patternType);
  }
}
