/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base java local inspection which provides batch suppress actions, i.e. actions which don't need UI components to run (e.g. Editor).
 * Please use this class if your inspection and its fixes
 *  - work with PSI or document only and
 *  - don't provide {@link com.intellij.codeInsight.intention.IntentionAction} for quick fixes/suppression, making do with {@link LocalQuickFix} only.
 */
public abstract class BaseJavaBatchLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool {
  @Deprecated
  public static boolean isSuppressedFor(@NotNull PsiElement element, @NotNull LocalInspectionTool tool) {
    BatchSuppressManager manager = BatchSuppressManager.SERVICE.getInstance();
    String alternativeId;
    String toolId = tool.getID();
    return manager.isSuppressedFor(element, toolId) ||
           (alternativeId = tool.getAlternativeID()) != null &&
           !alternativeId.equals(toolId) && manager.isSuppressedFor(element, alternativeId);
  }
}
