package com.intellij.formatting;

import com.intellij.lang.ASTNode;

/**
 * @author yole
 */
public interface ASTBlock extends Block {
  ASTNode getNode();
}
