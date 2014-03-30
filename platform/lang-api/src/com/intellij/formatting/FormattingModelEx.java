/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
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
