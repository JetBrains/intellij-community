// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBackref;
import org.intellij.lang.regexp.psi.RegExpBranch;
import org.intellij.lang.regexp.psi.RegExpConditional;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jetbrains.annotations.Nullable;

public class RegExpConditionalImpl extends RegExpElementImpl implements RegExpConditional {
  public RegExpConditionalImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpConditional(this);
  }

  @Override
  public RegExpAtom getCondition() {
    final PsiElement sibling = getFirstChild().getNextSibling();
    if (!(sibling instanceof RegExpBackref) && !(sibling instanceof RegExpNamedGroupRef) && !(sibling instanceof RegExpGroup)) {
      return null;
    }
    return (RegExpAtom)sibling;
  }

  @Override
  public @Nullable RegExpBranch getThenBranch() {
    return PsiTreeUtil.findChildOfType(this, RegExpBranch.class);
  }

  @Override
  public @Nullable RegExpBranch getElseBranch() {
    RegExpBranch then = getThenBranch();
    if (then == null) return null;
    PsiElement union = then.getNextSibling();
    if (!(union instanceof TreeElement leaf)) return null;
    IElementType type = leaf.getTokenType();
    if (type != RegExpTT.UNION) return null;
    PsiElement next = union.getNextSibling();
    return next instanceof RegExpBranch branch ? branch : null;
  }
}
