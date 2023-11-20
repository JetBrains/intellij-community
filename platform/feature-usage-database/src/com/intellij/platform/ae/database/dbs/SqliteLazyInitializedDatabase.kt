// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.dbs.migrations.MIGRATIONS
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.sqlite.*
import java.nio.file.Path
import kotlin.io.path.exists

private val logger = logger<SqliteLazyInitializedDatabase>()

private val MAX_CONNECTION_RETRIES_ALLOWED = 4

/**
 * This class provides access to an instance of [SqliteConnection]
 *
 * @param path if null, database will be initialized in memory. No values will be stored persistently
 */
class SqliteLazyInitializedDatabase(private val path: Path?) {
  private val connectionMutex = Mutex()
  private var connectionAttempts = 0
  private var connection: SqliteConnection? = null
  private var mutableMetadata: SqliteDatabaseMetadata? = null
  val metadata: SqliteDatabaseMetadata get() {
    val md = mutableMetadata
    assert(md != null) { "Database is not yet initialized" }

    return md!!
  }

  /**
   * Allows executing code with [SqliteConnection]
   */
  suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T? {
    val myConnectionAttempts = connectionMutex.withLock { connectionAttempts }
    if (myConnectionAttempts >= MAX_CONNECTION_RETRIES_ALLOWED) {
      return null
    }
    val myConnection = connectionMutex.withLock {
      try {
        getOrInitConnection()
      }
      catch (t: Throwable) {
        logger.error(t)
        null
      }
    }

    if (myConnection == null) return null

    check(myConnection.isOpen()) { "Database is not open" }

    return withContext(Dispatchers.IO) {
      action(myConnection)
    }
  }

  private fun getOrInitConnection(): SqliteConnection {
    val currentConnection = connection
    return if (currentConnection != null) {
      currentConnection
    }
    else {
      logger.info("Initializing database connection")
      logger.trace(ExceptionUtil.currentStackTrace())
      ++connectionAttempts
      val newConnection = SqliteConnection(path, false)
      val newMetadata = SqliteDatabaseMetadata(newConnection, path?.exists() ?: true)

      connection = newConnection
      mutableMetadata = newMetadata

      return newConnection
    }
  }

  private fun SqliteConnection.isOpen() = !isClosed
}
