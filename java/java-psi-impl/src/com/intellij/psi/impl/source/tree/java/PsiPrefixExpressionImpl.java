/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiPrefixExpressionImpl extends ExpressionPsiElement implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance(PsiPrefixExpressionImpl.class);

  public PsiPrefixExpressionImpl() {
    super(JavaElementType.PREFIX_EXPRESSION);
  }

  @Override
  public PsiExpression getOperand() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.OPERAND);
  }

  @Override
  @NotNull
  public PsiJavaToken getOperationSign() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN);
  }

  @Override
  @NotNull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  @Override
  public PsiType getType() {
    PsiExpression operand = getOperand();
    if (operand == null) return null;
    PsiType type = operand.getType();
    IElementType opCode = getOperationTokenType();
    if (opCode == JavaTokenType.PLUS || opCode == JavaTokenType.MINUS || opCode == JavaTokenType.TILDE) {
      if (type == null) return null;
      if (type instanceof PsiClassType) type = PsiPrimitiveType.getUnboxedType(type);
      return PsiTypes.byteType().equals(type) || PsiTypes.charType().equals(type) || PsiTypes.shortType().equals(type) ? PsiTypes.intType()
                                                                                                                       : type;
    }
    else if (opCode == JavaTokenType.PLUSPLUS || opCode == JavaTokenType.MINUSMINUS) {
      return type;
    }
    else if (opCode == JavaTokenType.EXCL) {
      return PsiTypes.booleanType();
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return getFirstChildNode();

      case ChildRole.OPERAND:
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child == getFirstChildNode()) return ChildRole.OPERATION_SIGN;
    if (child == getLastChildNode() && ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) return ChildRole.OPERAND;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPrefixExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent != null || !getOperationTokenType().equals(JavaTokenType.EXCL)) return true;
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    PatternResolveState hint = state.get(PatternResolveState.KEY);
    if (hint == null) return true;
    return PsiScopesUtil.walkChildrenScopes(this, processor, hint.invert().putInto(state), null, place);
  }

  @Override
  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }
}

