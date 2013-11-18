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
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;

/**
 * Represents a comment in Java code or in a custom language.
 */
public interface PsiComment extends PsiElement {
  /**
   * Returns the token type of the comment (for example, {@link JavaTokenType#END_OF_LINE_COMMENT} or
   * {@link JavaTokenType#C_STYLE_COMMENT}).
   *
   * @return the token type of the comment.
   */
  IElementType getTokenType();
}
