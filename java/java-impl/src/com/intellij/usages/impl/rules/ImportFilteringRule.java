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
package com.intellij.usages.impl.rules;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ImportFilteringRule extends com.intellij.usages.rules.ImportFilteringRule {
  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
    final PsiElement psiElement = usage instanceof PsiElementUsage? ((PsiElementUsage)usage).getElement() : null;
    if (psiElement != null) {
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        // check whether the element is in the import list
        final PsiImportList importList = PsiTreeUtil.getParentOfType(psiElement, PsiImportList.class, true);
        return importList == null;
      }
    }
    return true;
  }
}
