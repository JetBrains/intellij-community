/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

public interface LighterASTNode {
  IElementType getTokenType();
  int getStartOffset();
  int getEndOffset();
}