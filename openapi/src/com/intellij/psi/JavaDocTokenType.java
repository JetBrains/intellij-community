/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
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
  IElementType DOC_TAG_VALUE_SHARP_TOKEN = new IJavaDocElementType("DOC_TAG_VALUE_SHARP_TOKEN");

  IElementType DOC_COMMENT_BAD_CHARACTER = new IJavaDocElementType("DOC_COMMENT_BAD_CHARACTER");
  IElementType DOC_REFERENCE_TEXT = new IJavaDocElementType("DOC_REFERENCE_TEXT");
  IElementType DOC_TYPE_TEXT = new IJavaDocElementType("DOC_TYPE_TEXT");
}