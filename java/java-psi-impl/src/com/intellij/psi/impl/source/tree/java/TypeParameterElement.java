// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class TypeParameterElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(TypeParameterElement.class);

  public TypeParameterElement() {
    super(JavaElementType.TYPE_PARAMETER);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType i = child.getElementType();
    if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaElementType.EXTENDS_BOUND_LIST) {
      return getChildRole(child, ChildRole.EXTENDS_LIST);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));

    switch (role) {
      default:
        return null;

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.EXTENDS_LIST:
        return findChildByType(JavaElementType.EXTENDS_BOUND_LIST);
    }
  }
}
