/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */

//Retrieves method reference from this pair, do NOT reuse!!!
public class NameValuePairElement extends CompositeElement  {

  public NameValuePairElement() {
    super(JavaElementType.NAME_VALUE_PAIR);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
      return ChildRole.ANNOTATION_VALUE;
    }
    if (child.getElementType() == JavaTokenType.IDENTIFIER) {
      return ChildRole.NAME;
    }
    if (child.getElementType() == JavaTokenType.EQ) {
      return ChildRole.OPERATION_SIGN;
    }

    return ChildRoleBase.NONE;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    if (role == ChildRole.NAME) {
      return findChildByType(JavaTokenType.IDENTIFIER);
    }
    if (role == ChildRole.ANNOTATION_VALUE) {
      return findChildByType(ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET);
    }
    if (role == ChildRole.OPERATION_SIGN) {
      return findChildByType(JavaTokenType.EQ);
    }

    return null;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    final TreeElement treeElement = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == JavaTokenType.IDENTIFIER) {
      LeafElement eq = Factory.createSingleLeafElement(JavaTokenType.EQ, "=", 0, 1, treeCharTab, getManager());
      super.addInternal(eq, eq, first, Boolean.FALSE);
    }
    return treeElement;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    super.deleteChildInternal(child);
    if (child.getElementType() == JavaTokenType.IDENTIFIER) {
      final ASTNode sign = findChildByRole(ChildRole.OPERATION_SIGN);
      if (sign != null) {
        super.deleteChildInternal(sign);
      }
    }
  }
}
