// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class MethodReferenceElementType extends JavaStubElementType implements ICompositeElementType {
  public MethodReferenceElementType() {
    super("METHOD_REF_EXPRESSION");
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
        if (elType == JavaTokenType.DOUBLE_COLON) return ChildRole.DOUBLE_COLON;
        if (elType == JavaTokenType.IDENTIFIER) return ChildRole.REFERENCE_NAME;
        if (elType == JavaElementType.REFERENCE_EXPRESSION) return ChildRole.CLASS_REFERENCE;
        return ChildRole.EXPRESSION;
      }
    };
  }
}