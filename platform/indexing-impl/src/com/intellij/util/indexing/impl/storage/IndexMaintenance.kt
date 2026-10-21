// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import org.jetbrains.annotations.ApiStatus

/**
 * Starts a maintenance service for an index database;
 *
 * Index database maintenance may need access to high-level IDE functions: i.e. user AFK detection, heavy IDE processes status,
 * etc. Those high-level functions shouldn't be accessible directly from index database implementation ([FileBasedIndexLayoutProvider])
 * => this interface provides a way to put some maintenance control on a high level, and link it to low-level index database internals.
 */
@ApiStatus.Internal
interface IndexMaintenance<P : FileBasedIndexLayoutProvider> {
  /** Keeps the provider available for maintenance decisions until the registration closes */
  fun registerForMaintenance(provider: P): AutoCloseable

  companion object {
    @JvmStatic
    fun <P : FileBasedIndexLayoutProvider> getInstance(): IndexMaintenance<P>? {
      val app = ApplicationManager.getApplication() ?: return null
      if (app.isUnitTestMode || app.isHeadlessEnvironment) return null

      // The service registration determines the provider type, which the container erases
      @Suppress("UNCHECKED_CAST")
      return app.getService(IndexMaintenance::class.java) as IndexMaintenance<P>?
    }
  }
}
