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
package com.intellij.codeInspection.unusedSymbol;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnusedSymbolLocalInspectionBase extends AbstractBaseJavaLocalInspectionTool implements CustomSuppressableInspectionTool {
  @NonNls public static final String SHORT_NAME = HighlightInfoType.UNUSED_SYMBOL_SHORT_NAME;
  @NonNls public static final String DISPLAY_NAME = HighlightInfoType.UNUSED_SYMBOL_DISPLAY_NAME;
  @NonNls public static final String UNUSED_PARAMETERS_SHORT_NAME = "UnusedParameters";

  public boolean LOCAL_VARIABLE = true;
  public boolean FIELD = true;
  public boolean METHOD = true;
  public boolean CLASS = true;
  public boolean PARAMETER = true;
  public boolean REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    return isSuppressedFor(element, this);
  }
  public static boolean isSuppressedFor(@NotNull PsiElement element, @NotNull LocalInspectionTool tool) {
    return BaseJavaBatchLocalInspectionTool.isSuppressedFor(element, tool);
  }
  @Override
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    String shortName = getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      throw new AssertionError("HighlightDisplayKey.find(" + shortName + ") is null. Inspection: "+getClass());
    }
    SuppressQuickFix[] batchSuppressActions = BatchSuppressManager.SERVICE.getInstance().createBatchSuppressActions(key);
    return SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(batchSuppressActions);
  }
}
