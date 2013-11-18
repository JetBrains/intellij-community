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
package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class RemoveMiddlemanUsageViewDescriptor implements UsageViewDescriptor {
  private @NotNull final PsiField field;

  RemoveMiddlemanUsageViewDescriptor(@NotNull PsiField field) {
    super();
    this.field = field;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactorJBundle
      .message("references.to.expose.usage.view", MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference"));
  }

  public String getProcessedElementsHeader() {
    return RefactorJBundle.message("remove.middleman.field.header");
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{field};
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
