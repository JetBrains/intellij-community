// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class DefaultRoleFinder implements RoleFinder {

  private final Supplier<? extends IElementType[]> myComputable;

  public DefaultRoleFinder(IElementType... elementTypes) {
    myComputable = () -> elementTypes;
  }

  public DefaultRoleFinder(Supplier<? extends IElementType[]> computable) {
    myComputable = computable;
  }

  @Override
  public ASTNode findChild(@NotNull ASTNode parent) {
    ASTNode current = parent.getFirstChildNode();
    while (current != null) {
      if (ArrayUtil.indexOfIdentity(myComputable.get(), current.getElementType()) != -1) {
        return current;
      }
      current = current.getTreeNext();
    }
    return null;
  }
}
