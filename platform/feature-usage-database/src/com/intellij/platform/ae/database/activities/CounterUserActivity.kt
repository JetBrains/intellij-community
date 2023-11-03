// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ae.database.activities

import com.intellij.platform.ae.database.dbs.AEUserActivityDatabase
import com.intellij.platform.ae.database.dbs.counter.CounterUserActivityDatabase

interface CounterUserActivity : UserActivity

abstract class DatabaseBackedCounterUserActivity : CounterUserActivity {
  protected val coroutineScope get() = com.intellij.platform.ae.database.AEDatabaseLifetime.getScope()
  protected fun getDatabase() = AEUserActivityDatabase.getDatabase<CounterUserActivityDatabase>()
}

abstract class WritableDatabaseBackedCounterUserActivity : DatabaseBackedCounterUserActivity() {
  protected suspend fun submit(diff: Int) {
    getDatabase().submit(this, diff)
  }
}