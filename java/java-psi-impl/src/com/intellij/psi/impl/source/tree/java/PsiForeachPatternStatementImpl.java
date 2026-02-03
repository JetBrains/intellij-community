// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiForeachPatternStatement;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PsiForeachPatternStatementImpl extends PsiForeachStatementBaseImpl implements PsiForeachPatternStatement, Constants {
  public PsiForeachPatternStatementImpl() {
    super(FOREACH_PATTERN_STATEMENT);
  }

  @Override
  public String toString() {
    return "PsiForeachPatternStatement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null || lastParent.getParent() != this || lastParent == getIteratedValue())
      // Parent element should not see our vars
      return true;

    PsiPattern pattern = getIterationPattern();
    if (pattern instanceof PsiDeconstructionPattern) {
      PsiDeconstructionPattern deconstructionPattern = (PsiDeconstructionPattern)pattern;
      return deconstructionPattern.processDeclarations(processor, state, lastParent, place);
    }
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitForeachPatternStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiPattern getIterationPattern() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiPattern.class));
  }
}
