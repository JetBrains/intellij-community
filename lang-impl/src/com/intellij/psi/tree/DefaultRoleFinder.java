package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class DefaultRoleFinder implements RoleFinder{
  protected IElementType[] myElementTypes;

  public DefaultRoleFinder(IElementType... elementType) {
    myElementTypes = elementType;
  }

  public ASTNode findChild(@NotNull ASTNode parent) {
    ASTNode current = parent.getFirstChildNode();
    while(current != null){
      for (final IElementType elementType : myElementTypes) {
        if (current.getElementType() == elementType) return current;
      }
      current = current.getTreeNext();
    }
    return null;
  }
}
