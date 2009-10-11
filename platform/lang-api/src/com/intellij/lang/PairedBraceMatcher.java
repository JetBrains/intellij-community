/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the brace matching support required for a custom language. For paired
 * brace matching to work, the language must also provide a
 * {@link com.intellij.openapi.fileTypes.SyntaxHighlighter} and return the correct
 * lexer from <code>getHighlightingLexer()</code>.
 *
 * @author max
 * @see LanguageBraceMatching
 * @see BracePair
 */
public interface PairedBraceMatcher {
  /**
   * Returns the array of definitions for brace pairs that need to be matched when
   * editing code in the language.
   *
   * @return the array of brace pair definitions.
   */
  BracePair[] getPairs();

  /**
   * Returns true if paired rbrace should be inserted after lbrace of given type when lbrace is encountered before contextType token.
   * It is safe to always return true, then paired brace will be inserted anyway.
   * @param lbraceType lbrace for which information is queried
   * @param contextType token type that follows lbrace
   * @return true / false as described
   */
  boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType);

  /**
   * Returns the start offset of the code construct which owns the opening structural brace at the specified offset. For example,
   * if the opening brace belongs to an 'if' statement, returns the start offset of the 'if' statement.
   *
   * @param file the file in which brace matching is performed.
   * @param openingBraceOffset the offset of an opening structural brace.
   * @return the offset of corresponding code construct, or the same offset if not defined.
   */
  int getCodeConstructStart(final PsiFile file, int openingBraceOffset);
}
