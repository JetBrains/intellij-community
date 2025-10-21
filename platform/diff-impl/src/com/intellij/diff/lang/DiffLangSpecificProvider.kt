// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.lang

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

/**
 * Patches diff ranges based on the semantic of the language.
 * @see com.intellij.diff.fragments.LineFragment
 */
interface DiffLangSpecificProvider {
  /**
   * true if provider relies on LineFragments provided by [com.intellij.diff.comparison.ComparisonManager] and use it to calculate new ranges
   * false if provider uses custom algorithm to calculate new [LineFragment]s.
   */
  val shouldPrecalculateLineFragments: Boolean
  /**
   * Represents custom ignore option text.
   * @see com.intellij.diff.tools.util.base.TextDiffViewerUtil.IgnorePolicySettingAction
   */
  val description: @Nls(capitalization = Nls.Capitalization.Sentence) String

  /**
   * Checks if the current adjuster can is applicable based on the [Language].
   */
  fun isApplicable(language: Language): Boolean

  /**
   * Computes new [LineFragment] list using custom comparison algorithm. This implementation doesn't rely on previously computed [LineFragment]s
   * and, therefore, may work faster.
   *
   * @see getPatchedLineFragments
   */
  fun getLineFragments(project: Project?, textLeft: CharSequence, textRight: CharSequence, ignorePolicy: IgnorePolicy, highlightPolicy: HighlightPolicy, indicator: ProgressIndicator): List<List<LineFragment>> {
    throw UnsupportedOperationException("Method should be implemented if shouldPrecalculateLineFragments is false")
  }

  /**
   * Computes new [LineFragment] list using custom comparison algorithm.
   *
   * @param fragmentList list of [LineFragment] that were found by initial comparison
   * @param textLeft - text of the document before changes
   * @param textRight - text of the document after the changes
   * @param ignorePolicy - current ignore policy
   * @param highlightPolicy - current highlight policy
   *
   */
  fun getPatchedLineFragments(project: Project?, fragmentList: List<List<LineFragment>>, textLeft: CharSequence, textRight: CharSequence, ignorePolicy: IgnorePolicy, highlightPolicy: HighlightPolicy, indicator: ProgressIndicator): List<List<LineFragment>> {
    throw UnsupportedOperationException("Method should be implemented if shouldPrecalculateLineFragments is true")
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DiffLangSpecificProvider> = ExtensionPointName.create<DiffLangSpecificProvider>("com.intellij.diff.lang.DiffLangSpecificAdjuster")

    @JvmStatic
    fun findApplicable(leftContent: DiffContent, rightContent: DiffContent): DiffLangSpecificProvider? {
      val leftLanguage = DiffLanguage.getLanguage(leftContent)
      val rightLanguage = DiffLanguage.getLanguage(rightContent)

      if (leftLanguage == null || rightLanguage == null || leftLanguage != rightLanguage) return null

      return EP_NAME.extensionList.firstOrNull { it.isApplicable(leftLanguage) }
    }
  }
}