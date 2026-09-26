// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.generate.tostring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Contract;

/**
 * An extension which allows to prohibit toString generation for some classes
 */
public interface GenerateToStringClassFilter {
    ExtensionPointName<GenerateToStringClassFilter> EP_NAME = ExtensionPointName.create("com.intellij.generation.toStringClassFilter");

    /**
     * @param psiClass class to check
     * @return return false if toString should not be generated for the class
     */
    @Contract(pure = true)
    boolean canGenerateToString(PsiClass psiClass);
}
