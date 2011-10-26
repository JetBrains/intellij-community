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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public abstract class ReferenceListElement extends CompositeElement {
  public ReferenceListElement(IElementType type) {
    super(type);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    if (first == last && first.getElementType() == JavaElementType.JAVA_CODE_REFERENCE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == TokenType.ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (first == last && first.getElementType() == JavaElementType.JAVA_CODE_REFERENCE){
      ASTNode element = first;
      for(ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for(ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()){
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE){
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    IElementType keywordType = getKeywordType();
    String keywordText = getKeywordText();
    if (findChildByType(keywordType) == null && findChildByType(JavaElementType.JAVA_CODE_REFERENCE) != null){
      LeafElement keyword = Factory.createSingleLeafElement(keywordType, keywordText, SharedImplUtil.findCharTableByTree(this), getManager());
      super.addInternal(keyword, keyword, getFirstChildNode(), Boolean.TRUE);
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE){
      ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
      if (next != null && next.getElementType() == JavaTokenType.COMMA){
        deleteChildInternal(next);
      }
      else{
        ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
        if (prev != null){
          if (prev.getElementType() == JavaTokenType.COMMA
              || prev.getElementType() == getKeywordType()
              ){
            deleteChildInternal(prev);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  protected abstract String getKeywordText();

  protected abstract IElementType getKeywordType();
}
