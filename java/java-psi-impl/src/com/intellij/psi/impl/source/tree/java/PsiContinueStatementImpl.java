// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiLabelReference;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiContinueStatementImpl extends CompositePsiElement implements PsiContinueStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiContinueStatementImpl");

  public PsiContinueStatementImpl() {
    super(CONTINUE_STATEMENT);
  }

  @Override
  public PsiIdentifier getLabelIdentifier() {
    return (PsiIdentifier)findChildByRoleAsPsiElement(ChildRole.LABEL);
  }

  @Override
  public PsiStatement findContinuedStatement() {
    PsiIdentifier label = getLabelIdentifier();
    if (label == null){
      for(ASTNode parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        IElementType i = parent.getElementType();
        if (i == FOR_STATEMENT || i == FOREACH_STATEMENT || i == WHILE_STATEMENT || i == DO_WHILE_STATEMENT) {
          return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(parent);
        }
        if (i == METHOD || i == CLASS_INITIALIZER) {
          return null;
        }
      }
    }
    else{
      String labelName = label.getText();
      for(CompositeElement parent = getTreeParent(); parent != null; parent = parent.getTreeParent()){
        if (parent.getElementType() == LABELED_STATEMENT){
          TreeElement statementLabel = (TreeElement)parent.findChildByRole(ChildRole.LABEL_NAME);
          if (statementLabel.textMatches(labelName)){
            return ((PsiLabeledStatement)SourceTreeToPsiMap.treeElementToPsi(parent)).getStatement();
          }
        }
        if (parent.getElementType() == METHOD || parent.getElementType() == CLASS_INITIALIZER || parent.getElementType() == LAMBDA_EXPRESSION) return null; // do not pass through anonymous/local class
      }
    }
    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.CONTINUE_KEYWORD: return findChildByType(JavaTokenType.CONTINUE_KEYWORD);
      case ChildRole.LABEL: return findChildByType(JavaTokenType.IDENTIFIER);
      case ChildRole.CLOSING_SEMICOLON: return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.CONTINUE_KEYWORD) return ChildRole.CONTINUE_KEYWORD;
    if (i == JavaTokenType.IDENTIFIER) return ChildRole.LABEL;
    if (i == JavaTokenType.SEMICOLON) return ChildRole.CLOSING_SEMICOLON;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitContinueStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiReference getReference() {
    PsiIdentifier label = getLabelIdentifier();
    return label != null ? new PsiLabelReference(this, label) : null;
  }

  @Override
  public String toString() {
    return "PsiContinueStatement";
  }
}