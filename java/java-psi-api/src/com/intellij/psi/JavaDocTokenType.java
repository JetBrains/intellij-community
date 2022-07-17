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
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaDocElementType;


public interface JavaDocTokenType {
  IElementType DOC_COMMENT_START = new IJavaDocElementType("DOC_COMMENT_START");
  IElementType DOC_COMMENT_END = new IJavaDocElementType("DOC_COMMENT_END");
  IElementType DOC_COMMENT_DATA = new IJavaDocElementType("DOC_COMMENT_DATA");
  IElementType DOC_SPACE = new IJavaDocElementType("DOC_SPACE");
  IElementType DOC_COMMENT_LEADING_ASTERISKS = new IJavaDocElementType("DOC_COMMENT_LEADING_ASTERISKS");
  IElementType DOC_TAG_NAME = new IJavaDocElementType("DOC_TAG_NAME");
  IElementType DOC_INLINE_TAG_START = new IJavaDocElementType("DOC_INLINE_TAG_START");
  IElementType DOC_INLINE_TAG_END = new IJavaDocElementType("DOC_INLINE_TAG_END");

  IElementType DOC_TAG_VALUE_TOKEN = new IJavaDocElementType("DOC_TAG_VALUE_TOKEN");
  IElementType DOC_TAG_VALUE_DOT = new IJavaDocElementType("DOC_TAG_VALUE_DOT");
  IElementType DOC_TAG_VALUE_COMMA = new IJavaDocElementType("DOC_TAG_VALUE_COMMA");
  IElementType DOC_TAG_VALUE_LPAREN = new IJavaDocElementType("DOC_TAG_VALUE_LPAREN");
  IElementType DOC_TAG_VALUE_RPAREN = new IJavaDocElementType("DOC_TAG_VALUE_RPAREN");
  IElementType DOC_TAG_VALUE_QUOTE = new IJavaDocElementType("DOC_TAG_VALUE_QUOTE");
  IElementType DOC_TAG_VALUE_COLON = new IJavaDocElementType("DOC_TAG_VALUE_COLON");
  IElementType DOC_TAG_ATTRIBUTE_NAME = new IJavaDocElementType("DOC_TAG_ATTRIBUTE_NAME");
  IElementType DOC_TAG_ATTRIBUTE_VALUE = new IJavaDocElementType("DOC_TAG_ATTRIBUTE_VALUE");
  IElementType DOC_TAG_VALUE_LT = new IJavaDocElementType("DOC_TAG_VALUE_LT");
  IElementType DOC_TAG_VALUE_GT = new IJavaDocElementType("DOC_TAG_VALUE_GT");
  IElementType DOC_TAG_VALUE_SHARP_TOKEN = new IJavaDocElementType("DOC_TAG_VALUE_SHARP_TOKEN");

  IElementType DOC_COMMENT_BAD_CHARACTER = new IJavaDocElementType("DOC_COMMENT_BAD_CHARACTER");

  TokenSet ALL_JAVADOC_TOKENS = TokenSet.create(
   DOC_COMMENT_START, DOC_COMMENT_END, DOC_COMMENT_DATA, DOC_SPACE, DOC_COMMENT_LEADING_ASTERISKS, DOC_TAG_NAME,
   DOC_INLINE_TAG_START, DOC_INLINE_TAG_END, DOC_TAG_VALUE_TOKEN, DOC_TAG_VALUE_DOT, DOC_TAG_VALUE_COMMA,
   DOC_TAG_VALUE_LPAREN, DOC_TAG_VALUE_RPAREN, DOC_TAG_VALUE_SHARP_TOKEN, DOC_TAG_VALUE_QUOTE, DOC_TAG_VALUE_COLON,
   DOC_TAG_ATTRIBUTE_NAME, DOC_TAG_ATTRIBUTE_VALUE
  );
}