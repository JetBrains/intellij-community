// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiLambdaExpressionImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class LambdaExpressionElementType extends FunctionalExpressionElementType<PsiLambdaExpression> {
  public LambdaExpressionElementType() {
    super("LAMBDA_EXPRESSION", BasicJavaElementType.BASIC_LAMBDA_EXPRESSION);
  }

  @Override
  public PsiLambdaExpression createPsi(@NotNull ASTNode node) {
    return new PsiLambdaExpressionImpl(node);
  }

  @Override
  public PsiLambdaExpression createPsi(@NotNull FunctionalExpressionStub<PsiLambdaExpression> stub) {
    return new PsiLambdaExpressionImpl(stub);
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
        if (elType == JavaTokenType.ARROW) return ChildRole.ARROW;
        if (elType == JavaElementType.PARAMETER_LIST) return ChildRole.PARAMETER_LIST;
        if (elType == JavaElementType.CODE_BLOCK) return ChildRole.LBRACE;
        return ChildRole.EXPRESSION;
      }
    };
  }

  @NotNull
  @Override
  protected String getPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr) {
    LighterASTNode parameterList =
      Objects.requireNonNull(LightTreeUtil.firstChildOfType(tree, funExpr, JavaStubElementTypes.PARAMETER_LIST));
    return getLambdaPresentableText(tree, parameterList);
  }

  private static String getLambdaPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode parameterList) {
    StringBuilder buf = new StringBuilder(parameterList.getEndOffset() - parameterList.getStartOffset());
    formatParameterList(tree, parameterList, buf);
    buf.append(" -> {...}");
    return buf.toString();
  }

  private static void formatParameterList(@NotNull LighterAST tree, @NotNull LighterASTNode parameterList, StringBuilder buf) {
    final List<LighterASTNode> children = tree.getChildren(parameterList);
    boolean isFirstParameter = true;
    boolean appendCloseBracket = false;
    for (final LighterASTNode node : children) {
      final IElementType tokenType = node.getTokenType();
      if (tokenType == JavaTokenType.LPARENTH) {
        buf.append('(');
        appendCloseBracket = true;
      }
      else if (tokenType == JavaStubElementTypes.PARAMETER) {
        if (!isFirstParameter) {
          buf.append(", ");
        }
        formatParameter(tree, node, buf);
        if (isFirstParameter) {
          isFirstParameter = false;
        }
      }
    }
    if (appendCloseBracket) buf.append(')');
  }

  private static void formatParameter(@NotNull LighterAST tree, @NotNull LighterASTNode parameter, StringBuilder buf) {
    final List<LighterASTNode> children = tree.getChildren(parameter);
    for (LighterASTNode node : children) {
      final IElementType tokenType = node.getTokenType();
      if (tokenType == JavaElementType.TYPE) {
        formatType(tree, node, buf);
        buf.append(' ');
      }
      else if (tokenType == JavaTokenType.IDENTIFIER) {
        buf.append(RecordUtil.intern(tree.getCharTable(), node));
      }
    }
  }

  private static void formatType(LighterAST tree, LighterASTNode typeElement, StringBuilder buf) {
    for (LighterASTNode node : tree.getChildren(typeElement)) {
      final IElementType tokenType = node.getTokenType();
      if (tokenType == JavaElementType.JAVA_CODE_REFERENCE) {
        formatCodeReference(tree, node, buf);
      }
      else if (tokenType == JavaElementType.TYPE) {
        formatType(tree, node, buf);
      }
      else if (tokenType == JavaTokenType.QUEST) {
        buf.append("? ");
      }
      else if (ElementType.KEYWORD_BIT_SET.contains(tokenType)) {
        buf.append(RecordUtil.intern(tree.getCharTable(), node));
        if (!ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
          buf.append(" ");
        }
      }
      else if (tokenType == JavaTokenType.ELLIPSIS) {
        buf.append("...");
      }
      else if (tokenType == JavaTokenType.RBRACKET) {
        buf.append("]");
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        buf.append("[");
      }
    }
  }

  private static void formatCodeReference(LighterAST tree, LighterASTNode codeRef, StringBuilder buf) {
    for (LighterASTNode node : tree.getChildren(codeRef)) {
      final IElementType tokenType = node.getTokenType();
      if (tokenType == JavaTokenType.IDENTIFIER) {
        buf.append(RecordUtil.intern(tree.getCharTable(), node));
      }
      else if (tokenType == JavaElementType.REFERENCE_PARAMETER_LIST) {
        formatTypeParameters(tree, node, buf);
      }
    }
  }

  private static void formatTypeParameters(LighterAST tree, LighterASTNode typeParameters, StringBuilder buf) {
    final List<LighterASTNode> children = LightTreeUtil.getChildrenOfType(tree, typeParameters, JavaElementType.TYPE);
    if (children.isEmpty()) return;
    buf.append('<');
    for (int i = 0; i < children.size(); i++) {
      LighterASTNode child = children.get(i);
      formatType(tree, child, buf);
      if (i != children.size() - 1) {
        buf.append(", ");
      }
    }
    buf.append('>');
  }
}
