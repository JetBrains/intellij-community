// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.internal.MainDispatcherFactory

@InternalCoroutinesApi
internal class EdtCoroutineDispatcherFactory : MainDispatcherFactory {

  override val loadPriority: Int get() = 0

  override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher {
    return EdtCoroutineDispatcher
  }
}
