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
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ImportStatementBaseElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImportStatementBaseElement");

  protected ImportStatementBaseElement(IElementType type) {
    super(type);
  }

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.IMPORT_KEYWORD:
        return getFirstChildNode();

      case ChildRole.IMPORT_ON_DEMAND_DOT:
        return findChildByType(JavaTokenType.DOT);

      case ChildRole.IMPORT_ON_DEMAND_ASTERISK:
        return findChildByType(JavaTokenType.ASTERISK);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.IMPORT_KEYWORD) {
      return ChildRole.IMPORT_KEYWORD;
    }
    else if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.IMPORT_REFERENCE;
    }
    else if (i == JavaTokenType.DOT) {
      return ChildRole.IMPORT_ON_DEMAND_DOT;
    }
    else if (i == JavaTokenType.ASTERISK) {
      return ChildRole.IMPORT_ON_DEMAND_ASTERISK;
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
