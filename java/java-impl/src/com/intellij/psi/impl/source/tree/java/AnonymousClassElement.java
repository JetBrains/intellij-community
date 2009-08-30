package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaTokenType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.JavaTokenType;

public class AnonymousClassElement extends AnonymousClassElementBase {
  public AnonymousClassElement() {
    super(ANONYMOUS_CLASS);
  }
}
