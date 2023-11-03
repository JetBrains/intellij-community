// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ae.database.tests.dbs

import com.intellij.platform.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.platform.ae.database.dbs.SqliteInitializedDatabase
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*

/**
 * Runs a test for [IUserActivityDatabaseLayer]
 */
fun <T : IUserActivityDatabaseLayer> runDatabaseLayerTest(
  dbFactory: (CoroutineScope, SqliteInitializedDatabase) -> T,
  action: suspend (T) -> Unit,
) = runInitializedDatabaseTestInternal { cs, db -> action(dbFactory(cs, db)) }

fun <T : IUserActivityDatabaseLayer> runDatabaseLayerTest(
  dbFactory: (CoroutineScope, SqliteInitializedDatabase) -> T,
  action: suspend (T, SqliteInitializedDatabase) -> Unit,
) = runInitializedDatabaseTestInternal { cs, db -> action(dbFactory(cs, db), db) }

private fun runInitializedDatabaseTestInternal(action: suspend (CoroutineScope, SqliteInitializedDatabase) -> Unit) {
  timeoutRunBlocking {
    withContext(Dispatchers.IO) {
      val db = SqliteInitializedDatabase(this, null)
      action(this, db)
      db.onCoroutineScopeDeath()
      cancel()
    }
  }
}