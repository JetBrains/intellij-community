// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.lang.IdeLanguageCustomization
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.annotations.ApiStatus

/**
 * Serves [IdeLanguageCustomizationApi]: registered by the backend module for the monolith and the
 * remote-dev backend, used directly by [IdeLanguageCustomizationApi.getInstance] in backend-less
 * frontends (IJ Light).
 */
@ApiStatus.Internal
class IdeLanguageCustomizationApiImpl : IdeLanguageCustomizationApi {
  override suspend fun getPrimaryIdeLanguagesExtensions(): Set<String> {
    return serviceAsync<IdeLanguageCustomization>().primaryIdeLanguages
      .asSequence()
      .mapNotNull { it.associatedFileType }
      .flatMap { fileType ->
        sequenceOf(fileType.getDefaultExtension()).plus(getAssociatedExtensions(fileType))
      }
      .toSet()
  }

  private fun getAssociatedExtensions(fileType: LanguageFileType): Sequence<String> {
    return FileTypeManager.getInstance().getAssociations(fileType)
      .asSequence()
      .filterIsInstance<ExtensionFileNameMatcher>()
      .map { it.extension }
  }
}
