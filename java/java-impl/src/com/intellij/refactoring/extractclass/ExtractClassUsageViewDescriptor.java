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
package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private final PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass) {
        super();
        this.aClass = aClass;
    }


    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.extract") + MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
    }

    @Override
    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("extracting.from.class");
    }

    @Override
    @NotNull
    public PsiElement[] getElements() {
        return new PsiElement[]{aClass};
    }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
