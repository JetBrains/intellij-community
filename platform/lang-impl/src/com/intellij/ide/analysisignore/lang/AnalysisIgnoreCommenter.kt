// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.lang.Commenter

/**
 * Comments a line of a `.analysisignore` file with `#`. The format has no block comment.
 */
internal class AnalysisIgnoreCommenter : Commenter {
  override fun getLineCommentPrefix(): String = "#"

  override fun getBlockCommentPrefix(): String? = null

  override fun getBlockCommentSuffix(): String? = null

  override fun getCommentedBlockCommentPrefix(): String? = null

  override fun getCommentedBlockCommentSuffix(): String? = null
}
