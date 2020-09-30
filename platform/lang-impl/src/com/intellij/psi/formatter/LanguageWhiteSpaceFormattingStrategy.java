// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import com.intellij.lang.LanguageExtension;

/**
 * Exposes pre-configured {@link WhiteSpaceFormattingStrategy} objects to use in a per-language manner.
 *
 * @author Denis Zhdanov
 */
public final class LanguageWhiteSpaceFormattingStrategy extends LanguageExtension<WhiteSpaceFormattingStrategy> {

  public static final String EP_NAME = "com.intellij.lang.whiteSpaceFormattingStrategy";
  public static final LanguageWhiteSpaceFormattingStrategy INSTANCE = new LanguageWhiteSpaceFormattingStrategy();

  private LanguageWhiteSpaceFormattingStrategy() {
    super(EP_NAME);
  }
}
