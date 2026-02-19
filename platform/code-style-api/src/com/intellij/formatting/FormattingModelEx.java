// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;


public interface FormattingModelEx extends FormattingModel {
  /**
   * Replaces the contents of the specified text range in the document with the specified text
   * string consisting of whitespace characters. If necessary, other characters may be inserted
   * in addition to the passed whitespace (for example, \ characters for breaking lines in
   * languages like Python).
   *
   * @param textRange  the text range to replace with whitespace.
   * @param nodeAfter the AST node following the whitespace, if known
   * @param whiteSpace the whitespace to replace with.
   * @return new white space text range
   */
  TextRange replaceWhiteSpace(TextRange textRange, @Nullable ASTNode nodeAfter, String whiteSpace);
}
