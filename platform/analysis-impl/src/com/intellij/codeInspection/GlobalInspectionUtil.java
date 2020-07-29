// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GlobalInspectionUtil {
  private static final String LOC_MARKER = " #loc";

  @NotNull
  public static String createInspectionMessage(@NotNull String message) {
    //TODO: FIXME!
    return message + LOC_MARKER;
  }

  public static void createProblem(@NotNull PsiElement elt,
                                   @NotNull HighlightInfo info,
                                   TextRange range,
                                   @Nullable ProblemGroup problemGroup,
                                   @NotNull InspectionManager manager,
                                   @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor,
                                   @NotNull GlobalInspectionContext globalContext) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (info.quickFixActionRanges != null) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRange : info.quickFixActionRanges) {
        final IntentionAction action = actionRange.getFirst().getAction();
        if (action instanceof LocalQuickFix) {
          fixes.add((LocalQuickFix)action);
        }
      }
    }
    ProblemDescriptor descriptor = manager.createProblemDescriptor(elt, range, createInspectionMessage(StringUtil.notNullize(info.getDescription())),
                                                                   HighlightInfo.convertType(info.type), false,
                                                                   fixes.isEmpty() ? null : fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    descriptor.setProblemGroup(problemGroup);
    problemDescriptionsProcessor.addProblemElement(
      GlobalInspectionContextUtil.retrieveRefElement(elt, globalContext),
      descriptor
    );
  }
}
