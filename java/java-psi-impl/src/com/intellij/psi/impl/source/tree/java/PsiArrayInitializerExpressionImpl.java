/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class PsiArrayInitializerExpressionImpl extends ExpressionPsiElement implements PsiArrayInitializerExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl");

  public PsiArrayInitializerExpressionImpl() {
    super(JavaElementType.ARRAY_INITIALIZER_EXPRESSION);
  }

  @Override
  @NotNull
  public PsiExpression[] getInitializers(){
    return getChildrenAsPsiElements(ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
  }

  @Override
  public PsiType getType(){
    if (getTreeParent() instanceof PsiNewExpression){
      if (getTreeParent().getChildRole(this) == ChildRole.ARRAY_INITIALIZER){
        return ((PsiNewExpression)getTreeParent()).getType();
      }
    }
    else if (getTreeParent() instanceof PsiVariable){
      return ((PsiVariable)getTreeParent()).getType();
    }
    else if (getTreeParent() instanceof PsiArrayInitializerExpression){
      PsiType parentType = ((PsiArrayInitializerExpression)getTreeParent()).getType();
      if (!(parentType instanceof PsiArrayType)) return null;
      final PsiType componentType = ((PsiArrayType)parentType).getComponentType();
      return componentType instanceof PsiArrayType ? componentType : null;
    }
    else if (getTreeParent() instanceof FieldElement){
      return ((PsiField)getParent()).getType();
    }

    return null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LBRACE) {
      return ChildRole.LBRACE;
    }
    else if (i == JavaTokenType.RBRACE) {
      return ChildRole.RBRACE;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitArrayInitializerExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiArrayInitializerExpression:" + getText();
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    final TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (ElementType.EXPRESSION_BIT_SET.contains(first.getElementType())) {
     final CharTable charTab = SharedImplUtil.findCharTableByTree(this);
      for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, charTab, getManager());
          super.addInternal(comma, comma, first, Boolean.FALSE);
          break;
        }
      }
      for (ASTNode child = first.getTreePrev(); child != null; child = child.getTreePrev()) {
        if (child.getElementType() == JavaTokenType.COMMA) break;
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
          TreeElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", 0, 1, charTab, getManager());
          super.addInternal(comma, comma, child, Boolean.FALSE);
          break;
        }
      }
    }

    return firstAdded;
  }
}
