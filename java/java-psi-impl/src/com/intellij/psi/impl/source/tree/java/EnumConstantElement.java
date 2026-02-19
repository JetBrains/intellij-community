// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class EnumConstantElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance(EnumConstantElement.class);
  public EnumConstantElement() {
    super(ENUM_CONSTANT);
  }

  @Override
  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getStartOffset();
  }

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      case ChildRole.DOC_COMMENT:
        return PsiImplUtil.findDocComment(this);

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.ARGUMENT_LIST:
        return findChildByType(EXPRESSION_LIST);

      case ChildRole.ANONYMOUS_CLASS:
        return findChildByType(ENUM_CONSTANT_INITIALIZER);

      case ChildRole.MODIFIER_LIST:
        return findChildByType(JavaElementType.MODIFIER_LIST);

      default:
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (DOC_COMMENT_TOKENS.contains(i)) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == JavaTokenType.C_STYLE_COMMENT || i == JavaTokenType.END_OF_LINE_COMMENT) {
      {
        return ChildRoleBase.NONE;
      }
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == ENUM_CONSTANT_INITIALIZER) {
      return ChildRole.ANONYMOUS_CLASS;
    }
    else if (i == EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
