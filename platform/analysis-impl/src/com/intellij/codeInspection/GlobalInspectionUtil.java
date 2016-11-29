/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * User: Maxim.Mossienko
 * Date: 16.09.2009
 * Time: 20:35:06
 */
public class GlobalInspectionUtil {
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
                                                                   fixes.isEmpty() ? null : fixes.toArray(new LocalQuickFix[fixes.size()]));
    descriptor.setProblemGroup(problemGroup);
    problemDescriptionsProcessor.addProblemElement(
      GlobalInspectionContextUtil.retrieveRefElement(elt, globalContext),
      descriptor
    );
  }
}
