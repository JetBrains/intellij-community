// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.LocalHistory
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.openapi.Disposable

/**
 * Provides API access to [LocalHistoryImpl]
 */
abstract class LocalHistoryEx internal constructor(): LocalHistory(), Disposable {
  /**
   * return null if [LocalHistoryImpl] disabled or not initialized
   */
  abstract val facade: LocalHistoryFacade?
}
