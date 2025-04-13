// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
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
class CustomizableExcludeExtensions(private val excludingExtensionEP: ExtensionPointName<IndexFilterExcludingExtension>) {

  @Volatile
  private var excludingExtensions = createExcludeExtensionsLazyValue()

  private fun createExcludeExtensionsLazyValue(): Lazy<Map<FileType, List<IndexFilterExcludingExtension>>> = lazy(LazyThreadSafetyMode.PUBLICATION) {
    //some app configurations (e.g. fleet, lsp) may skip Indexing.xml descriptor, and have their own configuration, without
    // appropriate extensionPoint defined -- let's be safe and pretend as-if there are no extensions then:
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(excludingExtensionEP)) {
      excludingExtensionEP.extensionList.groupBy { it.getFileType() }
    }
    else {
      emptyMap()
    }
  }

  //MAYBE RC: implement changes tracking, or is it an overkill? Change in indexing filter most likely need re-indexing to take effect,
  //          so there is little sense in just changing the filter alone here
  fun installListener() = excludingExtensionEP.addChangeListener({ excludingExtensions = createExcludeExtensionsLazyValue() }, null)


  fun hasExtensionForFileType(fileType: FileType): Boolean = excludingExtensions.value.containsKey(fileType)

  fun shouldExcludeFile(file: IndexedFile): Boolean {
    val excludeExtensions = excludingExtensions.value[file.fileType]
    if (excludeExtensions == null) {
      return false
    }
    return excludeExtensions.any { it.shouldExclude(file) }
  }

  override fun toString(): String {
    //don't trigger lazy loading, access EP directly:
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(excludingExtensionEP)) {
      return "CustomizableExcludeExtensions(${excludingExtensionEP.extensionList})"
    }
    else {
      return "CustomizableExcludeExtensions(EP[${excludingExtensionEP.name}] is unresolved -> empty)"
    }
  }
}