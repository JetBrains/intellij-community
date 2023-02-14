// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AnnotationParamListElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(AnnotationParamListElement.class);
  private static final TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(JavaElementType.NAME_VALUE_PAIR);

  public AnnotationParamListElement() {
    super(JavaElementType.ANNOTATION_PARAMETER_LIST);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == JavaTokenType.RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(i) ||
             (i == JavaElementType.NAME_VALUE_PAIR && child.getFirstChildNode() != null &&
              child.getFirstChildNode().getElementType() == JavaElementType.ANNOTATION_ARRAY_INITIALIZER)) {
      return ChildRole.ANNOTATION_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    switch (role) {
      default:
        LOG.assertTrue(false);
        return null;
      case ChildRole.LPARENTH:
        return findChildByType(JavaTokenType.LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(JavaTokenType.RPARENTH);
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ASTNode lparenth = findChildByType(JavaTokenType.LPARENTH);
    if (lparenth == null) {
      CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement created = Factory.createSingleLeafElement(JavaTokenType.LPARENTH, "(", 0, 1, treeCharTab, getManager());
      super.addInternal(created, created, getFirstChildNode(), true);
    }

    ASTNode rparenth = findChildByType(JavaTokenType.RPARENTH);
    if (rparenth == null) {
      CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement created = Factory.createSingleLeafElement(JavaTokenType.RPARENTH, ")", 0, 1, treeCharTab, getManager());
      super.addInternal(created, created, getLastChildNode(), false);
    }

    ASTNode[] nodes = getChildren(NAME_VALUE_PAIR_BIT_SET);
    if (nodes.length == 1) {
      ASTNode node = nodes[0];
      if (node instanceof PsiNameValuePair) {
        PsiNameValuePair pair = (PsiNameValuePair)node;
        if (pair.getName() == null) {
          PsiAnnotationMemberValue value = pair.getValue();
          if (value != null) {
            try {
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(getPsi().getProject());
              PsiAnnotation annotation = factory.createAnnotationFromText("@AAA(value = " + value.getText() + ")", null);
              replaceChild(node, annotation.getParameterList().getAttributes()[0].getNode());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }

    if (anchor == null) {
      if (before == null) before = Boolean.TRUE;
      anchor = findChildByType(before ? JavaTokenType.RPARENTH : JavaTokenType.LPARENTH);
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (first.getElementType() == JavaElementType.NAME_VALUE_PAIR && last.getElementType() == JavaElementType.NAME_VALUE_PAIR) {
      JavaSourceUtil.addSeparatingComma(this, first, NAME_VALUE_PAIR_BIT_SET);
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.NAME_VALUE_PAIR) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }
}
