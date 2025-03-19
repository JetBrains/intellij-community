// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.bidi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Plain text bidi layout strategy: ignore any tokens, perform bidi layout on the whole line.
 */
@ApiStatus.Internal
public final class PlainTextBidiRegionsSeparator extends BidiRegionsSeparator {
  @Override
  public boolean createBorderBetweenTokens(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
    return false;
  }
}
