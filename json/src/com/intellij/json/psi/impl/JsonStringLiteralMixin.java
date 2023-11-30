// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public abstract class JsonStringLiteralMixin extends JsonLiteralImpl implements PsiLanguageInjectionHost {
  protected JsonStringLiteralMixin(ASTNode node) {
    super(node);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new JSStringLiteralEscaper<PsiLanguageInjectionHost>(this) {
      @Override
      protected boolean isRegExpLiteral() {
        return false;
      }
    };
  }

  @Override
  public void subtreeChanged() {
    putUserData(JsonPsiImplUtils.STRING_FRAGMENTS, null);
  }
}
