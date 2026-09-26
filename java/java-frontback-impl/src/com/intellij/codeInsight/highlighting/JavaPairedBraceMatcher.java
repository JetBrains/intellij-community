// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class JavaPairedBraceMatcher extends PairedBraceAndAnglesMatcher {
  private static class Holder {
    private static final TokenSet TYPE_TOKENS =
      TokenSet.orSet(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET,
                     TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.COMMA,
                                     JavaTokenType.AT,//anno
                                     JavaTokenType.RBRACKET, JavaTokenType.LBRACKET, //arrays
                                     JavaTokenType.QUEST, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD));//wildcards
  }

  public JavaPairedBraceMatcher() {
    super(new JavaBraceMatcher(), JavaLanguage.INSTANCE, JavaFileType.INSTANCE, Holder.TYPE_TOKENS);
  }

  @Override
  public @NotNull IElementType lt() {
    return JavaTokenType.LT;
  }

  @Override
  public @NotNull IElementType gt() {
    return JavaTokenType.GT;
  }
}
