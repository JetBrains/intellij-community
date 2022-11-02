// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PatternHighlightingModel {

  static void createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern, @NotNull HighlightInfoHolder holder) {
    if (deconstructionPattern == null) return;
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType deconstructionType = typeElement.getType();
    PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(deconstructionType);
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("switch.record.required", typeElement.getText());
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    if (recordClass.hasTypeParameters() &&
        deconstructionType instanceof PsiClassType && !((PsiClassType)deconstructionType).hasParameters()) {
      String message = JavaErrorBundle.message("error.raw.deconstruction", typeElement.getText());
      HighlightInfo info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      holder.add(info);
      return;
    }
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] patternComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    if (recordComponents.length != patternComponents.length) {
      String message = JavaAnalysisBundle.message("arguments.count.mismatch", recordComponents.length, patternComponents.length);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(deconstructionPattern.getDeconstructionList())
        .description(message)
        .escapedToolTip(message).create();
      holder.add(info);
      return;
    }

    for (int i = 0; i < recordComponents.length; i++) {
      PsiPattern patternComponent = patternComponents[i];
      PsiType recordType = recordComponents[i].getType();
      PsiType patternType = JavaPsiPatternUtil.getPatternType(patternComponent);
      if (patternType == null ||
          !recordType.equals(patternType) && !JavaPsiPatternUtil.dominates(recordType, patternType)) {
        HighlightInfo info =
          HighlightUtil.createIncompatibleTypeHighlightInfo(recordType, patternType, patternComponent.getTextRange(), 0);
        holder.add(info);
      }
      if (patternComponent instanceof PsiDeconstructionPattern) {
        createDeconstructionErrors((PsiDeconstructionPattern)patternComponent, holder);
      }
    }
  }
}
