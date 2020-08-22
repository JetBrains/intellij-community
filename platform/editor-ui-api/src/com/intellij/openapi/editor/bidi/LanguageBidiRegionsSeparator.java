// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.bidi;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @see BidiRegionsSeparator
 */
public final class LanguageBidiRegionsSeparator extends LanguageExtension<BidiRegionsSeparator> {
  public static final LanguageBidiRegionsSeparator INSTANCE = new LanguageBidiRegionsSeparator();

  private LanguageBidiRegionsSeparator() {
    super("com.intellij.bidiRegionsSeparator", new BidiRegionsSeparator() {
      @Override
      public boolean createBorderBetweenTokens(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
        return true;
      }
    });
  }
}
