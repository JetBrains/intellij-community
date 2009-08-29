package com.intellij.lexer;

/**
 * @author yole
 */
public class JavaDocLexer extends DocCommentLexer {
  public JavaDocLexer(final boolean isJdk15Enabled) {
    super(new JavaDocTokenTypes(), isJdk15Enabled);
  }
}
