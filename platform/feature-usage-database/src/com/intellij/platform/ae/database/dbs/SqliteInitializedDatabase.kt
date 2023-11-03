// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ae.database.IdService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ae.database.dbs.migrations.LAST_DB_VERSION
import com.intellij.platform.ae.database.dbs.migrations.MIGRATIONS
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.sqlite.*
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = logger<SqliteInitializedDatabase>()

/**
 * This class is responsible for keeping consistency of a database (initial state, running migrations)
 * and keeps metadata, like this IDE id and DB version
 *
 * This class manages resources based on lifetime of [CoroutineScope], please don't implement [AutoCloseable] here
 *
 * This class must be initialized on background thread
 *
 * TODO: open/close db on each request? How bad it is?
 *
 * If you want to add new migration, add text to [MIGRATIONS] array
 *
 * @param cs
 * @param path if null, database will be initialized in memory. No values will be stored persistently
 */
class SqliteInitializedDatabase(cs: CoroutineScope, path: Path?) {
  private val shouldRunMigrations = path?.exists() ?: true
  private val connection = SqliteConnection(path, false)
  private val actionsBeforeDatabaseDeath = mutableListOf<suspend () -> Unit>()

  /**
   * ID from database that represents a pair of IDE ID [IdService.id] and machine ID [IdService.machineId] and IDE family [BuildNumber.currentVersion.productCode]
   */
  val ideId: Int

  init {
    ThreadingAssertions.assertBackgroundThread()
    if (!shouldRunMigrations) {
      logger.info("New database, executing migration")
      executeMigrations(0)
    }
    else {
      val version = try {
        getVersion()
      }
      catch (t: SqliteException) {
        if (t.message == "[SQLITE_ERROR] SQL error or missing database (no such table: meta)") {
          logger.info("File exists, but database seem to be uninitialized")
          0
        }
        else {
          throw t
        }
      }
      executeMigrations(version)
    }

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      cs.awaitCancellationAndInvoke {
        @Suppress("TestOnlyProblems")
        onCoroutineScopeDeath()
      }
    }

    ideId = initIdeId()
  }

  fun createStatementCollection() = StatementCollection(connection)

  fun invokeBeforeDatabaseDeath(action: suspend () -> Unit) {
    actionsBeforeDatabaseDeath.add(action)
  }

  fun isOpen() = !connection.isClosed

  @TestOnly
  internal suspend fun onCoroutineScopeDeath() {
    for (action in actionsBeforeDatabaseDeath) {
      action()
    }
    connection.close()
  }

  suspend fun <T> execute(action: suspend (initDb: SqliteInitializedDatabase) -> T): T {
    check(isOpen()) { "Database is not open" }
    return withContext(Dispatchers.IO) {
      action(this@SqliteInitializedDatabase)
    }
  }

  private fun executeMigrations(fromVersion: Int) {
    val migrations = MIGRATIONS.subList(maxOf(fromVersion, 0), LAST_DB_VERSION)
    for (migration in migrations) {
      connection.execute(migration)
    }

    connection
      .prepareStatement("UPDATE meta SET version = (?) WHERE true;", ObjectBinderFactory.create1<Int>())
      .apply { binder.bind(LAST_DB_VERSION) }
      .executeUpdate()
  }

  private fun getVersion(): Int {
    return connection.selectInt("SELECT version FROM meta LIMIT 1") ?: -1
  }

  private fun initIdeId(): Int {
    val potentialResult = connection.prepareStatement("SELECT id FROM ide WHERE ide_id = (?) AND machine_id = (?) LIMIT 1", ObjectBinder(2)).use { statement ->
      statement.binder.bind(IdService.getInstance().id, IdService.getInstance().machineId)
      statement.selectInt()
    }
    if (potentialResult != null) return potentialResult

    return connection.prepareStatement("insert into ide(ide_id, machine_id, family) values (?, ?, ?) RETURNING id;", ObjectBinder(3)).use { statement ->
      statement.binder.bind(IdService.getInstance().id, IdService.getInstance().machineId, IdService.getInstance().ideCode)
      statement.selectInt() ?: error("Null was returned when not expected")
    }
  }
}
