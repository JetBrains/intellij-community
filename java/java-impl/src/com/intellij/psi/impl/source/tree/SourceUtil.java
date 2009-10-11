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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public class SourceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SourceUtil");

  private SourceUtil() {
  }

  public static String getTextSkipWhiteSpaceAndComments(ASTNode element) {
    int length = AstBufferUtil.toBuffer(element, null, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    char[] buffer = new char[length];
    AstBufferUtil.toBuffer(element, buffer, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    return new String(buffer);
  }

  public static TreeElement addParenthToReplacedChild(final IElementType parenthType,
                                                      TreeElement newChild,
                                                      PsiManager manager) {
    CompositeElement parenthExpr = ASTFactory.composite(parenthType);

    TreeElement dummyExpr = (TreeElement)newChild.clone();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(newChild);
    new DummyHolder(manager, parenthExpr, null, charTableByTree);
    parenthExpr.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.LPARENTH, "("));
    parenthExpr.rawAddChildren(dummyExpr);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.RPARENTH, ")"));

    try {
      CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      parenthExpr =
      (CompositeElement)SourceTreeToPsiMap.psiElementToTree(
        codeStyleManager.reformat(SourceTreeToPsiMap.treeElementToPsi(parenthExpr)));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e); // should not happen
    }

    newChild.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(newChild));
    dummyExpr.rawReplaceWithList(newChild);

    newChild = parenthExpr;
    // TODO remove explicit caches drop since this should be ok iff we will use ChangeUtil for the modification 
    TreeUtil.clearCaches(TreeUtil.getFileElement(newChild));
    return newChild;
  }
}
