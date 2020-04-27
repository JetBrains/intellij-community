// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ExpressionPsiElement extends CompositePsiElement {
  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod") private final int myHC = CompositePsiElement.ourHC++;

  public ExpressionPsiElement(final IElementType type) {
    super(type);
  }

  @Override
  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
  }

  @Override
  public final int hashCode() {
    return myHC;
  }
}