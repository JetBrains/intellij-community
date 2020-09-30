// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

public final class LanguageExpressionTypes extends LanguageExtension<ExpressionTypeProvider> {
  public static final LanguageExpressionTypes INSTANCE = new LanguageExpressionTypes();

  private LanguageExpressionTypes() {
    super("com.intellij.codeInsight.typeInfo");
  }
}