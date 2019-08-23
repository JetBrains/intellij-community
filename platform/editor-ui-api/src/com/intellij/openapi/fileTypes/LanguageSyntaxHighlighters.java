// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.LanguageExtension;

public class LanguageSyntaxHighlighters extends LanguageExtension<SyntaxHighlighter> {
  private LanguageSyntaxHighlighters() {
    super("com.intellij.lang.syntaxHighlighter");
  }

  public static final LanguageSyntaxHighlighters INSTANCE = new LanguageSyntaxHighlighters();
}
