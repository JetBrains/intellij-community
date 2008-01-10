package com.intellij.psi.impl.source;

import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.FileElement;

public class DummyHolderElement extends FileElement {
  public DummyHolderElement() {
    super(TokenType.DUMMY_HOLDER);
  }
}
