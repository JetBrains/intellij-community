// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

final class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private final PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass) {
        super();
        this.aClass = aClass;
    }


    @Override
    public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
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
