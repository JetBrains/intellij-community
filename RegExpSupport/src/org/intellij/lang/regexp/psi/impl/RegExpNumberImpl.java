// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RegExpNumberImpl extends RegExpElementImpl implements RegExpNumber {

  public RegExpNumberImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable Number getValue() {
    return RegExpLanguageHosts.getInstance().getQuantifierValue(this);
  }

  @Override
  public void accept(RegExpElementVisitor visitor) {
    visitor.visitRegExpNumber(this);
  }
}
