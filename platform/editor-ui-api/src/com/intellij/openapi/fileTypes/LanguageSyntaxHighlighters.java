// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;

public final class LanguageSyntaxHighlighters extends LanguageExtension<SyntaxHighlighter> {
  public static final ExtensionPointName<KeyedLazyInstance<SyntaxHighlighter>> EP_NAME = ExtensionPointName.create("com.intellij.lang.syntaxHighlighter");

  private LanguageSyntaxHighlighters() {
    super(EP_NAME);
  }

  public static final LanguageSyntaxHighlighters INSTANCE = new LanguageSyntaxHighlighters();
}
