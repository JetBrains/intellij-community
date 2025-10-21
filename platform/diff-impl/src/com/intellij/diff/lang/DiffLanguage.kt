// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.lang

import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.lang.DiffLanguage.getLanguageOrCompute
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.CalledInAny

internal object DiffLanguage {

  /**
   * Not to be confused with [DiffUserDataKeys.LANGUAGE]
   * to avoid affecting [com.intellij.diff.util.DiffUtil.createEditorHighlighter] logic
   */
  private val LANGUAGE_KEY: Key<Language> = Key.create("DiffLanguage.Computed")

  /**
   * Note that unlike [getLanguageOrCompute] this method ignores [com.intellij.psi.LanguageSubstitutors] as their
   * calculation might require a background thread.
   *
   * @return language saved as user data in [content] or language guessed by [content] file type
   */
  @CalledInAny
  fun getLanguage(content: DiffContent): Language? =
    getLanguageByDataKey(content) ?: getLanguageByFileType(content)

  @JvmStatic
  @RequiresBackgroundThread
  fun getLanguageOrCompute(project: Project, content: DiffContent): Language? {
    val language = getLanguageByDataKey(content)
    if (language != null) return language

    return computeLanguage(content, project)
  }

  @JvmStatic
  @RequiresBackgroundThread
  fun computeAndCacheLanguage(content: DiffContent, project: Project?) {
    val language = computeLanguage(content, project)
    if (language != null) {
      content.putUserData(LANGUAGE_KEY, language)
    }
  }

  private fun computeLanguage(content: DiffContent, project: Project?): Language? {
    if (project == null) return getLanguageByFileType(content)

    val fileType = content.getContentType()
    val languageForPsi = (content as? DocumentContent)?.highlightFile?.let { highlightingFile ->
      runReadAction { LanguageUtil.getLanguageForPsi(project, highlightingFile, fileType) }
    }

    return languageForPsi ?: getLanguageByFileType(content)
  }

  private fun getLanguageByDataKey(content: DiffContent): Language? = content.getUserData(LANGUAGE_KEY)

  private fun getLanguageByFileType(content: DiffContent): Language? = content.getContentType()?.let(LanguageUtil::getFileTypeLanguage)
}
