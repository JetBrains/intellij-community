// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapterBase;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Lexer to wrap around the Java Lexer when parsing Java types for the markdown JavaDoc (JEP-467)
 * In reference links, array types are escaped:  "char\[\]" which the JavaLexer doesn't like
 * <p>
 * It does this by <em>covering up</em> a <i>BAD_CHARACTER</i> token if followed by <i>[</i> or <i>]</i>
 *
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class JavaTypeEscapeLexer extends MergingLexerAdapterBase {
  private final MergeFunction myMergeFunction = new EscapeMarkdownFunction();

  public JavaTypeEscapeLexer(BasicJavaLexer original) {
    super(original);
  }

  @Override
  public MergeFunction getMergeFunction() {
    return myMergeFunction;
  }


  private static class EscapeMarkdownFunction implements MergeFunction {
    @Override
    public IElementType merge(IElementType type, Lexer originalLexer) {
      if (type != TokenType.BAD_CHARACTER) return type;

      final IElementType tokenType = originalLexer.getTokenType();
      if (tokenType != JavaTokenType.LBRACKET && tokenType != JavaTokenType.RBRACKET) {
        return type;
      }

      originalLexer.advance();
      return tokenType;
    }
  }
}
