// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.util.ResourceUtil
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object DeclarativeHintsPreviewProvider {
  /**
   * Preview name of the default case when no option is selected in settings.
   */
  private const val MAIN_PREVIEW_NAME = "preview"

  fun getPreview(language: Language, providerId: String, provider: InlayHintsProvider): String? {
    return getOptionPreview(provider, language, MAIN_PREVIEW_NAME, providerId)
  }

  fun getOptionPreview(language: Language, providerId: String, optionId: String, provider: InlayHintsProvider): String? {
    return getOptionPreview(provider, language, optionId, providerId)
  }

  private fun getOptionPreview(provider: InlayHintsProvider, language: Language, optionId: String, providerId: String): String? {
    val fileType = language.associatedFileType ?: PlainTextFileType.INSTANCE
    return getTextFromStream(providerId, provider, fileType.defaultExtension, optionId)
  }

  private fun getTextFromStream(
    providerId: String,
    provider: Any,
    extension: String,
    caseId: String
  ): String? {
    val path = "inlayProviders/$providerId/$caseId.$extension"
    val stream = provider.javaClass.classLoader.getResourceAsStream(path)
    return if (stream != null) ResourceUtil.loadText(stream) else null
  }
}