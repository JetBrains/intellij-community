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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceJavaCodeReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JavaSourceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.JavaSourceUtil");

  private static final TokenSet REF_FILTER = TokenSet.orSet(
    ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.ANNOTATION));

  private JavaSourceUtil() { }

  public static void fullyQualifyReference(@NotNull CompositeElement reference, @NotNull PsiClass targetClass) {
    if (((SourceJavaCodeReference)reference).isQualified()) { // qualified reference
      final PsiClass parentClass = targetClass.getContainingClass();
      if (parentClass == null) return;
      final ASTNode qualifier = reference.findChildByRole(ChildRole.QUALIFIER);
      if (qualifier instanceof SourceJavaCodeReference) {
        ((SourceJavaCodeReference)qualifier).fullyQualify(parentClass);
      }
    }
    else { // unqualified reference, need to qualify with package name
      final String qName = targetClass.getQualifiedName();
      if (qName == null) {
        return; // todo: local classes?
      }
      final int i = qName.lastIndexOf('.');
      if (i > 0) {
        final String prefix = qName.substring(0, i);
        final PsiManager manager = reference.getManager();
        final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(manager.getProject()).getParserFacade();

        final TreeElement qualifier;
        if (reference instanceof PsiReferenceExpression) {
          qualifier = (TreeElement)parserFacade.createExpressionFromText(prefix, null).getNode();
        }
        else {
          qualifier = (TreeElement)parserFacade.createReferenceFromText(prefix, null).getNode();
        }

        if (qualifier != null) {
          final CharTable systemCharTab = SharedImplUtil.findCharTableByTree(qualifier);
          final LeafElement dot = Factory.createSingleLeafElement(JavaTokenType.DOT, ".", 0, 1, systemCharTab, manager);
          qualifier.rawInsertAfterMe(dot);
          reference.addInternal(qualifier, dot, null, Boolean.FALSE);
        }
      }
    }
  }

  @NotNull
  public static String getReferenceText(@NotNull PsiJavaCodeReferenceElement ref) {
    final StringBuilder buffer = new StringBuilder();

    ((TreeElement)ref.getNode()).acceptTree(new RecursiveTreeElementWalkingVisitor() {
      @Override
      public void visitLeaf(LeafElement leaf) {
        if (!REF_FILTER.contains(leaf.getElementType())) {
          String leafText = leaf.getText();
          if (buffer.length() > 0 && !leafText.isEmpty() && Character.isJavaIdentifierPart(leafText.charAt(0))) {
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
    dummyExpr.getTreeParent().replaceChild(dummyExpr, newChild);

    // TODO remove explicit caches drop since this should be ok if we will use ChangeUtil for the modification
    TreeUtil.clearCaches(TreeUtil.getFileElement(parenthExpr));
    return parenthExpr;
  }
}
