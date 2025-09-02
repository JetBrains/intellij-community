// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class LambdaExpressionElementType extends JavaStubElementType implements ICompositeElementType, ParentProviderElementType {
  public LambdaExpressionElementType() {
    super("LAMBDA_EXPRESSION");
  }


  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(BasicJavaElementType.BASIC_LAMBDA_EXPRESSION);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
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
}
