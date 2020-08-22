// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

/**
 * @author yole
 */
public final class LanguageBraceMatching extends LanguageExtension<PairedBraceMatcher> {
  public static final LanguageBraceMatching INSTANCE = new LanguageBraceMatching();

  private LanguageBraceMatching() {
    super("com.intellij.lang.braceMatcher");
  }
}