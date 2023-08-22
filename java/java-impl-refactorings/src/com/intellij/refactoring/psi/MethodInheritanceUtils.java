// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.psi;

import com.intellij.psi.PsiMethod;

public final class MethodInheritanceUtils {
    private MethodInheritanceUtils() {
        super();
    }

  public static boolean hasSiblingMethods(PsiMethod method) {
        final Iterable<PsiMethod> overridingMethods =
                SearchUtils.findOverridingMethods(method);
        if(overridingMethods.iterator().hasNext())
        {
            return true;
        }
        final PsiMethod[] superMethods = method.findSuperMethods();
        return superMethods.length!=0;

    }
}
