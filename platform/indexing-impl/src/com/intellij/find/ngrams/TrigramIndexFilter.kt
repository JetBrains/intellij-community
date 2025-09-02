// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ThreeState
import com.intellij.util.indexing.CustomizableExcludeExtensions
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexFilterExcludingExtension
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy


/**
 * 'Smart' file-filter for {@link TrigramIndex}: allows extending filtering patterns with {@link IndexFilterExcludingExtension}.
 * The current use of it is to exclude source files in libraries from trigram indexing.
 *
 * @see IndexFilterExcludingExtension
 */
@Service //RC: why is it a service? All other filters are just a regular POJO
internal class TrigramIndexFilter : BaseFileTypeInputFilter(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) {

  companion object {
    @JvmStatic
    val ENABLE_EXTENSION_EXCLUDES_REGISTRY_KEY = "ide.index.trigram.enable.exclude.extensions"

    /** @see TrigramIndexRegistryValueListener */
    private val ENABLE_EXTENSION_EXCLUDES = Registry.`is`(ENABLE_EXTENSION_EXCLUDES_REGISTRY_KEY, false)

    private val EXTENSION_EXCLUDES: CustomizableExcludeExtensions = CustomizableExcludeExtensions(
      ExtensionPointName.create("com.intellij.trigramIndexFilterExcludeExtension")
    )

    @JvmStatic
    fun isExcludeExtensionsEnabled(): Boolean = ENABLE_EXTENSION_EXCLUDES
  }

  init {
    logger<TrigramIndexFilter>().info(
      "Filter exclude extensions is ${if (ENABLE_EXTENSION_EXCLUDES) "enabled: $EXTENSION_EXCLUDES" else "disabled"}"
    )
  }

  override fun acceptFileType(fileType: FileType): ThreeState = when {
    !TrigramIndex.isEnabled() -> ThreeState.NO

    fileType.isBinary -> ThreeState.NO

    fileType is PlainTextFileType -> ThreeState.fromBoolean(!FileBasedIndex.IGNORE_PLAIN_TEXT_FILES)

    ENABLE_EXTENSION_EXCLUDES && EXTENSION_EXCLUDES.hasExtensionForFileType(fileType) -> ThreeState.UNSURE //go through slowPathIfFileTypeHintUnsure()

    else -> ThreeState.YES
  }

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    check(ENABLE_EXTENSION_EXCLUDES) { "ENABLE_EXTENSION_EXCLUDES must be true to reach this point" }

    return !EXTENSION_EXCLUDES.shouldExcludeFile(file)
  }
}