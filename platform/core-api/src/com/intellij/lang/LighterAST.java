// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Abstract syntax tree built up from light nodes.
 */
public abstract class LighterAST {
  private final CharTable myCharTable;

  public LighterAST(@NotNull CharTable charTable) {
    myCharTable = charTable;
  }

  public @NotNull CharTable getCharTable() {
    return myCharTable;
  }

  public abstract @NotNull LighterASTNode getRoot();

  public abstract @Nullable LighterASTNode getParent(final @NotNull LighterASTNode node);

  public abstract @Unmodifiable @NotNull List<LighterASTNode> getChildren(final @NotNull LighterASTNode parent);
}