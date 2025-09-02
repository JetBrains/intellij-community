// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class FileParser extends BasicFileParser {

  public FileParser(@NotNull JavaParser javaParser) {
    super(javaParser);
  }
}