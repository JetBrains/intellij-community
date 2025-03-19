// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.IndexExtension
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator.customIndexLayoutProviderBean
import com.intellij.util.indexing.impl.storage.IndexStorageLayoutLocator.getLayout
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider.STORAGE_LAYOUT_EP_NAME
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException


/**
 * Encapsulates finding a appropriate [IndexStorageLayout] ([VfsAwareIndexStorageLayout] really) for a [IndexExtension]
 * See [getLayout] method for a details of lookup algo.
 */
@Internal
object IndexStorageLayoutLocator {
  private val log = logger<IndexStorageLayoutLocator>()

  /**
   * Finds a [VfsAwareIndexStorageLayout] for the given indexExtension.
   *
   * Storage layout implementation lookup protocol:
   * 1. EP(`com.intellij.fileBasedIndexLayout`) supplies the list of registered layout providers beans ([FileBasedIndexLayoutProviderBean])
   * 2. Provider beans filtered for [layoutProvider.isSupported()], and sorted by [.priority]
   * 3. If [customIndexLayoutProviderBean] is set -- it is moved to the 0th position in the providers beans list
   * 4. First provider from the list which [provider.isApplicable(indexExtension)] is used
   *
   * [customIndexLayoutProviderBean] could be set:
   * 1. Via `-Didea.index.storage.forced.layout=<providerId>`
   * 2. Via [IndexLayoutPersistentSettings] -- which loads/stores <providerId> from `{indexRoot}/indices.layout` file.
   * This is a way used by UI to switch default provider, and persist chosen provider between sessions.
   */
  @JvmStatic
  @Throws(IOException::class)
  fun <Key, Value> getLayout(indexExtension: FileBasedIndexExtension<Key, Value>): VfsAwareIndexStorageLayout<Key, Value> {
    val prioritizedLayoutProviders = prioritizedLayoutProviders(forceTopProviderBean = customIndexLayoutProviderBean)
    val applicableLayoutProviders = prioritizedLayoutProviders.filter { it.layoutProvider.isApplicable(indexExtension) }

    val primaryProviderBeanForExtension = applicableLayoutProviders.firstOrNull()
    if (primaryProviderBeanForExtension == null) {
      //default provider should be applicable to anything, so this is an exceptional case:
      throw UnsupportedOperationException(
        "${indexExtension}: no suitable index storage provider was found in a " + prioritizedLayoutProviders.joinToString(separator = "\n")
      )
    }

    log.info("Layout '${primaryProviderBeanForExtension.id}' will be used to for '${indexExtension.name}' index " +
             "(applicable providers: [${applicableLayoutProviders.joinToString { it.id }}])" +
             ((indexExtension as? ShardableIndexExtension)?.let {", shards = ${it.shardsCount()}"} ?: ""))
    
    val otherApplicableProviders = applicableLayoutProviders
      .filterNot { it === primaryProviderBeanForExtension }
      .map { it.layoutProvider }
    return primaryProviderBeanForExtension.layoutProvider.getLayout(indexExtension, otherApplicableProviders)
  }

  /** Layout providers beans supported on the current platform/IDE, ordered by priority (desc) */
  val supportedLayoutProviders: List<FileBasedIndexLayoutProviderBean>
    get() {
      return STORAGE_LAYOUT_EP_NAME.extensionList
        .filter { it.layoutProvider.isSupported }
        .sortedBy { -it.priority }  //MAX_INT priority is the first one
    }

  /** id of a non-default [FileBasedIndexLayoutProviderBean], if chosen, otherwise null */
  @JvmStatic
  val customLayoutId: String?
    get() = customIndexLayoutProviderBean?.id

  /**
   * IndexLayoutProvider could be customized either by sys('idea.index.storage.forced.layout') property, or
   * by [IndexLayoutPersistentSettings] (which is `{indexesRoot}/indices.layout` file), with the first method having a priority.
   *
   * Method throws [IllegalStateException] if custom layout provider configuration exists but not valid (e.g. supplied providerId
   * does not exist). If both configuration methods have no configuration at all (i.e. no system property, no 'indices.layout' file)
   * -- method just returns null.
   */
  private val customIndexLayoutProviderBean: FileBasedIndexLayoutProviderBean?
    get() {
      val forcedLayoutID = System.getProperty("idea.index.storage.forced.layout")
      if (forcedLayoutID != null) {
        return supportedLayoutProviders.find { it.id == forcedLayoutID }
               ?: throw IllegalStateException("Can't find index storage layout for id = $forcedLayoutID")
      }
      return IndexLayoutPersistentSettings.getCustomLayout()
    }

  /**
   * @return list of provider beans supported on the current platform, ordered by priority (desc).
   * If forceTopProviderBean is not null => it is moved/added to the 0th position
   */
  private fun prioritizedLayoutProviders(forceTopProviderBean: FileBasedIndexLayoutProviderBean? = null): List<FileBasedIndexLayoutProviderBean> {
    if (forceTopProviderBean == null) {
      return supportedLayoutProviders
    }
    //move 'force top' provider on top of the list of supported providers:
    return supportedLayoutProviders
      .filter { it.id != forceTopProviderBean.id }
      .toMutableList()
      .apply { add(0, forceTopProviderBean) }
  }
}