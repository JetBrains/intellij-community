package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PlainTextTokenTypes;

public class PlainTextFileElement extends FileElement{
  public PlainTextFileElement() {
    super(PlainTextTokenTypes.PLAIN_TEXT_FILE);
  }
}
