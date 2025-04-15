// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public final class PrattExpressionParser extends BasicPrattExpressionParser {
  public PrattExpressionParser(@NotNull JavaParser parser) {
    super(parser);
  }
}
