// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.QuickFixFactory.Pattern;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

class PatternHighlightingModel {

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType deconstructionType = typeElement.getType();
    PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(deconstructionType);
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("switch.record.required", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (recordClass.hasTypeParameters() && deconstructionType instanceof PsiClassType classType && !classType.hasParameters()) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      var info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] patternComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    boolean missingOrRedundantComponents = recordComponents.length != patternComponents.length;
    for (int i = 0; i < Math.min(recordComponents.length, patternComponents.length); i++) {
      PsiPattern patternComponent = patternComponents[i];
      PsiType recordType = recordComponents[i].getType();
      PsiType patternType = JavaPsiPatternUtil.getPatternType(patternComponent);
      if (patternType == null || !recordType.equals(patternType) && !JavaPsiPatternUtil.dominates(recordType, patternType)) {
        if (recordComponents.length == patternComponents.length) {
          var builder = HighlightUtil.createIncompatibleTypeHighlightInfo(recordType, patternType, patternComponent.getTextRange(), 0);
          holder.add(builder.create());
        }
        else {
          missingOrRedundantComponents = false;
          break;
        }
      }
      if (patternComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)patternComponent, holder);
      }
    }
    if (recordComponents.length != patternComponents.length) {
      HighlightInfo info = createIncorrectNumberOfNestedPatternsError(deconstructionPattern, patternComponents, recordComponents,
                                                                      missingOrRedundantComponents);
      holder.add(info);
    }
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
        fix = QUICK_FIX_FACTORY.createAddMissingNestedPatternsFix(deconstructionList, missingPatterns);
        builder.registerFix(fix, null, null, null, null);
      }
      else {
        PsiPattern[] deconstructionComponents = deconstructionList.getDeconstructionComponents();
        int endOffset = deconstructionList.getTextLength();
        int startOffset = deconstructionComponents[recordComponents.length].getStartOffsetInParent();
        TextRange textRange = TextRange.create(startOffset, endOffset);
        builder.range(deconstructionList, textRange);
        PsiPattern[] elementsToDelete = Arrays.copyOfRange(patternComponents, recordComponents.length, patternComponents.length);
        String text = QuickFixBundle.message("remove.redundant.nested.patterns.fix.text",
                                                 patternComponents.length -
                                                 recordComponents.length == 1 ? 0 : 1);
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
