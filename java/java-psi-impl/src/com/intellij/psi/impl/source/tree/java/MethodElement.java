/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class MethodElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.MethodElement");

  public MethodElement() {
    super(METHOD);
  }

  protected MethodElement(IElementType type) {
    super(type);
  }

  @Override
  public int getTextOffset() {
    ASTNode name = findChildByType(IDENTIFIER);
    return name != null ? name.getStartOffset() : this.getStartOffset();
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getElementType() == JavaElementType.CODE_BLOCK) {
      ASTNode semicolon = TreeUtil.findChildBackward(this, SEMICOLON);
      if (semicolon != null) {
        deleteChildInternal(semicolon);
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public ASTNode copyElement() {
    CharTable table = SharedImplUtil.findCharTableByTree(this);
    final PsiClass psiClass = ((PsiMethod)getPsi()).getContainingClass();
    return psiClass != null ? ChangeUtil.copyElement(this, psiClass.getTypeParameterList(), table) : super.copyElement();
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == CODE_BLOCK) {
      final ASTNode prevWS = TreeUtil.prevLeaf(child);
      if (prevWS != null && prevWS.getElementType() == TokenType.WHITE_SPACE) {
        removeChild(prevWS);
      }
      super.deleteChildInternal(child);
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, ";", 0, 1, treeCharTab, getManager());
      addInternal(semicolon, semicolon, null, Boolean.TRUE);
    }
    else if (child.getElementType() == PARAMETER_LIST) {
      throw new IllegalArgumentException("Deleting parameter list is prohibited");
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        return PsiImplUtil.findDocComment(this);

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.TYPE_PARAMETER_LIST:
        return findChildByType(TYPE_PARAMETER_LIST);

      case ChildRole.NAME:
        return findChildByType(IDENTIFIER);

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.METHOD_BODY:
        return findChildByType(CODE_BLOCK);

      case ChildRole.PARAMETER_LIST:
        return findChildByType(PARAMETER_LIST);

      case ChildRole.THROWS_LIST:
        return findChildByType(THROWS_LIST);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);

      case ChildRole.DEFAULT_KEYWORD:
        return findChildByType(DEFAULT_KEYWORD);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaDocElementType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE_PARAMETER_LIST) {
      return ChildRole.TYPE_PARAMETER_LIST;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.METHOD_BODY;
    }
    else if (i == PARAMETER_LIST) {
      return ChildRole.PARAMETER_LIST;
    }
    else if (i == THROWS_LIST) {
      return ChildRole.THROWS_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else if (i == DEFAULT_KEYWORD) {
      return ChildRole.DEFAULT_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}
