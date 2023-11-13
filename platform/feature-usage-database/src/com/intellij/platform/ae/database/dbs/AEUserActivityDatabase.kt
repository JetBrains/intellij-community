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
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KClass

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

  private var db: SqliteInitializedDatabase? = null
  private val layers = HashMap<String, IUserActivityDatabaseLayer>()

  private val layersFactories = listOf(
    DatabaseFactory(
      CounterUserActivityDatabase::class,
      { cs, db ->  CounterUserActivityDatabase(cs, db) },
      { cs -> MockCounterUserActivityDatabase(cs) }
    ),
    DatabaseFactory(
      TimeSpanUserActivityDatabase::class,
      { cs, db -> TimeSpanUserActivityDatabase(cs, db) },
      { cs -> MockTimeSpanUserActivityDatabase(cs) }
    )
  )

  private val getDatabaseLock = Any()

  inline fun <reified T : IUserActivityDatabaseLayer> getDatabase(): T {
    ThreadingAssertions.assertBackgroundThread()

    synchronized(getDatabaseLock) {
      val layer = layers[T::class.java.name]
      if (layer == null) {
        logger.info("Trying to init layer ${T::class.simpleName}")

        val theDb = safeGetDb()

        val newLayer = initLayer(T::class.java, theDb)
        if (newLayer == null) {
          error("Layer ${T::class.java.simpleName} was not found")
        }

        val castedLayer = newLayer as? T ?: error("Layer ${T::class.java.simpleName} is incorrent type ${newLayer::class.java.simpleName}")

        layers[T::class.java.name] = castedLayer

        return castedLayer
      }

      val castedLayer = layer as? T ?: error("Layer ${T::class.java.simpleName} was found, but of incorrect type ${layer::class.java.simpleName}")

      return castedLayer
    }
  }

  private fun safeGetDb(): SqliteInitializedDatabase {
    val currentDb = db
    return if (currentDb == null) {
      logger.info("DB is not yet init")
      val myDb = initDb()
      db = myDb
      myDb
    }
    else {
      currentDb
    }
  }

  private fun initLayer(clazz: Class<*>, database: SqliteInitializedDatabase): IUserActivityDatabaseLayer? {
    val layerFactory = layersFactories.firstOrNull { it.clazz.java == clazz }
    if (layerFactory == null) {
      logger.warn("Layer ${clazz.simpleName} not found")
      return null
    }

    return layerFactory.getInstance(cs, database)
  }

  private fun initDb(): SqliteInitializedDatabase {
    val theDb = SqliteInitializedDatabase(cs, getDatabasePath())
    db = theDb

    return theDb
  }

  private fun getDatabasePath(): Path? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null // in-memory database

    val folder = PathManager.getCommonDataPath().resolve("IntelliJ")
    folder.createDirectories()

    return folder.resolve("ae.db")
  }

  private class DatabaseFactory<T : IUserActivityDatabaseLayer> (
    val clazz: KClass<T>,
    val prod: (cs: CoroutineScope, db: SqliteInitializedDatabase) -> IUserActivityDatabaseLayer,
    val test: (cs: CoroutineScope) -> IUserActivityDatabaseLayer
  ) {
    fun getInstance(cs: CoroutineScope, db: SqliteInitializedDatabase): IUserActivityDatabaseLayer = if (ApplicationManager.getApplication().isUnitTestMode) test(cs) else prod(cs, db)
  }
}