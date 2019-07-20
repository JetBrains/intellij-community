/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Defines whether or not some elements can be indented when a user selects a fragment of text and invokes "indent" action (normally by
 * pressing [TAB]). The elements which are said to be unmovable ({@link #canIndent(int, int, PsiElement)} returns {@code false}) do not change their indentation.
 * This may be useful for cases like HEREDOC text handling.
 *
 * @author Rustam Vishnyakov
 */
public interface IndentStrategy {
  /**
   * @deprecated use {@link #canIndent(int, int, PsiElement)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  default boolean canIndent(@SuppressWarnings("unused") @NotNull PsiElement element) {
    return true;
  }

  /**
   * Checks if an element can be indented.
   * @param indentationStartOffset The start offset of the whole indentation.
   * @param indentationEndOffset The end offset of the whole indentation.
   * @param element The element to check.
   * @return True if the element can change its indentation, false if the indentation must be preserved.
   */
  default boolean canIndent(int indentationStartOffset, int indentationEndOffset, @NotNull PsiElement element) {
    return canIndent(element);
  }
}
