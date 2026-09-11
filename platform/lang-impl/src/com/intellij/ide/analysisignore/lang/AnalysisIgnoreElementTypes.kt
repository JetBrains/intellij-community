// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus

/**
 * The token types and the element types of a `.analysisignore` file.
 */
@ApiStatus.Internal
object AnalysisIgnoreElementTypes {
  /** A line that starts with `#` in column 0, without the line break. */
  @JvmField
  val COMMENT: IElementType = IElementType("COMMENT", AnalysisIgnoreLanguage)

  /** A line with content that is not a comment, without the trailing spaces and the line break. */
  @JvmField
  val PATTERN: IElementType = IElementType("PATTERN", AnalysisIgnoreLanguage)

  /** The element that holds one [PATTERN] token. */
  @JvmField
  val ENTRY: IElementType = IElementType("ENTRY", AnalysisIgnoreLanguage)

  @JvmField
  val FILE: IFileElementType = IFileElementType(AnalysisIgnoreLanguage)

  @JvmField
  val COMMENTS: TokenSet = TokenSet.create(COMMENT)
}
