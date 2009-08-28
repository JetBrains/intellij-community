package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RoleFinder {
  @Nullable
  ASTNode findChild(@NotNull ASTNode parent);
}
