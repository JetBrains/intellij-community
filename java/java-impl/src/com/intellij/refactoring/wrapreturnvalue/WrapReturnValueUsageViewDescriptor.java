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
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class WrapReturnValueUsageViewDescriptor implements UsageViewDescriptor {

    @NotNull
    private final PsiMethod method;

    WrapReturnValueUsageViewDescriptor(@NotNull PsiMethod method,
                                       UsageInfo[] usages){
        super();
        this.method = method;
    }

    @NotNull
    public PsiElement[] getElements(){
        return new PsiElement[]{method};
    }

    public String getProcessedElementsHeader(){
        return RefactorJBundle.message("method.whose.return.are.to.wrapped");
    }

    public String getCodeReferencesText(int usagesCount, int filesCount){
        return RefactorJBundle.message("references.to.be.modified.usage.view",
                MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, RefactorJBundle.message("reference")));
    }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
