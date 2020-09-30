// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

public final class LanguageParserDefinitions extends LanguageExtension<ParserDefinition> {
  public static final LanguageParserDefinitions INSTANCE = new LanguageParserDefinitions();

  private LanguageParserDefinitions() {
    super("com.intellij.lang.parserDefinition");
  }
}