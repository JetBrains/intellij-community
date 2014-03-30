/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GlobalJavaInspectionTool extends GlobalInspectionTool implements CustomSuppressableInspectionTool {
  @Override
  public boolean queryExternalUsagesRequests(@NotNull final InspectionManager manager,
                                             @NotNull final GlobalInspectionContext globalContext,
                                             @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor);
  }

  protected boolean queryExternalUsagesRequests(@NotNull RefManager manager, @NotNull GlobalJavaInspectionContext globalContext, @NotNull ProblemDescriptionsProcessor processor) {
    return false;
  }

  @Override
  @Nullable
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return SuppressManager.getInstance().createSuppressActions(HighlightDisplayKey.find(getShortName()));
  }

  @Override
  public boolean isSuppressedFor(@NotNull final PsiElement element) {
    return SuppressManager.getInstance().isSuppressedFor(element, getShortName());
  }
}