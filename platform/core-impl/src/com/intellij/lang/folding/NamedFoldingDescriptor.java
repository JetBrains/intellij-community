// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @deprecated Use {@link FoldingDescriptor} instead.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public final class NamedFoldingDescriptor extends FoldingDescriptor {
  /**
   * @deprecated Use {@link FoldingDescriptor#FoldingDescriptor(ASTNode, TextRange, FoldingGroup, String)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public NamedFoldingDescriptor(@NotNull ASTNode node,
                                final @NotNull TextRange range,
                                @Nullable FoldingGroup group,
                                @NotNull String placeholderText) {
    super(node, range, group, placeholderText);
  }
}
