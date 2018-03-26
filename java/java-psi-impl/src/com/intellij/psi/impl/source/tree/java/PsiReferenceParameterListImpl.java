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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
public class PsiReferenceParameterListImpl extends CompositePsiElement implements PsiReferenceParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl");
  private static final TokenSet TYPE_SET = TokenSet.create(JavaElementType.TYPE);

  public PsiReferenceParameterListImpl() {
    super(JavaElementType.REFERENCE_PARAMETER_LIST);
  }

  @Override
  @NotNull
  public PsiTypeElement[] getTypeParameterElements() {
    return getChildrenAsPsiElements(JavaElementType.TYPE, PsiTypeElement.ARRAY_FACTORY);
  }

  @Override
  @NotNull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByReferenceParameterList(this);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    IElementType i = child.getElementType();
    if (i == JavaElementType.TYPE) {
      return ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (i == JavaTokenType.GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LT_IN_TYPE_LIST:
        if (getFirstChildNode() != null && getFirstChildNode().getElementType() == JavaTokenType.LT){
          return getFirstChildNode();
        }
        else{
          return null;
        }

      case ChildRole.GT_IN_TYPE_LIST:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == JavaTokenType.GT){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before){
    if (first == last && first.getElementType() == JavaElementType.TYPE){
      if (getLastChildNode() != null && getLastChildNode().getElementType() == TokenType.ERROR_ELEMENT){
        super.deleteChildInternal(getLastChildNode());
      }
    }

    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    if (getFirstChildNode()== null || getFirstChildNode().getElementType() != JavaTokenType.LT){
      TreeElement lt = Factory.createSingleLeafElement(JavaTokenType.LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }
    if (getLastChildNode() == null || getLastChildNode().getElementType() != JavaTokenType.GT){
      TreeElement gt = Factory.createSingleLeafElement(JavaTokenType.GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && first.getElementType() == JavaElementType.TYPE) {
      JavaSourceUtil.addSeparatingComma(this, first, TYPE_SET);
    }

    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.TYPE) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);

    if (getTypeParameterElements().length == 0){
      ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      if (lt != null){
        deleteChildInternal(lt);
      }

      ASTNode gt = findChildByRole(ChildRole.GT_IN_TYPE_LIST);
      if (gt != null){
        deleteChildInternal(gt);
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiReferenceParameterList";
  }
}
