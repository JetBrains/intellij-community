// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class PatternHighlightingModel {

  public static @NotNull List<HighlightInfo> createDeconstructionErrors(@Nullable PsiDeconstructionPattern deconstructionPattern) {
    if (deconstructionPattern == null) return Collections.emptyList();
    PsiTypeElement typeElement = deconstructionPattern.getTypeElement();
    PsiType deconstructionType = typeElement.getType();
    PsiClass recordClass = PsiUtil.resolveClassInClassTypeOnly(deconstructionType);
    if (recordClass == null || !recordClass.isRecord()) {
      String message = JavaErrorBundle.message("switch.record.required", typeElement.getText());
      HighlightInfo error =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create();
      return new SmartList<>(error);
    }
    PsiRecordComponent[] recordComponents = recordClass.getRecordComponents();
    PsiPattern[] patternComponents = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
    if (recordComponents.length != patternComponents.length) {
      String message = HighlightMethodUtil.createMismatchedArgumentCountTooltip(recordComponents.length, patternComponents.length);
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(deconstructionPattern.getDeconstructionList())
        .description(message)
        .escapedToolTip(message).create();
      return new SmartList<>(info);
    }

    List<HighlightInfo> results = new SmartList<>();
    for (int i = 0; i < recordComponents.length; i++) {
      PsiPattern patternComponent = patternComponents[i];
      PsiType recordType = recordComponents[i].getType();
      PsiType patternType = JavaPsiPatternUtil.getPatternType(patternComponent);
      if (patternType == null ||
          !recordType.equals(patternType) && !JavaPsiPatternUtil.dominates(recordType, patternType)) {
        HighlightInfo info =
          HighlightUtil.createIncompatibleTypeHighlightInfo(recordType, patternType, patternComponent.getTextRange(), 0);
        results.add(info);
      }
      if (patternComponent instanceof PsiDeconstructionPattern) {
        results.addAll(createDeconstructionErrors(((PsiDeconstructionPattern)patternComponent)));
      }
    }
    return results;
  }
}
