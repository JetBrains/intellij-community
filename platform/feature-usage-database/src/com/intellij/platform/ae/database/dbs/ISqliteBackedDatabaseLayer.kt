package com.intellij.platform.ae.database.dbs

import org.jetbrains.sqlite.StatementCollection

/**
 * A layer that uses SQLite to store data.
 *
 * Be very careful with these APIs! You MUST NOT make any write operations to the database
 */
interface ISqliteBackedDatabaseLayer {
  /**
   * Returns statement collection.
   *
   * Using prepared statements created from statement collection you can execute queries
   */
  fun getStatementCollection(): StatementCollection

  /**
   * Table name for the current layer. Use this in your SQL queries
   */
  val tableName: String
}