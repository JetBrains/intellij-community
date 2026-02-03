// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.scope.util.PsiScopesUtil;
import org.jetbrains.annotations.NotNull;

public final class ResolveVariableUtil {
  public static PsiVariable resolveVariable(@NotNull PsiJavaCodeReferenceElement ref,
                                            boolean[] problemWithAccess,
                                            boolean[] problemWithStatic
  ) {

    /*
    long time1 = System.currentTimeMillis();
    */

    final VariableResolverProcessor processor = new VariableResolverProcessor(ref, ref.getContainingFile());
    PsiScopesUtil.resolveAndWalk(processor, ref, null);

    /*
    long time2 = System.currentTimeMillis();
    Statistics.resolveVariableTime += (time2 - time1);
    Statistics.resolveVariableCount++;
    */
    final JavaResolveResult[] result = processor.getResult();
    if (result.length != 1) return null;
    final PsiVariable refVar = (PsiVariable)result[0].getElement();

    if (problemWithAccess != null) {
      problemWithAccess[0] = !result[0].isAccessible();
    }
    if (problemWithStatic != null) {
      problemWithStatic[0] = !result[0].isStaticsScopeCorrect();
    }


    return refVar;
  }
}