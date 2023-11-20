// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.createDirectories
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
 * This service provides access to an instance of [SqliteConnection]
 */
@Service
class SqliteLazyInitializedDatabase : ISqliteExecutor, ISqliteInternalExecutor {
  companion object {
    internal suspend fun getInstanceAsync() = serviceAsync<SqliteLazyInitializedDatabase>()
  }
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
  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
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
      action(myConnection, metadata)
    }
  }

  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection) -> T): T? {
    return execute { initDb, metadata ->
      action(initDb)
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
      val path = getDatabasePath()
      val newConnection = SqliteConnection(path, false)
      val newMetadata = SqliteDatabaseMetadata(newConnection, path?.exists() ?: true)

      connection = newConnection
      mutableMetadata = newMetadata

      return newConnection
    }
  }

  private fun SqliteConnection.isOpen() = !isClosed

  private fun getDatabasePath(): Path? {
    if (ApplicationManager.getApplication().isUnitTestMode) return null // in-memory database

    val folder = PathManager.getCommonDataPath().resolve("IntelliJ")
    folder.createDirectories()

    return folder.resolve("ae.db")
  }
}
