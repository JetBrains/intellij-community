// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.element.JavaDocSyntaxElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.ApiStatus;

/**
 * @see JavaDocSyntaxElementType
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 * This class is planned to be removed.
 * Use {@link com.intellij.psi.JavaDocTokenType} directly.
 */
@Deprecated
@ApiStatus.ScheduledForRemoval
public interface BasicJavaDocElementType {
  IElementType BASIC_DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType BASIC_DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType BASIC_DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType BASIC_DOC_FRAGMENT_REF = new IJavaDocElementType("DOC_FRAGMENT_REF");
  IElementType BASIC_DOC_FRAGMENT_NAME = new IJavaDocElementType("DOC_FRAGMENT_NAME");
  IElementType BASIC_DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");
  IElementType BASIC_DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");
  IElementType BASIC_DOC_SNIPPET_TAG = new IJavaDocElementType("DOC_SNIPPET_TAG");
  IElementType BASIC_DOC_SNIPPET_TAG_VALUE = new IJavaDocElementType("DOC_SNIPPET_TAG_VALUE");
  IElementType BASIC_DOC_SNIPPET_BODY = new IJavaDocElementType("DOC_SNIPPET_BODY");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_LIST =
    new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_LIST");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_VALUE");

  IElementType BASIC_DOC_REFERENCE_HOLDER = new IJavaDocElementType("DOC_REFERENCE_HOLDER");

  IElementType BASIC_DOC_TYPE_HOLDER = new IJavaDocElementType("DOC_TYPE_HOLDER");

  IElementType BASIC_DOC_COMMENT = new IJavaDocElementType("DOC_COMMENT");
  IElementType BASIC_DOC_MARKDOWN_CODE_BLOCK = new IJavaDocElementType("DOC_CODE_BLOCK");
  IElementType BASIC_DOC_MARKDOWN_REFERENCE_LINK = new IJavaDocElementType("DOC_REFERENCE_LINK");
  IElementType BASIC_DOC_MARKDOWN_REFERENCE_LABEL = new IJavaDocElementType("DOC_REFERENCE_LABEL");

  ParentAwareTokenSet BASIC_ALL_JAVADOC_ELEMENTS = ParentAwareTokenSet.create(
    BASIC_DOC_TAG, BASIC_DOC_INLINE_TAG, BASIC_DOC_METHOD_OR_FIELD_REF, BASIC_DOC_PARAMETER_REF, BASIC_DOC_TAG_VALUE_ELEMENT,
    BASIC_DOC_REFERENCE_HOLDER, BASIC_DOC_TYPE_HOLDER, BASIC_DOC_COMMENT);
}