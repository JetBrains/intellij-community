package com.intellij.psi.impl.source.tree;

import com.intellij.psi.TokenType;

public class PlainTextFileElement extends FileElement{
  public PlainTextFileElement() {
    super(TokenType.PLAIN_TEXT_FILE);
  }
}
