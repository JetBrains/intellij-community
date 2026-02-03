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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface if you want to enable word completion for specific element types.
 * Register as an extension point "com.intellij.codeInsight.wordCompletionFilter"
 *
 * @see com.intellij.lang.LanguageWordCompletion
 * @see com.intellij.codeInsight.completion.WordCompletionContributor
 */
public interface WordCompletionElementFilter {
  /**
   * @return true if word completion is enabled inside leaf tokens with the specified element type
   */
  boolean isWordCompletionEnabledIn(@NotNull IElementType element);

  /**
   * @return true if word completion is enabled in dumb mode
   */
  default boolean isWordCompletionInDumbModeEnabled() {
    return true;
  }
}