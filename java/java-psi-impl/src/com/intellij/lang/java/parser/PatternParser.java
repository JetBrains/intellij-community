// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class PatternParser extends BasicPatternParser {
  public PatternParser(@NotNull JavaParser javaParser) {
    super(javaParser);
  }

  @Override
  public boolean isPattern(PsiBuilder builder) {
    return super.isPattern(builder);
  }
}
