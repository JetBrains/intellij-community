// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet

import com.intellij.openapi.module.Module
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FacetManagerFactory {
  /**
   * Should be invoked under read lock only. Otherwise, it may lead to a memory leak.
   *
   * @throws AlreadyDisposedException if the module is disposed
   */
  @RequiresReadLock(generateAssertion = false)
  fun getFacetManager(module: Module): FacetManager
}