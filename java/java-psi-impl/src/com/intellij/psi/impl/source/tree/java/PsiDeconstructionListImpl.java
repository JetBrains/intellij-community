// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.tree.JavaElementType.*;

public class PsiDeconstructionListImpl extends CompositePsiElement implements PsiDeconstructionList {
  private final TokenSet PRIMARY_PATTERN_SET = TokenSet.create(TYPE_TEST_PATTERN, DECONSTRUCTION_PATTERN, PARENTHESIZED_PATTERN);

  public PsiDeconstructionListImpl() {
    super(DECONSTRUCTION_LIST);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDeconstructionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (PRIMARY_PATTERN_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByType(JavaTokenType.RPARENTH);
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByType(JavaTokenType.LPARENTH);
        before = Boolean.FALSE;
      }
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && PRIMARY_PATTERN_SET.contains(first.getElementType())) {
      JavaSourceUtil.addSeparatingComma(this, first, PRIMARY_PATTERN_SET);
    }

    return firstAdded;
  }

  @Override
  public @NotNull PsiPattern @NotNull [] getDeconstructionComponents() {
    PsiPattern[] children = PsiTreeUtil.getChildrenOfType(this, PsiPattern.class);
    if (children == null) {
      return PsiPattern.EMPTY;
    }
    return children;
  }

  @Override
  public String toString() {
    return "PsiDeconstructionList";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    PsiPattern[] components = getDeconstructionComponents();
    for (PsiPattern component : components) {
      component.processDeclarations(processor, state, null, place);
    }
    return true;
  }
}
