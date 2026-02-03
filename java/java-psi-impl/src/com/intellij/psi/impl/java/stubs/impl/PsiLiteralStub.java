// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiLiteralStub extends StubBase<PsiLiteralExpressionImpl> {
  private final @NotNull String myLiteralText;
  private volatile IElementType myLiteralType;

  public PsiLiteralStub(StubElement parent, @NotNull String literalText) {
    super(parent, JavaStubElementTypes.LITERAL_EXPRESSION);
    myLiteralText = literalText;
  }

  public @NotNull String getLiteralText() {
    return myLiteralText;
  }

  public @NotNull IElementType getLiteralType() {
    IElementType type = myLiteralType;
    if (type == null) {
      Lexer lexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
      lexer.start(myLiteralText);
      myLiteralType = type = lexer.getTokenType();
      assert type != null : myLiteralText;
    }
    return type;
  }
}
