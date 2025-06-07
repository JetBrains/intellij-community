// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Internal interface for creating indent instances.
 * <p/>
 * Methods of this interface define contract for implementing {@link Indent} factory methods, so, feel free to check
 * their contracts.
 */
@ApiStatus.Internal
public interface IndentFactory {
  Indent getNormalIndent(boolean relativeToDirectParent);

  Indent getNoneIndent();

  Indent getAbsoluteNoneIndent();

  Indent getAbsoluteLabelIndent();

  Indent getLabelIndent();

  Indent getContinuationIndent(boolean relativeToDirectParent);

  Indent getContinuationWithoutFirstIndent(boolean relativeToDirectParent);

  Indent getSpaceIndent(final int spaces, boolean relativeToDirectParent);

  Indent getIndent(@NotNull Indent.Type type, boolean relativeToDirectParent, boolean enforceIndentToChildren);

  Indent getIndent(@NotNull Indent.Type type, int spaces, boolean relativeToDirectParent, boolean enforceIndentToChildren);

  @ApiStatus.Experimental
  Indent getIndentEnforcedToChildrenToBeRelativeToMe(@NotNull Indent.Type type, int spaces);

  Indent getSmartIndent(@NotNull Indent.Type type);

  Indent getSmartIndent(@NotNull Indent.Type type, boolean relativeToDirectParent);
}
