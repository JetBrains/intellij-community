// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType

/**
 * @see com.intellij.psi.impl.source.tree.JavaDocElementType
 */
object JavaDocSyntaxElementType {
  val DOC_TAG: SyntaxElementType = SyntaxElementType("DOC_TAG")
  val DOC_INLINE_TAG: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG")
  val DOC_METHOD_OR_FIELD_REF: SyntaxElementType = SyntaxElementType("DOC_METHOD_OR_FIELD_REF")
  val DOC_PARAMETER_REF: SyntaxElementType = SyntaxElementType("DOC_PARAMETER_REF")
  val DOC_TAG_VALUE_ELEMENT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_ELEMENT")
  val DOC_SNIPPET_TAG: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_TAG")
  val DOC_SNIPPET_TAG_VALUE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_TAG_VALUE")
  val DOC_SNIPPET_BODY: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_BODY")
  val DOC_SNIPPET_ATTRIBUTE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE")
  val DOC_SNIPPET_ATTRIBUTE_LIST: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE_LIST")
  val DOC_SNIPPET_ATTRIBUTE_VALUE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE_VALUE")

  val DOC_REFERENCE_HOLDER: SyntaxElementType = SyntaxElementType("DOC_REFERENCE_HOLDER")

  val DOC_TYPE_HOLDER: SyntaxElementType = SyntaxElementType("DOC_TYPE_HOLDER")

  val DOC_COMMENT: SyntaxElementType = SyntaxElementType("DOC_COMMENT")
  val DOC_MARKDOWN_CODE_BLOCK: SyntaxElementType = SyntaxElementType("DOC_CODE_BLOCK")
  val DOC_MARKDOWN_REFERENCE_LINK: SyntaxElementType = SyntaxElementType("DOC_REFERENCE_LINK")
}