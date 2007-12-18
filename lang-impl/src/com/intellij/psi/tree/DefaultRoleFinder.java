package com.intellij.psi.tree;

import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.ASTNode;

public class DefaultRoleFinder implements RoleFinder{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.tree.DefaultRoleFinder");
  final IElementType[] myElementTypes;

  public DefaultRoleFinder(IElementType... elementType) {
    myElementTypes = elementType;
  }

  public ASTNode findChild(ASTNode parent) {
    ASTNode current = parent.getFirstChildNode();
    while(current != null){
      for (int i = 0; i < myElementTypes.length; i++) {
        final IElementType elementType = myElementTypes[i];
        if(current.getElementType() == elementType) return current;
      }
      current = current.getTreeNext();
    }
    return null;
  }
}
