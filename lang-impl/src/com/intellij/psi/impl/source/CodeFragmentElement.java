package com.intellij.psi.impl.source;

import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.FileElement;

public class CodeFragmentElement extends FileElement {
  public CodeFragmentElement() {
    super(TokenType.CODE_FRAGMENT);
  }
}
