/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class TypeParameterExtendsBoundsListElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.TypeParameterExtendsBoundsListElement");

  public TypeParameterExtendsBoundsListElement() {
    super(JavaElementType.EXTENDS_BOUND_LIST);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (first == last && first.getElementType() == JAVA_CODE_REFERENCE){
      ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == AND) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(AND, "&", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == AND) break;
        if (child.getElementType() == JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(AND, "&", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    final IElementType keywordType = JavaTokenType.EXTENDS_KEYWORD;
    final String keywordText = PsiKeyword.EXTENDS;
    if (findChildByType(keywordType) == null && findChildByType(JAVA_CODE_REFERENCE) != null){
      LeafElement keyword = Factory.createSingleLeafElement(keywordType, keywordText, treeCharTab, getManager());
      super.addInternal(keyword, keyword, getFirstChildNode(), Boolean.TRUE);
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JAVA_CODE_REFERENCE){
      ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
      if (next != null && next.getElementType() == AND){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
        if (prev != null){
          if (prev.getElementType() == AND || prev.getElementType() == EXTENDS_KEYWORD){
            deleteChildInternal(prev);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public int getChildRole(final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    final IElementType elType = child.getElementType();
    if (elType == AND) {
      return ChildRole.AMPERSAND_IN_BOUNDS_LIST;
    }
    else if (elType == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.BASE_CLASS_REFERENCE;
    }
    else if (elType == JavaTokenType.EXTENDS_KEYWORD) {
      return ChildRole.EXTENDS_KEYWORD;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
