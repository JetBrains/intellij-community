package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 1, 2005
 * Time: 12:14:17 AM
 * To change this template use File | Settings | File Templates.
 */
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY = new FoldingDescriptor[0];

  private ASTNode myElement;
  private TextRange myRange;

  public FoldingDescriptor(final ASTNode node, final TextRange range) {
    myElement = node;
    myRange = range;
  }

  public ASTNode getElement() {
    return myElement;
  }

  public TextRange getRange() {
    return myRange;
  }
}
