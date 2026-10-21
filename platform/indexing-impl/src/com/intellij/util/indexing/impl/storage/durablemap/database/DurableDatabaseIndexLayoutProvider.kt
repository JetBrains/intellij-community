// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.Storage
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.util.io.storages.database.DurableDatabase
import com.intellij.platform.util.io.storages.database.DurableDatabaseFactory
import com.intellij.platform.util.io.storages.database.DurableMapImplementation
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY
import com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER
import com.intellij.platform.util.io.storages.database.impl.DropRetiredChunksHousekeeper
import com.intellij.platform.util.io.storages.database.impl.SparseChunksEvacuationHousekeeper
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics
import com.intellij.util.ExceptionUtil.runAllAndRethrowAllExceptions
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.impl.storage.IndexMaintenance
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension
import com.intellij.util.io.IOUtil.MiB
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
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
 *
 * [invertedMapImplementation] and [forwardMapImplementation] select the formats for the two map roles.
 */
@ApiStatus.Internal
class DurableDatabaseIndexLayoutProvider @JvmOverloads constructor(
  private val databasePath: Path = PathManager.getIndexRoot().resolve(DATABASE_DIRECTORY_NAME),
  private val forwardMapImplementation: DurableMapImplementation = DurableMapImplementation.getProperty(
    "DurableDatabaseIndexLayoutProvider.forwardMapImplementation", KEY_VALUE_TOGETHER
  ),
  private val invertedMapImplementation: DurableMapImplementation = DurableMapImplementation.getProperty(
    "DurableDatabaseIndexLayoutProvider.invertedMapImplementation", KEY_VALUE_SEPARATELY
  ),
  private val maintenanceService: () -> IndexMaintenance<DurableDatabaseIndexLayoutProvider>? = { IndexMaintenance.getInstance() },
) : FileBasedIndexLayoutProvider {

  private object OTelReporterHolder {
    // Keep one callback and its cumulative counters across provider replacements and data removal
    val reporter = DurableDatabaseOTelReporter(TelemetryManager.getMeter(Storage))
  }

  private val lazyExecutorHolder: Lazy<ScheduledExecutorService> = newExecutorHolder()
  private val lazyDatabaseHolder: Lazy<DurableDatabase> = newDatabaseHolder()

  //@GuardedBy(this)
  private var closed = false

  //@GuardedBy(this)
  private var maintenanceRegistration: AutoCloseable? = null

  private fun newExecutorHolder(): Lazy<ScheduledExecutorService> = lazy {
    Executors.newSingleThreadScheduledExecutor(
      IntelliJVirtualThreads.ofVirtual().name("durableDatabaseCompaction").factory()
    )
  }

  private fun newDatabaseHolder(): Lazy<DurableDatabase> = lazy {
    //TODO RC: use one of the platform executors?
    val scheduler = lazyExecutorHolder.value
    LOG.info(
      "implementations: forwardIndex=$forwardMapImplementation, invertedIndex=$invertedMapImplementation, " +
      "directory: [$databasePath]",
    )
    val database = DurableDatabaseFactory.withDefaults()
      .housekeeping(scheduler, scheduler)
      .startupHousekeeping(
        SparseChunksEvacuationHousekeeper(
          /* evacuateBelow:       */ 0.25f,
          /* maxEvacuation:       */ 25L * MiB,
          /* maxBlocksToEvacuate: */ 1000
        ),
        DropRetiredChunksHousekeeper(),
      )
      .open(databasePath)
    try {
      OTelReporterHolder.reporter.attachDatabase(database)
      database
    }
    catch (t: Throwable) {
      try {
        database.close()
      }
      catch (closeError: Throwable) {
        t.addSuppressed(closeError)
      }
      throw t
    }
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
    check(!closed) { "Provider is closed [$databasePath]" }

    val database = lazyDatabaseHolder.value
    val layout: VfsAwareIndexStorageLayout<K, V> = when (extension) {
      is SingleEntryFileBasedIndexExtension<V> -> {
        @Suppress("UNCHECKED_CAST")
        DurableDatabaseSingleEntryStorageLayout(database, extension, invertedMapImplementation) as VfsAwareIndexStorageLayout<K, V>
      }
      is ShardableIndexExtension -> {
        if (extension.shardsCount() > 1) {
          DurableDatabaseShardedStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
        }
        else {
          DurableDatabaseStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
        }
      }
      else -> DurableDatabaseStorageLayout(database, extension, invertedMapImplementation, forwardMapImplementation)
    }

    if (maintenanceRegistration == null) {
      maintenanceRegistration = maintenanceService()?.registerForMaintenance(this)
    }
    return layout
  }

  @Synchronized
  @Throws(IOException::class)
  override fun closeAndClearData() {
    close()
    if (Files.exists(databasePath) && !FileUtil.deleteWithRenaming(databasePath)) {
      throw IOException("Cannot delete database at [$databasePath]")
    }
  }

  @Synchronized
  @Throws(IOException::class)
  override fun close() {
    if (closed) return
    closed = true
    closeDatabase()
  }

  //@GuardedBy(this)
  @Throws(IOException::class)
  private fun closeDatabase() {
    runAllAndRethrowAllExceptions(
      IOException::class.java, { IOException("Failed to close database at [$databasePath]") },

      {
        maintenanceRegistration?.close()
        maintenanceRegistration = null
      },

      {
        if (lazyDatabaseHolder.isInitialized()) {
          OTelReporterHolder.reporter.detachDatabase(lazyDatabaseHolder.value)
        }
      },

      {
        if (lazyDatabaseHolder.isInitialized()) {
          lazyDatabaseHolder.value.close()
        }
      },

      {
        if (lazyExecutorHolder.isInitialized()) {
          lazyExecutorHolder.value.shutdown()
        }
      }
    )
  }


  /**
   * For monitoring/testing purposes
   * @return database metrics, or [DatabaseMetrics.DUMMY] if the database is not open or the provider is closed
   */
  @ApiStatus.Internal
  @Synchronized
  fun databaseMetrics(): DatabaseMetrics {
    if (!closed && lazyDatabaseHolder.isInitialized()) {
      return lazyDatabaseHolder.value.metrics(/*snapshot: */true)
    }
    else {
      return DatabaseMetrics.DUMMY
    }
  }
}
