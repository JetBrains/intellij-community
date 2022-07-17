// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.psi;

import com.intellij.psi.PsiNameHelper;

import java.util.StringTokenizer;

public final class PackageNameUtil {
    private PackageNameUtil() {
    }

    public static boolean containsNonIdentifier(PsiNameHelper nameHelper, String packageName) {

        final StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
        while(tokenizer.hasMoreTokens())
        {
            final String component = tokenizer.nextToken();
            if (!nameHelper.isIdentifier(component)) {
                return true;
            }
        }
        return false;
    }
}
