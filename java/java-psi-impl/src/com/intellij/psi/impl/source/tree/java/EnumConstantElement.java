/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class EnumConstantElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.EnumConstantElement");
  public EnumConstantElement() {
    super(ENUM_CONSTANT);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getStartOffset();
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

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
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaDocElementType.DOC_COMMENT) {
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
