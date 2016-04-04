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

import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Defines support for "Enter within comments" actions in a custom language.
 * @author Maxim.Mossienko
 * @see LanguageCommenters
 */
public interface CodeDocumentationAwareCommenter extends Commenter {
  /**
   * Returns the type of the line comment in the language,
   * or null if the language does not support line comments.
   * It is assumed that line comment prefix is not null when line comment type is not null.
   * @return the line comment type.
   */
  @Nullable
  IElementType getLineCommentTokenType();

  /**
   * Returns the type of the block comment in the language,
   * or null if the language does not support block comments.
   * It is assumed that block comment prefix is not null when block comment type is not null.
   * @return the block comment type.
   */
  @Nullable
  IElementType getBlockCommentTokenType();

  /**
   * Returns the type of the documentation comment token in the language,
   * or null if the language does not support documentation comments.
   * It is assumed that documentation comment prefix is not null when documentation comment type is not null.
   * @return the documentation comment type.
   */
  @Nullable
  IElementType getDocumentationCommentTokenType();

  /**
   * Returns the string which starts documentation comment in the language, or null if the language
   * does not support documentation comments.
   * @return the documentation comment text, or null.
   */
  @Nullable String getDocumentationCommentPrefix();

  /**
   * Returns the string which prefixes documentation line comment in the language, or null if the language
   * does not support documentation comments.
   * @return the line comment text, or null.
   */
  @Nullable String getDocumentationCommentLinePrefix();

  /**
   * Returns the string which ends documentation comment in the language, or null if the language
   * does not support documentation comments.
   * @return the documentation comment end text, or null.
   */
  @Nullable String getDocumentationCommentSuffix();

  boolean isDocumentationComment(PsiComment element);
}
