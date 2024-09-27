// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.storage.EmbeddingsStorageManagerWrapper
import com.intellij.platform.ml.embeddings.indexer.storage.TextEmbeddingsStorageManager
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

/**
 * EmbeddingsConfiguration defines a configuration of indexer and storage used for a given indexId.
 * Use [getStorageManagerWrapper] to obtain the corresponding instance of [EmbeddingsStorageManagerWrapper] to perform a search for
 * a given index.
 */
@ApiStatus.OverrideOnly
interface EmbeddingsConfiguration<KeyT> {
  fun getStorageManager(): TextEmbeddingsStorageManager<KeyT>

  fun getKeyProvider(): EmbeddingStorageKeyProvider<KeyT>

  fun isEnabled(): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<EmbeddingsConfigurationBean> = ExtensionPointName.create(
      "com.intellij.platform.ml.embeddings.textEmbeddingsConfiguration")

    fun getStorageManagerWrapper(indexId: IndexId): EmbeddingsStorageManagerWrapper<*> {
      val bean = EP_NAME.extensionList.first {
        it.indexId == indexId &&
        it.instance.isEnabled()
      }

      val instance = bean.instance
      return instance.toStorageManagerWrapper(indexId)
    }

    private fun <KeyT> EmbeddingsConfiguration<KeyT>.toStorageManagerWrapper(indexId: IndexId): EmbeddingsStorageManagerWrapper<KeyT> {
      val storageManager = getStorageManager()
      val keyProvider = getKeyProvider()
      return EmbeddingsStorageManagerWrapper(indexId, storageManager, keyProvider)
    }
  }
}

internal class EmbeddingsConfigurationBean : BaseKeyedLazyInstance<EmbeddingsConfiguration<*>>() {
  @RequiredElement
  @Attribute("implementation")
  lateinit var implementation: String

  @RequiredElement
  @Attribute(value = "indexId")
  lateinit var indexId: IndexId

  override fun getImplementationClassName(): String? = implementation
}