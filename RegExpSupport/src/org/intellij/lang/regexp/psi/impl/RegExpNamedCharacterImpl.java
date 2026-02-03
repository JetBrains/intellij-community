// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpNamedCharacter;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RegExpNamedCharacterImpl extends RegExpCharImpl implements RegExpNamedCharacter {

  public RegExpNamedCharacterImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable ASTNode getNameNode() {
    return getNode().findChildByType(RegExpTT.NAME);
  }

  @Override
  public String getName() {
    final ASTNode node = getNameNode();
    return (node == null) ? null : node.getText();
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpNamedCharacter(this);
  }
}
