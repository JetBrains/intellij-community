package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;

public class ClassInitializerElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ClassInitializerElement");

  public ClassInitializerElement() {
    super(CLASS_INITIALIZER);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.METHOD_BODY:
        return findChildByType(CODE_BLOCK);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == C_STYLE_COMMENT || i == END_OF_LINE_COMMENT) {
      {
        return ChildRoleBase.NONE;
      }
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.METHOD_BODY;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
