// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.generate.tostring;

import com.intellij.psi.PsiClass;

/**
 * Nikolay.Tropin
 * 2014-12-01
 */
public final class GenerateToStringInterfaceFilter implements GenerateToStringClassFilter {

    @Override
    public boolean canGenerateToString(PsiClass psiClass) {
        return !psiClass.isInterface();
    }
}
