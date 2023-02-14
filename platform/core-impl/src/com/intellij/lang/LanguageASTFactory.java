// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

/**
 * @see ASTFactory
 */
public final class LanguageASTFactory extends LanguageExtension<ASTFactory> {
  public static final LanguageASTFactory INSTANCE = new LanguageASTFactory();

  private LanguageASTFactory() {
    super("com.intellij.lang.ast.factory", ASTFactory.DefaultFactoryHolder.DEFAULT);
  }
}
