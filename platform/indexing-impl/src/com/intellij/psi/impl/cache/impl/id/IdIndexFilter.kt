// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
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
 * 'Smart' file-filter for {@link IdIndex}: allows extending filtering patterns with {@link IndexFilterExcludingExtension}.
 * The current use of it: exclude .java source-files in libraries (index .class-files instead).
 *
 * @see IndexFilterExcludingExtension
 */
internal class IdIndexFilter : BaseFileTypeInputFilter(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) {
  private companion object {
    //FIXME RC: use JavaIdIndexer.INDEX_SOURCE_FILES_IN_LIBRARIES_REGISTRY_KEY (but it is in different module, and overall it looks
    //          like encapsulation violation)
    val ENABLE_EXTENSION_EXCLUDES = !Registry.`is`("index.ids.from.java.sources.in.jar", false)

    val EXTENSION_EXCLUDES: CustomizableExcludeExtensions = CustomizableExcludeExtensions(
      ExtensionPointName.create("com.intellij.idIndexFilterExcludeExtension")
    )
  }

  init{
    logger<IdIndexFilter>().info(
      "Filter extensions is ${if (ENABLE_EXTENSION_EXCLUDES) "enabled: $EXTENSION_EXCLUDES" else "disabled"}"
    )
  }

  override fun acceptFileType(fileType: FileType): ThreeState {
    return when {
      fileType is PlainTextFileType -> ThreeState.fromBoolean(!FileBasedIndex.IGNORE_PLAIN_TEXT_FILES)

      ENABLE_EXTENSION_EXCLUDES && EXTENSION_EXCLUDES.hasExtensionForFileType(fileType) -> ThreeState.UNSURE //go through slowPathIfFileTypeHintUnsure()

      fileType is LanguageFileType -> ThreeState.YES

      //'.class' fileType is also handled here:
      IdTableBuilding.getFileTypeIndexer(fileType) != null -> ThreeState.YES

      else -> ThreeState.NO
    }
  }

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    check(ENABLE_EXTENSION_EXCLUDES) { "ENABLE_EXTENSION_EXCLUDES must be true to reach this point" }

    return !EXTENSION_EXCLUDES.shouldExcludeFile(file)
  }
}