package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Dec 6, 2004
 * Time: 7:40:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class BraceTokenParser extends PrefixedTokenParser {
  public BraceTokenParser(String prefix, IElementType tokenType) {
    super(prefix, tokenType);
  }

  protected int getTokenEnd(int position) {
    return position;
  }
}
