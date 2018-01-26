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
 * @author max
 */
package com.intellij.find.findUsages;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThrowsUsageTargetProvider implements UsageTargetProvider {
  @Override
  @Nullable
  public UsageTarget[] getTargets(@NotNull Editor editor, @NotNull final PsiFile file) {
    PsiElement element = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset()));
    if (element == null) return null;

    if (element instanceof PsiKeyword && PsiKeyword.THROWS.equals(element.getText())) {
      return new UsageTarget[]{new PsiElement2UsageTargetAdapter(element)};
    }

    final PsiElement parent = element.getParent();
    if (parent instanceof PsiThrowStatement) {
      return new UsageTarget[] {new PsiElement2UsageTargetAdapter(parent)};
    }

    return null;
  }

  @Override
  public UsageTarget[] getTargets(@NotNull PsiElement psiElement) {
    return null;
  }
}