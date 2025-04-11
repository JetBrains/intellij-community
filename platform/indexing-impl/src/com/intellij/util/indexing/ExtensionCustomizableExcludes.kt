// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus

/**
 * Helper to work with [IndexFilterExcludingExtension] -- encapsulates all work with file-excluding extensions,
 * and provides simple [hasExtensionForFileType] and [shouldExcludeFile] methods.
 *
 * @see IndexFilterExcludingExtension
 */
@ApiStatus.Internal
class ExtensionCustomizableExcludes(private val excludingExtensionEP: ExtensionPointName<IndexFilterExcludingExtension>) {

  @Volatile
  private var excludeExtensions = createExcludeExtensionsLazyValue()

  private fun createExcludeExtensionsLazyValue(): Lazy<Map<FileType, List<IndexFilterExcludingExtension>>> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    excludingExtensionEP.extensionList.groupBy { it.getFileType() }
  }

  //MAYBE RC: implement changes tracking, or is it an overkill? Change in indexing filter most likely need re-indexing to take effect,
  //          so there is little sense in just changing the filter alone here
  fun installListener() = excludingExtensionEP.addChangeListener({ excludeExtensions = createExcludeExtensionsLazyValue() }, null)


  fun hasExtensionForFileType(fileType: FileType): Boolean = excludeExtensions.value.containsKey(fileType)

  fun shouldExcludeFile(file: IndexedFile): Boolean {
    val excludeExtensions = excludeExtensions.value[file.fileType]
    if(excludeExtensions == null) {
      return false
    }
    return excludeExtensions.any { it.shouldExclude(file) }
  }
}