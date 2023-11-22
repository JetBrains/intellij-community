// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.platform.ae.database.IdService
import com.intellij.util.ExceptionUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.sqlite.*
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

private val logger = logger<SqliteLazyInitializedDatabase>()

private const val MAX_CONNECTION_RETRIES_ALLOWED = 4

/**
 * This service provides access to an instance of [SqliteConnection]
 */
@Service
class SqliteLazyInitializedDatabase(private val cs: CoroutineScope) : ISqliteExecutor, ISqliteInternalExecutor {
  companion object {
    internal suspend fun getInstanceAsync() = serviceAsync<SqliteLazyInitializedDatabase>()
    internal fun getInstance() = ApplicationManager.getApplication().service<SqliteLazyInitializedDatabase>()
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

  private var retryMessageLogged = false

  private val actionsBeforeDatabaseDisposal = mutableListOf<suspend () -> Unit>()

  init {
    cs.launch {
      cs.awaitCancellationAndInvoke {
        logger.info("Database disposal started, ${actionsBeforeDatabaseDisposal.size} actions to perform")
        logger.runAndLogException {
          withTimeout(4.seconds) {
            for (action in actionsBeforeDatabaseDisposal) {
              action()
            }
          }
        }
        logger.runAndLogException {
          val myConnection = connectionMutex.withLock { connection }
          if (myConnection != null) {
            myConnection.close()
          }
          else {
            logger.info("Connection was null, so didn't close it")
          }
        }
        logger.info("Database disposal finished")
      }
    }
  }

  fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
    actionsBeforeDatabaseDisposal.add(action)
  }

  /**
   * Allows executing code with [SqliteConnection]
   */
  override suspend fun <T> execute(action: suspend (initDb: SqliteConnection, metadata: SqliteDatabaseMetadata) -> T): T? {
    val myConnectionAttempts = connectionMutex.withLock { connectionAttempts }
    if (myConnectionAttempts >= MAX_CONNECTION_RETRIES_ALLOWED && !retryMessageLogged) {
      logger.error("Max retries reached to init db")
      retryMessageLogged = true
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
      val isNewFile = path == null || !path.exists()
      val newConnection = SqliteConnection(path, false)
      val newMetadata = SqliteDatabaseMetadata(newConnection, isNewFile)

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

    return folder.resolve("ae_${IdService.getInstance().id}.db")
  }
}
