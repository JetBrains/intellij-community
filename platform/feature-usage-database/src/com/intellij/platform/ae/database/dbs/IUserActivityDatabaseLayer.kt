package com.intellij.platform.ae.database.dbs

interface IUserActivityDatabaseLayer

/**
 * Internal user activity database layer. These methods should not be used outside 'ae-database'
 */
internal interface IInternalUserActivityDatabaseLayer {
  /**
   * Lambda should be executed during coroutine scope, but before database close() method
   */
  fun invokeOnDatabaseDeath(action: suspend () -> Unit)

  /**
   * Ensures that all database access is made on IO dispatcher
   *
   * @see com.intellij.ae.database.v2.dbs.SqliteInitializedDatabase.execute
   */
  suspend fun <T> execute(action: suspend () -> T): T
}