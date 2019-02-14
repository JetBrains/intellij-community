// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.cache.impl.id.LexingIdIndexer
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.MapDataExternalizer
import com.intellij.util.xmlb.annotations.Attribute
import java.io.DataInput
import java.io.DataOutput

/**
 * @author traff
 */

fun <K, V> wrapIndexerWithPrebuilt(ex: FileBasedIndexExtension<K, V>, providers: Array<PrebuiltFileBasedIndexProvider<K, V>>): DataIndexer<K, V, FileContent> {
  return object : DataIndexer<K, V, FileContent> {
    override fun map(inputData: FileContent): Map<K, V> {
      val result = providers.mapNotNull { it.get(inputData) }.firstOrNull()
      if (result == null) {
        return ex.indexer.map(inputData)
      } else if (PrebuiltIndexProviderBase.DEBUG_PREBUILT_INDICES) {
        val originalValue = ex.indexer.map(inputData)
        if (result != originalValue) {
          PrebuiltIndexAwareIdIndexer.LOG.error("Prebuilt id index differs from actual value for ${inputData.file.path}")
        }
      }
      return result
    }
  }
}

private val EP_NAME: ExtensionPointName<PrebuiltFileBasedIndexProviderEP> = ExtensionPointName.create("com.intellij.prebuiltFileBasedIndex")
class PrebuiltFileBasedIndexProviderEP : AbstractExtensionPointBean() {
  @Attribute("provider")
  var provider: String? = null

  @Attribute("indexName")
  var indexName: String? = null

  val providerClass: Class<*> by lazy { Class.forName(provider) }

  val instance: PrebuiltFileBasedIndexProvider<*, *> by lazy { providerClass.newInstance() as PrebuiltFileBasedIndexProvider<*, *> }
}

interface PrebuiltFileBasedIndexProviderGenerator {
  companion object {
    @JvmField
    val EXTENSION_POINT_NAME: ExtensionPointName<PrebuiltFileBasedIndexProviderGenerator> = ExtensionPointName.create("com.intellij.prebuiltFileBasedIndexProviderGenerator")
  }

  fun <K, V> generateProvider(id: ID<K, V>): PrebuiltFileBasedIndexProvider<K, V>?
}

abstract class PrebuiltFileBasedIndexProvider<K, V>(val id: ID<K, V>) : PrebuiltIndexProviderBase<Map<K, V>>() {
  companion object {
    @JvmField
    val EXTENSION_POINT_NAME: ExtensionPointName<PrebuiltFileBasedIndexProvider<*, *>> = ExtensionPointName.create(
      "com.intellij.prebuiltFileBasedIndexProvider")
  }

  @Suppress("UNCHECKED_CAST")
  private val indexExtension = FileBasedIndexExtension.EXTENSION_POINT_NAME.getExtensionList().first { it.name == id } as FileBasedIndexExtension<K, V>

  override val indexExternalizer get() = MapDataExternalizer(indexExtension.keyDescriptor, indexExtension.valueExternalizer)

  override val indexName get() = indexExtension.name.name
}

abstract class PrebuiltIndexAwareIdIndexer : PrebuiltFileBasedIndexProvider<IdIndexEntry, Int>(IdIndex.NAME) {
  companion object {
    internal val LOG = Logger.getInstance("#com.intellij.index.PrebuiltIndexAwareIdIndexer")
    const val ID_INDEX_FILE_NAME: String = "id-index"
  }

  override val indexName: String get() = ID_INDEX_FILE_NAME
}