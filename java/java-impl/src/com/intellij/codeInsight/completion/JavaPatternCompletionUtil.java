// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiCaseLabelElementList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeTestPattern;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class JavaPatternCompletionUtil {

  public static boolean insideDeconstructionList(@NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, element)) return false;
    return element.getParent() instanceof PsiJavaCodeReferenceElement ref &&
           ref.getParent() instanceof PsiTypeElement typeElement &&
           (typeElement.getParent() instanceof PsiDeconstructionList ||
            (typeElement.getParent() instanceof PsiPatternVariable patternVariable &&
             patternVariable.getParent() instanceof PsiTypeTestPattern typeTestPattern &&
             typeTestPattern.getParent() instanceof PsiDeconstructionList));
  }

  /**
   * @param psiFile file (=element.getContainingFile())
   * @param element element where completion is invoked
   * @return true if patterns should be suggested here
   */
  public static boolean isPatternContext(@NotNull PsiFile psiFile, @NotNull PsiElement element) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS, psiFile)) return false;
    if (!(element instanceof PsiIdentifier)) return false;
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiJavaCodeReferenceElement)) return false;
    PsiElement parent1 = parent.getParent();
    if (parent1 instanceof PsiCaseLabelElementList) return true;
    if (!(parent1 instanceof PsiTypeElement)) return false;
    PsiElement parent2 = parent1.getParent();
    return parent2 instanceof PsiInstanceOfExpression;
  }

  /**
   * Suggests primitives inside a deconstruction list pattern.
   *
   * @param currentPosition the current position within the deconstruction component
   * @param result          the consumer to sink suggestions into
   */
  static void suggestPrimitivesInsideDeconstructionListPattern(@NotNull PsiElement currentPosition,
                                                               @NotNull Consumer<? super LookupElement> result) {
    PsiRecordComponent component = getRecordComponentForDeconstructionComponent(currentPosition);
    if (component == null) return;
    PsiType type = component.getType();
    if (PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, currentPosition)) {
      suggestPrimitiveTypesForPattern(currentPosition, type, result);
      return;
    }
    if (type instanceof PsiPrimitiveType) {
      LookupElement lookupItem = BasicExpressionCompletionContributor.createKeywordLookupItem(currentPosition, type.getCanonicalText());
      result.accept(OverridableSpace.create(lookupItem, TailTypes.spaceType()));
    }
  }

  /**
   * Returns the PsiRecordComponent near the current position.
   *
   * @param currentPosition the current position within the deconstruction component
   * @return the PsiRecordComponent associated with the deconstruction component, or null if not found
   */
  public static @Nullable PsiRecordComponent getRecordComponentForDeconstructionComponent(@NotNull PsiElement currentPosition) {
    PsiDeconstructionList deconstructionList = PsiTreeUtil.getParentOfType(currentPosition, PsiDeconstructionList.class);
    if (deconstructionList == null) return null;
    PsiDeconstructionPattern deconstructionPattern = ObjectUtils.tryCast(deconstructionList.getParent(), PsiDeconstructionPattern.class);
    if (deconstructionPattern == null) return null;
    PsiClass psiRecord = PsiUtil.resolveClassInClassTypeOnly(deconstructionPattern.getTypeElement().getType());
    if (psiRecord == null || !psiRecord.isRecord()) return null;
    @NotNull PsiPattern @NotNull [] components = deconstructionList.getDeconstructionComponents();
    int indexOfPattern = -1;
    for (int i = 0; i < components.length; i++) {
      PsiPattern component = components[i];
      if (PsiTreeUtil.isAncestor(component, currentPosition, false)) {
        indexOfPattern = i;
        break;
      }
    }
    if (indexOfPattern == -1) {
      indexOfPattern = components.length;
    }
    PsiRecordComponent[] recordComponents = psiRecord.getRecordComponents();
    if (recordComponents.length <= indexOfPattern) return null;
    return recordComponents[indexOfPattern];
  }

  static void suggestPrimitiveTypesForPattern(@NotNull PsiElement currentPosition,
                                              @Nullable PsiType fromType,
                                              @NotNull Consumer<? super LookupElement> result) {
    if (fromType == null) return;
    if (!PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, currentPosition)) return;
    for (PsiType primitiveType : PsiTypes.primitiveTypes()) {
      if (TypeConversionUtil.areTypesConvertible(fromType, primitiveType)) {
        LookupElement lookupItem =
          BasicExpressionCompletionContributor.createKeywordLookupItem(currentPosition, primitiveType.getCanonicalText());
        result.accept(OverridableSpace.create(lookupItem, TailTypes.spaceType()));
      }
    }
  }
}
