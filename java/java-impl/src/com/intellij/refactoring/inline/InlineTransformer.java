// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.util.InlineUtil;

public abstract class InlineTransformer {
  public abstract boolean isMethodAccepted(PsiMethod method);

  public abstract boolean isReferenceAccepted(PsiReference reference);
  
  public PsiCodeBlock transformBody(PsiCodeBlock body, PsiReference reference) {
    return body;
  }
  
  static class NormalTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return !InlineMethodProcessor.checkBadReturns(method);
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return true;
    }
  }
  
  static class TailCallTransformer extends InlineTransformer {
    @Override
    public boolean isMethodAccepted(PsiMethod method) {
      return true;
    }

    @Override
    public boolean isReferenceAccepted(PsiReference reference) {
      return InlineUtil.getTailCallType(reference) != InlineUtil.TailCallType.None;
    }
  }
}
