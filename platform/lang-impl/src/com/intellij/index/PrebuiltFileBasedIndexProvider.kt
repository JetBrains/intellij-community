// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.ID
import com.intellij.util.io.MapDataExternalizer

open class PrebuiltFileBasedIndexProvider<K, V>(val id: ID<K, V>, override val dirName: String) : PrebuiltIndexProviderBase<Map<K, V>>() {
  companion object {
    @JvmField
    val EXTENSION_POINT_NAME: ExtensionPointName<PrebuiltFileBasedIndexProvider<*, *>> = ExtensionPointName.create(
      "com.intellij.prebuiltFileBasedIndexProvider")
  }

  @Suppress("UNCHECKED_CAST")
  private val indexExtension = FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList().first { it.name == id } as FileBasedIndexExtension<K, V>

  override val indexExternalizer: MapDataExternalizer<K, V>
    get() = MapDataExternalizer(indexExtension.keyDescriptor, indexExtension.valueExternalizer)

  override val indexName get() = indexExtension.name.name

  override val fileTypes: Set<FileType>
    get() = FileTypeManager.getInstance().registeredFileTypes.toSet()
}