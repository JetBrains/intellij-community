// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ForAscendingPostfixTemplate extends ForIndexedPostfixTemplate {
  public ForAscendingPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("fori", "for ($type$ $index$ = 0; $index$ < $bound$; $index$++) {\n$END$\n}",
          "for (int i = 0; i < expr.length; i++)", provider);
  }
}