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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public abstract class AnonymousClassElementBase extends ClassElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.AnonymousClassElement");

  public AnonymousClassElementBase(IElementType type) {
    super(type);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.BASE_CLASS_REFERENCE:
        return getFirstChildNode().getElementType() == JavaElementType.JAVA_CODE_REFERENCE ? getFirstChildNode() : null;

      case ChildRole.ARGUMENT_LIST:
        return findChildByType(JavaElementType.EXPRESSION_LIST);

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return getChildRole(child, ChildRole.BASE_CLASS_REFERENCE);
    }
    else if (i == JavaElementType.EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == JavaElementType.FIELD) {
      return ChildRole.FIELD;
    }
    else if (i == JavaElementType.METHOD) {
      return ChildRole.METHOD;
    }
    else if (i == JavaElementType.CLASS_INITIALIZER) {
      return ChildRole.CLASS_INITIALIZER;
    }
    else if (i == JavaElementType.CLASS) {
      return ChildRole.CLASS;
    }
    else if (i == JavaTokenType.LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == JavaTokenType.RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
