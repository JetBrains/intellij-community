// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMissingDeconstructionComponentsFix.Pattern;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.PsiClassType.ClassResolveResult;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

class PatternHighlightingModel {

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType recordType = typeElement.getType();
    var resolveResult = recordType instanceof PsiClassType classType ? classType.resolveGenerics() : ClassResolveResult.EMPTY;
    PsiClass recordClass = resolveResult.getElement();
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("switch.record.required", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (recordClass.hasTypeParameters() && recordType instanceof PsiClassType classType && !classType.hasParameters()) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
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
      if (!isApplicable(substitutedRecordComponentType, deconstructionComponentType)) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          var builder = HighlightUtil.createIncompatibleTypeHighlightInfo(substitutedRecordComponentType, deconstructionComponentType,
                                                                          deconstructionComponent.getTextRange(), 0);
          holder.add(builder.create());
        }
      }
      else if (JavaGenericsUtil.isUncheckedCast(Objects.requireNonNull(deconstructionComponentType),
                                                GenericsUtil.getVariableTypeByExpressionType(substitutedRecordComponentType))) {
        hasMismatchedPattern = true;
        if (recordComponents.length == deconstructionComponents.length) {
          PsiType recordComponentTypeErasure = TypeConversionUtil.erasure(recordComponentType);
          String message = JavaErrorBundle.message("unsafe.cast.in.instanceof",
                                                   JavaHighlightUtil.formatType(recordComponentTypeErasure),
                                                   JavaHighlightUtil.formatType(deconstructionComponentType));
          holder.add(SwitchBlockHighlightingModel.createError(deconstructionComponent, message).create());
        }
      }
      if (recordComponents.length != deconstructionComponents.length && hasMismatchedPattern) {
        break;
      }
      if (deconstructionComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)deconstructionComponent, holder);
      }
    }
    if (recordComponents.length != deconstructionComponents.length) {
      HighlightInfo info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, deconstructionComponents, recordComponents,
                                                                      !hasMismatchedPattern);
      holder.add(info);
    }
  }

  private static boolean isApplicable(@NotNull PsiType recordType, @Nullable PsiType patternType) {
    if (recordType instanceof PsiPrimitiveType || patternType instanceof PsiPrimitiveType) {
      return recordType.equals(patternType);
    }
    return patternType != null && TypeConversionUtil.areTypesConvertible(recordType, patternType);
  }

  private static HighlightInfo createIncorrectNumberOfNestedPatternsError(@NotNull PsiDeconstructionPattern deconstructionPattern,
                                                                          PsiPattern @NotNull [] patternComponents,
                                                                          PsiRecordComponent @NotNull [] recordComponents,
                                                                          boolean needQuickFix) {
    assert patternComponents.length != recordComponents.length;
    String message = JavaErrorBundle.message("incorrect.number.of.nested.patterns", recordComponents.length, patternComponents.length);
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).description(message).escapedToolTip(message);
    PsiDeconstructionList deconstructionList = deconstructionPattern.getDeconstructionList();
    final IntentionAction fix;
    if (needQuickFix) {
      if (patternComponents.length < recordComponents.length) {
        builder.range(deconstructionList);
        var missingRecordComponents = Arrays.copyOfRange(recordComponents, patternComponents.length, recordComponents.length);
        var missingPatterns = ContainerUtil.map(missingRecordComponents, component -> Pattern.create(component, deconstructionList));
        fix = new AddMissingDeconstructionComponentsFix(deconstructionList, missingPatterns);
        builder.registerFix(fix, null, null, null, null);
      }
      else {
        PsiPattern[] deconstructionComponents = deconstructionList.getDeconstructionComponents();
        int endOffset = deconstructionList.getTextLength();
        int startOffset = deconstructionComponents[recordComponents.length].getStartOffsetInParent();
        TextRange textRange = TextRange.create(startOffset, endOffset);
        builder.range(deconstructionList, textRange);
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        int diff = patternComponents.length - recordComponents.length;
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text", diff);
        fix = QUICK_FIX_FACTORY.createDeleteFix(elementsToDelete, text);
        builder.registerFix(fix, null, text, null, null);
      }
    }
    else {
      builder.range(deconstructionList);
    }
    return builder.create();
  }
}
