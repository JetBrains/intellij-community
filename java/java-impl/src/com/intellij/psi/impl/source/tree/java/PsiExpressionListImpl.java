package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.CharTable;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionListImpl extends CompositePsiElement implements PsiExpressionList, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl");

  public PsiExpressionListImpl() {
    super(EXPRESSION_LIST);
  }

  @NotNull
  public PsiExpression[] getExpressions() {
    return getChildrenAsPsiElements(EXPRESSION_BIT_SET, PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
  }

  @NotNull
  public PsiType[] getExpressionTypes() {
    return ContainerUtil.map2Array(getExpressions(), PsiType.class, new Function<PsiExpression, PsiType>() {
      public PsiType fun(final PsiExpression expression) {
        return expression.getType();
      }
    });
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        return getFirstChildNode() != null && getFirstChildNode().getElementType() == LPARENTH ? getFirstChildNode() : null;

      case ChildRole.RPARENTH:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == RPARENTH) {
          return getLastChildNode();
        }
        else {
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRoleBase.NONE;
    }
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(LPARENTH, "(", 0, 1, treeCharTab, getManager());
          super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(RPARENTH, ")", 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.RPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByRole(ChildRole.LPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(LPARENTH, "(", 0, 1, treeCharTab, getManager());
          super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(RPARENTH, ")", 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.LPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.FALSE;
      }
    }
    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (ElementType.EXPRESSION_BIT_SET.contains(first.getElementType())) {
      ASTNode element = first;
      for (ASTNode child = element.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, element, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = element.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(COMMA, ",", 0, 1, treeCharTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }
    return firstAdded;
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      ASTNode next = TreeUtil.skipElements(child.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (next != null && next.getElementType() == COMMA) {
        deleteChildInternal(next);
      }
      else {
        ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (prev != null && prev.getElementType() == COMMA) {
          deleteChildInternal(prev);
        }
      }
    }
    super.deleteChildInternal(child);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiExpressionList";
  }
}
