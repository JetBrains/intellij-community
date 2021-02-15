// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MethodReferenceElementType extends FunctionalExpressionElementType<PsiMethodReferenceExpression> {
  //prevents cyclic static variables initialization
  private final static NotNullLazyValue<TokenSet> EXCLUDE_FROM_PRESENTABLE_TEXT = NotNullLazyValue.lazy(() -> {
    return TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.REFERENCE_PARAMETER_LIST));
  });

  public MethodReferenceElementType() {
    super("METHOD_REF_EXPRESSION");
  }

  @Override
  public PsiMethodReferenceExpression createPsi(@NotNull ASTNode node) {
    return new PsiMethodReferenceExpressionImpl(node);
  }

  @Override
  public PsiMethodReferenceExpression createPsi(@NotNull FunctionalExpressionStub<PsiMethodReferenceExpression> stub) {
    return new PsiMethodReferenceExpressionImpl(stub);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this) {
      @Override
      public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
        super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
      }

      @Override
      public int getChildRole(@NotNull ASTNode child) {
        IElementType elType = child.getElementType();
        if (elType == JavaTokenType.DOUBLE_COLON) return ChildRole.DOUBLE_COLON;
        if (elType == JavaTokenType.IDENTIFIER) return ChildRole.REFERENCE_NAME;
        if (elType == JavaElementType.REFERENCE_EXPRESSION) return ChildRole.CLASS_REFERENCE;
        return ChildRole.EXPRESSION;
      }
    };
  }

  @NotNull
  @Override
  protected String getPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr) {
    return LightTreeUtil.toFilteredString(tree, funExpr, EXCLUDE_FROM_PRESENTABLE_TEXT.getValue());
  }
}