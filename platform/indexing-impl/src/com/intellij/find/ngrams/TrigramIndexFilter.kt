// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.util.SystemProperties
import com.intellij.util.ThreeState
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter
import com.intellij.util.indexing.hints.FileTypeSubstitutionStrategy
import org.jetbrains.annotations.ApiStatus

/**
 * 'Smart' file-filter for {@link TrigramIndex}: allows extending filtering patterns with {@link ExtensionPointName}.
 * The current use of it is to exclude source files in libraries from trigram indexing.
 *
 * @see TrigramIndexFilterExcludeExtension
 */
@Service
internal class TrigramIndexFilter: BaseFileTypeInputFilter(FileTypeSubstitutionStrategy.BEFORE_SUBSTITUTION) {
  private companion object {
    val excludeExtensionEPName = ExtensionPointName.create<TrigramIndexFilterExcludeExtension>("com.intellij.trigramIndexFilterExcludeExtension")
    val enableExcludeExtensions = SystemProperties.getBooleanProperty("ide.trigram.index.uses.exclude.extensions", false)
  }

  @Volatile
  private var excludeExtensions = createExcludeExtensionsLazyValue()

  private fun createExcludeExtensionsLazyValue(): Lazy<Map<FileType, List<TrigramIndexFilterExcludeExtension>>> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    excludeExtensionEPName.extensionList.groupBy { it.getFileType() }
  }

  fun installListener() = excludeExtensionEPName.addChangeListener({
                                                                     excludeExtensions = createExcludeExtensionsLazyValue()
                                                                   }, null)


  override fun acceptFileType(fileType: FileType): ThreeState {
    if (!TrigramIndex.isEnabled()) {
      return ThreeState.NO
    }
    when {
      fileType.isBinary -> return ThreeState.NO
      fileType is PlainTextFileType -> return ThreeState.fromBoolean(!FileBasedIndex.IGNORE_PLAIN_TEXT_FILES)
      enableExcludeExtensions && excludeExtensions.value.containsKey(fileType) -> return ThreeState.UNSURE
      else -> return ThreeState.YES
    }
  }

  override fun slowPathIfFileTypeHintUnsure(file: IndexedFile): Boolean {
    val excludeExtensions = excludeExtensions.value[file.fileType] ?: return true
    return !excludeExtensions.any { it.shouldExclude(file) }
  }
}

@ApiStatus.Internal
interface TrigramIndexFilterExcludeExtension {
  fun getFileType(): FileType
  fun shouldExclude(file: IndexedFile): Boolean
}