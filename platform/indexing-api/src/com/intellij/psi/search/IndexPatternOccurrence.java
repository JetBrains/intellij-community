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
package com.intellij.psi.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the occurrence of an index pattern in the comments of a source code file.
 *
 * @author yole
 * @since 5.1
 * @see com.intellij.psi.search.searches.IndexPatternSearch
 * @see IndexPatternProvider
 */
public interface IndexPatternOccurrence {
  /**
   * Returns the file in which the occurrence was found.
   *
   * @return the file in which the occurrence was found.
   */
  @NotNull PsiFile getFile();

  /**
   * Returns the text range which was matched by the pattern.
   *
   * @return the text range which was matched by the pattern.
   */
  @NotNull TextRange getTextRange();

  /**
   * Returns the instance of the pattern which was matched.
   *
   * @return the instance of the pattern which was matched.
   */
  @NotNull IndexPattern getPattern();
}
