// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.annotations.ApiStatus
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Sets [Dispatchers.Main] to [dispatcher] for all tests of a class, and resets it after them.
 * It is the JUnit 5 replacement of [MainDispatcherRule].
 */
@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    Dispatchers.setMain(dispatcher)
  }

  override fun afterAll(context: ExtensionContext) {
    Dispatchers.resetMain()
  }
}
