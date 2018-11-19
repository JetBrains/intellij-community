// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiBreakStatementImpl extends CompositePsiElement implements PsiBreakStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiBreakStatementImpl");

  public PsiBreakStatementImpl() {
    super(BREAK_STATEMENT);
  }

  @Override
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL);
  }

  @Override
  public PsiStatement findExitedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label == null) {
      for (ASTNode parent = getTreeParent(); parent != null; parent = parent.getTreeParent()) {
        IElementType i = parent.getElementType();
        if (i == FOR_STATEMENT || i == WHILE_STATEMENT || i == DO_WHILE_STATEMENT || i == SWITCH_STATEMENT || i == FOREACH_STATEMENT) {
          return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(parent);
        }
        else if (i == METHOD || i == CLASS_INITIALIZER) {
          return null; // do not pass through anonymous/local class
        }
      }
    }
    else {
      String labelName = label.getText();
      for (CompositeElement parent = getTreeParent(); parent != null; parent = parent.getTreeParent()) {
        IElementType i = parent.getElementType();
        if (i == LABELED_STATEMENT) {
          ASTNode statementLabel = parent.findChildByRole(ChildRole.LABEL_NAME);
          if (statementLabel != null && statementLabel.getText().equals(labelName)) {
            return SourceTreeToPsiMap.<PsiLabeledStatement>treeToPsiNotNull(parent).getStatement();
          }
        }
        if (i == METHOD || i == CLASS_INITIALIZER || i == LAMBDA_EXPRESSION) {
          return null; // do not pass through anonymous/local class
        }
      }
    }
    return null;
  }

  @Override
  public PsiReference getReference() {
    PsiIdentifier label = getLabelIdentifier();
    return label != null ? new PsiLabelReference(this, label) : null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.BREAK_KEYWORD: return findChildByType(BREAK_KEYWORD);
      case ChildRole.LABEL: return findChildByType(IDENTIFIER);
      case ChildRole.CLOSING_SEMICOLON: return TreeUtil.findChildBackward(this, SEMICOLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == BREAK_KEYWORD) return ChildRole.BREAK_KEYWORD;
    if (i == IDENTIFIER) return ChildRole.LABEL;
    if (i == SEMICOLON) return ChildRole.CLOSING_SEMICOLON;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitBreakStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiBreakStatement";
  }
}