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
package com.intellij.psi.impl.source.resolve;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.scope.util.PsiScopesUtil;
import org.jetbrains.annotations.NotNull;

public class ResolveVariableUtil {
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