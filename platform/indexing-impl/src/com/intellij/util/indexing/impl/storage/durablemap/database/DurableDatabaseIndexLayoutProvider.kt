// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.util.io.storages.database.DurableDatabase
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate

/**
 * Comma-separated list of `indexId`s that this provider will support.
 * One could enable the provider not only globally, but selectively, for specific set of indexes.
 * Use '*' to enable the provider for *all* indexes.
 */
private const val APPLICABLE_INDEX_IDS_PROPERTY = "DurableDatabaseIndexLayoutProvider.APPLICABLE_INDEX_IDS"
private const val DATABASE_DIRECTORY_NAME = "indexing-database"

private val LOG = logger<DurableDatabaseIndexLayoutProvider>()

/**
 * Stores index data in one [DurableDatabase] in `{index-dir}/indexing-database/`.
 *
 * Currently, uses the same selection logic as the file-based Durable Map provider: the list
 * of indexes to support is given in `DurableDatabaseIndexLayoutProvider.APPLICABLE_INDEX_IDS` system property, and
 * only those indexes are owned by this provider. The only exception is `*`-value: if it is in the list -- **all** indexes are
 * owned by this provider
 */
@ApiStatus.Internal
class DurableDatabaseIndexLayoutProvider(
  private val databasePath: Path = PathManager.getIndexRoot().resolve(DATABASE_DIRECTORY_NAME)
) : FileBasedIndexLayoutProvider {
  private var lazyDatabaseHolder: Lazy<DurableDatabase> = newDatabaseHolder()

  private fun newDatabaseHolder(): Lazy<DurableDatabase> = lazy {
    DurableDatabaseFactory.withDefaults().open(databasePath)
  }

  private val applicableIndexIds: Predicate<String> by lazy {
    val applicableIds = System.getProperty(APPLICABLE_INDEX_IDS_PROPERTY, "")
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .toSet()
    return@lazy if (applicableIds.isEmpty()) {
      LOG.info("Applicable indexes: <none> (given by $APPLICABLE_INDEX_IDS_PROPERTY system property)")
      Predicate<String> { _ -> false }
    }
    else if (applicableIds.contains("*")) {
      LOG.info("Applicable indexes: <all> (given by $APPLICABLE_INDEX_IDS_PROPERTY system property)")
      Predicate<String> { _ -> true }
    }
    else {
      LOG.info("Applicable indexes: ${applicableIds.joinToString { "[${it}]" }} (given by $APPLICABLE_INDEX_IDS_PROPERTY system property)")
      Predicate<String> { indexId -> applicableIds.contains(indexId) }
    }
  }

  override fun isSupported(): Boolean = true

  override fun isApplicable(extension: FileBasedIndexExtension<*, *>): Boolean =
    applicableIndexIds.test(extension.name.name)

  @Synchronized
  override fun <K, V> getLayout(
    extension: FileBasedIndexExtension<K, V>,
    otherApplicableProviders: Iterable<FileBasedIndexLayoutProvider>,
  ): VfsAwareIndexStorageLayout<K, V> {
    val database = lazyDatabaseHolder.value
    return when (extension) {
      is SingleEntryFileBasedIndexExtension<V> -> {
        @Suppress("UNCHECKED_CAST")
        DurableDatabaseSingleEntryStorageLayout(database, extension) as VfsAwareIndexStorageLayout<K, V>
      }
      is ShardableIndexExtension -> {
        if (extension.shardsCount() > 1) {
          DurableDatabaseShardedStorageLayout(database, extension)
        }
        else {
          DurableDatabaseStorageLayout(database, extension)
        }
      }
      else -> DurableDatabaseStorageLayout(database, extension)
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun closeAndClearData() {
    closeDatabase()
    if (Files.exists(databasePath) && !FileUtil.deleteWithRenaming(databasePath)) {
      throw IOException("Cannot delete database at $databasePath")
    }
    lazyDatabaseHolder = newDatabaseHolder()
  }

  @Throws(IOException::class)
  override fun close(): Unit = closeDatabase()

  @Synchronized
  @Throws(IOException::class)
  private fun closeDatabase() {
    if (lazyDatabaseHolder.isInitialized()) {
      lazyDatabaseHolder.value.close()
    }
  }
}
