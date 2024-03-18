// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.lang.ASTNode;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class UnsupportedNodeElementTypeException extends IncorrectOperationException {

  public UnsupportedNodeElementTypeException(@NotNull ASTNode node) {
    super("Cannot create PSI for element type " + node.getElementType() + ". AST Node: " + node + " (" + node.getClass() + ")");
  }
}
