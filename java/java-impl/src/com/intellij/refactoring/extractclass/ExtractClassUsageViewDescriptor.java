// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private final PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass) {
        super();
        this.aClass = aClass;
    }


    @NotNull
    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.extract", usagesCount, filesCount);
    }

    @Override
    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("extracting.from.class");
    }

    @Override
    public PsiElement @NotNull [] getElements() {
        return new PsiElement[]{aClass};
    }
}
