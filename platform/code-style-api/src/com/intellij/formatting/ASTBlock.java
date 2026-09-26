// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;


public interface ASTBlock extends Block {
  @Nullable
  ASTNode getNode();

  /**
   * @return {@link ASTNode} from this {@code block} if it's {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  static @Nullable ASTNode getNode(@Nullable Block block) {
    return block instanceof ASTBlock ? ((ASTBlock)block).getNode() : null;
  }

  /**
   * @return element type of the {@link ASTNode} contained in the {@code block}, if it's an {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  static @Nullable IElementType getElementType(@Nullable Block block) {
    ASTNode node = getNode(block);
    return node == null ? null : node.getElementType();
  }

  /**
   * @return {@link PsiElement} from {@link ASTNode} from this {@code block} if it's {@link ASTBlock}, null otherwise
   */
  @Contract("null -> null")
  static @Nullable PsiElement getPsiElement(@Nullable Block block) {
    ASTNode obj = getNode(block);
    return obj == null ? null : obj.getPsi();
  }
}
