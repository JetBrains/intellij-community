// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import kotlin.jvm.JvmField

/**
 * @see com.intellij.psi.impl.source.tree.JavaDocElementType
 */
object JavaDocSyntaxElementType {
  @JvmField val DOC_TAG: SyntaxElementType = SyntaxElementType("DOC_TAG")
  @JvmField val DOC_INLINE_TAG: SyntaxElementType = SyntaxElementType("DOC_INLINE_TAG")
  @JvmField val DOC_METHOD_OR_FIELD_REF: SyntaxElementType = SyntaxElementType("DOC_METHOD_OR_FIELD_REF")
  @JvmField val DOC_PARAMETER_REF: SyntaxElementType = SyntaxElementType("DOC_PARAMETER_REF")
  @JvmField val DOC_TAG_VALUE_ELEMENT: SyntaxElementType = SyntaxElementType("DOC_TAG_VALUE_ELEMENT")
  @JvmField val DOC_SNIPPET_TAG: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_TAG")
  @JvmField val DOC_SNIPPET_TAG_VALUE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_TAG_VALUE")
  @JvmField val DOC_SNIPPET_BODY: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_BODY")
  @JvmField val DOC_SNIPPET_ATTRIBUTE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE")
  @JvmField val DOC_SNIPPET_ATTRIBUTE_LIST: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE_LIST")
  @JvmField val DOC_SNIPPET_ATTRIBUTE_VALUE: SyntaxElementType = SyntaxElementType("DOC_SNIPPET_ATTRIBUTE_VALUE")

  @JvmField val DOC_REFERENCE_HOLDER: SyntaxElementType = SyntaxElementType("DOC_REFERENCE_HOLDER")

  @JvmField val DOC_TYPE_HOLDER: SyntaxElementType = SyntaxElementType("DOC_TYPE_HOLDER")

  @JvmField val DOC_COMMENT: SyntaxElementType = SyntaxElementType("DOC_COMMENT")
  @JvmField val DOC_MARKDOWN_CODE_BLOCK: SyntaxElementType = SyntaxElementType("DOC_CODE_BLOCK")
  @JvmField val DOC_MARKDOWN_REFERENCE_LINK: SyntaxElementType = SyntaxElementType("DOC_REFERENCE_LINK")
}