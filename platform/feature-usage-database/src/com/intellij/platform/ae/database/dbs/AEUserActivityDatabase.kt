package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.dbs.counter.CounterUserActivityDatabase
import com.intellij.platform.ae.database.dbs.counter.MockCounterUserActivityDatabase
import com.intellij.platform.ae.database.dbs.timespan.MockTimeSpanUserActivityDatabase
import com.intellij.platform.ae.database.dbs.timespan.TimeSpanUserActivityDatabase
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private val logger = logger<AEUserActivityDatabase>()

/**
 * Main access point for database layers
 */
@Service(Service.Level.APP)
internal class AEUserActivityDatabase(private val cs: CoroutineScope) {
  companion object {
    suspend inline fun <reified T : IUserActivityDatabaseLayer> getDatabaseAsync() = serviceAsync<AEUserActivityDatabase>().getDatabase<T>()
    inline fun <reified T : IUserActivityDatabaseLayer> getDatabase() = ApplicationManager.getApplication().service<AEUserActivityDatabase>().getDatabase<T>()
  }

  private val layers: Map<String, IUserActivityDatabaseLayer>

  init {
    ThreadingAssertions.assertBackgroundThread()
    layers = initDbLayers() // TODO this method invokes IO, not good in constructor
  }

  inline fun <reified T : IUserActivityDatabaseLayer> getDatabase(): T {
    return layers[T::class.java.name] as? T ?: error("Layer ${T::class.java.name} was not found")
  }

  private fun initDbLayers(): Map<String, IUserActivityDatabaseLayer> {
    // TODO: I'm reinventing component container here
    val layers = try {
      listOf(
        DatabaseFactory(
          { cs, db ->  CounterUserActivityDatabase(cs, db) },
          { cs -> MockCounterUserActivityDatabase(cs) }
        ),
        DatabaseFactory(
          { cs, db -> TimeSpanUserActivityDatabase(cs, db) },
          { cs -> MockTimeSpanUserActivityDatabase(cs) }
        )
      )
    }
    catch (t: Throwable) {
      logger.error(t)
      emptyList()
    }

    val db = try {
      SqliteInitializedDatabase(cs, getDatabasePath())
    }
    catch (t: Throwable) {
      logger.error(t)
      return emptyMap()
    }

    return layers.map { if (ApplicationManager.getApplication().isUnitTestMode) it.test(cs) else it.prod(cs, db) }.associateBy { it::class.java.name }
  }

  private fun getDatabasePath(): Path? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null // in-memory database

    val folder = PathManager.getCommonDataPath().resolve("IntelliJ")
    folder.createDirectories()

    return folder.resolve("ae.db")
  }

  private data class DatabaseFactory(
    val prod: (cs: CoroutineScope, db: SqliteInitializedDatabase) -> IUserActivityDatabaseLayer,
    val test: (cs: CoroutineScope) -> IUserActivityDatabaseLayer
  )
}