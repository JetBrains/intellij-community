// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.lang.DiffLanguage
import com.intellij.diff.util.ThreeSide
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * An interface for resolving merge conflicts in a way that requires semantic of some language.
 */
interface LangSpecificMergeConflictResolver {
  /**
   * Checks whether the current language resolver can be used for handling conflicts in the given file.
   */
  fun isApplicable(language: Language): Boolean

  /**
   * Attempts to resolve merge conflicts in the given file. Conflicting chunks are received by a default vcs driver.
   *
   * @return resolved contents for the conflicting chunks, or null if it wasn't possible to resolve chunk automatically.
   *
   * @see LangSpecificMergeConflictResolverWrapper
   */
  suspend fun tryResolveMergeConflicts(context: LangSpecificMergeContext): List<CharSequence?>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LangSpecificMergeConflictResolver> = ExtensionPointName.create("com.intellij.diff.merge.conflict.semantic.resolver")

    @JvmStatic
    fun findApplicable(contentList: List<DocumentContent>): LangSpecificMergeConflictResolver? {
      if (contentList.size != ThreeSide.entries.size) return null

      val languageList = contentList.mapNotNull { DiffLanguage.getLanguage(it) }
      
      if (languageList.size != ThreeSide.entries.size || languageList.distinct().size != 1) return null

      val language = languageList.first()
      return EP_NAME.findFirstSafe { it.isApplicable(language) }
    }
  }
}