/*
 * @author max
 */
package com.intellij.lang.java;

import com.intellij.lang.WordCompletionElementFilter;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class JavaWordCompletionFilter implements WordCompletionElementFilter {
  private static final TokenSet ENABLED_TOKENS = TokenSet.create(JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT,
                                                                 JavaDocTokenType.DOC_COMMENT_DATA, JavaTokenType.STRING_LITERAL);

  public boolean isWordCompletionEnabledIn(final IElementType element) {
    return ENABLED_TOKENS.contains(element);
  }
}