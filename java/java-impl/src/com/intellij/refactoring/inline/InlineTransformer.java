// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;

@FunctionalInterface
public interface InlineTransformer {
  
  /**
   * Transforms method body in the way so it can be inserted into the call site. May declare result variable if necessary.  
   * 
   * @param methodCopy non-physical copy of the method to be inlined (may be changed by this call)
   * @param callSite method call
   * @param returnType substituted method return type
   * @return result variable or null if unnecessary
   */
  PsiLocalVariable transformBody(PsiMethod methodCopy, PsiReferenceExpression callSite, PsiType returnType);

  /**
   * @return true if this transformer is a fallback transformer which may significantly rewrite the method body
   */
  default boolean isFallBackTransformer() {
    return false;
  }
}
