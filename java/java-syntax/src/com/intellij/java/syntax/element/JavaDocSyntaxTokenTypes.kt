// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * @see com.intellij.lexer.JavaDocTokenTypes
 */
object JavaDocSyntaxTokenTypes {
  val spaceCommentsTokenSet: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaDocSyntaxTokenType.DOC_SPACE, JavaDocSyntaxTokenType.DOC_COMMENT_DATA)
}
