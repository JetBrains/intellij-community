/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class ReferenceListElement extends CompositeElement {
  private final IElementType myKeyword;
  private final String myKeywordText;
  private final IElementType mySeparator;
  private final String mySeparatorText;

  public ReferenceListElement(IElementType type, IElementType keywordType, String keywordText) {
    this(type, keywordType, keywordText, JavaTokenType.COMMA, ",");
  }

  public ReferenceListElement(IElementType type, IElementType keyword, String keywordText, IElementType separator, String separatorText) {
    super(type);
    myKeyword = keyword;
    myKeywordText = keywordText;
    mySeparator = separator;
    mySeparatorText = separatorText;
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (first == last && first.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
      for (ASTNode child = ((ASTNode)first).getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == mySeparator) break;
        if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
          TreeElement separator = Factory.createSingleLeafElement(mySeparator, mySeparatorText, treeCharTab, getManager());
          super.addInternal(separator, separator, first, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = ((ASTNode)first).getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == mySeparator) break;
        if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
          TreeElement separator = Factory.createSingleLeafElement(mySeparator, mySeparatorText, treeCharTab, getManager());
          super.addInternal(separator, separator, child, Boolean.FALSE);
          break;
        }
      }
    }

    if (findChildByType(myKeyword) == null && findChildByType(JavaElementType.JAVA_CODE_REFERENCE) != null) {
      LeafElement keyword = Factory.createSingleLeafElement(myKeyword, myKeywordText, treeCharTab, getManager());
      super.addInternal(keyword, keyword, getFirstChildNode(), Boolean.TRUE);
    }

    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
      ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
      if (next != null && next.getElementType() == mySeparator) {
        deleteChildInternal(next);
      }
      else {
        ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
        if (prev != null &&
            (prev.getElementType() == mySeparator || prev.getElementType() == myKeyword)) {
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    assert child.getTreeParent() == this : child;
    IElementType childType = child.getElementType();
    if (childType == JavaTokenType.COMMA) return ChildRole.COMMA;
    if (childType == JavaElementType.JAVA_CODE_REFERENCE) return ChildRole.REFERENCE_IN_LIST;
    return ChildRoleBase.NONE;
  }
}