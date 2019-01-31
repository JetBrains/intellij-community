// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;

public abstract class PsiSwitchBlockImpl extends CompositePsiElement implements PsiSwitchBlock {
  protected PsiSwitchBlockImpl(IElementType type) {
    super(type);
  }

  @Override
  public PsiExpression getExpression() {
    return (PsiExpression)findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)findPsiChildByType(JavaElementType.CODE_BLOCK);
  }

  @Override
  public PsiJavaToken getLParenth() {
    return (PsiJavaToken)findPsiChildByType(JavaTokenType.LPARENTH);
  }

  @Override
  public PsiJavaToken getRParenth() {
    return (PsiJavaToken)findPsiChildByType(JavaTokenType.RPARENTH);
  }
}