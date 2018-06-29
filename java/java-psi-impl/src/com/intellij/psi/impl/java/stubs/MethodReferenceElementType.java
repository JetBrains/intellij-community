/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MethodReferenceElementType extends FunctionalExpressionElementType<PsiMethodReferenceExpression> {
  //prevents cyclic static variables initialization
  private final static NotNullLazyValue<TokenSet> EXCLUDE_FROM_PRESENTABLE_TEXT = new NotNullLazyValue<TokenSet>() {
    @NotNull
    @Override
    protected TokenSet compute() {
      return TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.REFERENCE_PARAMETER_LIST));
    }
  };

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
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()) &&
            ElementType.EXPRESSION_BIT_SET.contains(newElement.getElementType())) {
          boolean needParenth = ReplaceExpressionUtil.isNeedParenthesis(child, newElement);
          if (needParenth) {
            newElement = JavaSourceUtil.addParenthToReplacedChild(JavaElementType.PARENTH_EXPRESSION, newElement, getManager());
          }
        }
        super.replaceChildInternal(child, newElement);
      }


      @Override
      public int getChildRole(@NotNull ASTNode child) {
        final IElementType elType = child.getElementType();
        if (elType == JavaTokenType.DOUBLE_COLON) {
          return ChildRole.DOUBLE_COLON;
        } else if (elType == JavaTokenType.IDENTIFIER) {
          return ChildRole.REFERENCE_NAME;
        } else if (elType == JavaElementType.REFERENCE_EXPRESSION) {
          return ChildRole.CLASS_REFERENCE;
        }
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
