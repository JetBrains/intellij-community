// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class PsiSwitchLabeledRuleStatementImpl extends PsiSwitchLabelStatementBaseImpl implements PsiSwitchLabeledRuleStatement {
  private static final TokenSet BODY_STATEMENTS =
    TokenSet.create(JavaElementType.BLOCK_STATEMENT, JavaElementType.THROW_STATEMENT, JavaElementType.EXPRESSION_STATEMENT);

  public PsiSwitchLabeledRuleStatementImpl() {
    super(JavaElementType.SWITCH_LABELED_RULE);
  }

  @Override
  public PsiStatement getBody() {
    return (PsiStatement)findPsiChildByType(BODY_STATEMENTS);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabeledRuleStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSwitchLabeledRule";
  }
}