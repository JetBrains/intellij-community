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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SourceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SourceUtil");

  private static final TokenSet REF_FILTER = TokenSet.orSet(
    ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.ANNOTATION));

  private SourceUtil() { }

  @NotNull
  public static String getReferenceText(@NotNull PsiJavaCodeReferenceElement ref) {
    final StringBuilder buffer = new StringBuilder();

    ((TreeElement)ref.getNode()).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        if (!REF_FILTER.contains(leaf.getElementType())) {
          String leafText = leaf.getText();
          if (buffer.length() > 0 && leafText.length() > 0 && Character.isJavaIdentifierPart(leafText.charAt(0))) {
            char lastInBuffer = buffer.charAt(buffer.length() - 1);
            if (lastInBuffer == '?' || Character.isJavaIdentifierPart(lastInBuffer)) {
              buffer.append(" ");
            }
          }

          buffer.append(leafText);
        }
      }

      @Override
      public void visitComposite(CompositeElement composite) {
        if (!REF_FILTER.contains(composite.getElementType())) {
          super.visitComposite(composite);
        }
      }
    });

    return buffer.toString();
  }

  @NotNull
  public static String getReferenceText(@NotNull LighterAST tree, @NotNull LighterASTNode node) {
    return LightTreeUtil.toFilteredString(tree, node, REF_FILTER);
  }

  /** @deprecated use {@link AstBufferUtil#getTextSkippingWhitespaceComments(ASTNode)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static String getTextSkipWhiteSpaceAndComments(ASTNode element) {
    return AstBufferUtil.getTextSkippingWhitespaceComments(element);
  }

  /** @deprecated use {@link LightTreeUtil#toFilteredString(LighterAST, LighterASTNode, TokenSet)} (to remove in IDEA 13) */
  @SuppressWarnings("UnusedDeclaration")
  public static String getTextSkipWhiteSpaceAndComments(LighterAST tree, LighterASTNode node) {
    return LightTreeUtil.toFilteredString(tree, node, ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET);
  }

  public static TreeElement addParenthToReplacedChild(@NotNull IElementType parenthType,
                                                      @NotNull TreeElement newChild,
                                                      @NotNull PsiManager manager) {
    CompositeElement parenthExpr = ASTFactory.composite(parenthType);

    TreeElement dummyExpr = (TreeElement)newChild.clone();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(newChild);
    new DummyHolder(manager, parenthExpr, null, charTableByTree);
    parenthExpr.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.LPARENTH, "("));
    parenthExpr.rawAddChildren(dummyExpr);
    parenthExpr.rawAddChildren(ASTFactory.leaf(JavaTokenType.RPARENTH, ")"));

    try {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
      PsiElement formatted = codeStyleManager.reformat(SourceTreeToPsiMap.treeToPsiNotNull(parenthExpr));
      parenthExpr = (CompositeElement)SourceTreeToPsiMap.psiToTreeNotNull(formatted);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e); // should not happen
    }

    newChild.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(newChild));
    dummyExpr.rawReplaceWithList(newChild);

    newChild = parenthExpr;
    // TODO remove explicit caches drop since this should be ok if we will use ChangeUtil for the modification
    TreeUtil.clearCaches(TreeUtil.getFileElement(newChild));
    return newChild;
  }
}
