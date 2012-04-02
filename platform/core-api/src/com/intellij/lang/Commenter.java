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

import org.jetbrains.annotations.Nullable;

/**
 * Defines the support for "Comment with Line Comment" and "Comment with Block Comment"
 * actions in a custom language.
 * @author max
 * @see LanguageCommenters
 */
public interface Commenter {
  /**
   * Returns the string which prefixes a line comment in the language, or null if the language
   * does not support line comments.
   * @return the line comment text, or null.
   */
  @Nullable
  String getLineCommentPrefix();

  /**
   * Returns the string which marks the beginning of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the block comment start text, or null.
   */
  @Nullable
  String getBlockCommentPrefix();

  /**
   * Returns the string which marks the end of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the block comment end text, or null.
   */
  @Nullable
  String getBlockCommentSuffix();

  /**
   * Returns the string which marks the commented beginning of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the commented block comment start text, or null.
   */
  @Nullable
  String getCommentedBlockCommentPrefix();

  /**
   * Returns the string which marks the commented end of a block comment in the language,
   * or null if the language does not support block comments.
   * @return the commented block comment end text, or null.
   */
  @Nullable
  String getCommentedBlockCommentSuffix();
}
