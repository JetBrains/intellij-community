// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.annotations.ApiStatus
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) : TestRule {

  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        Dispatchers.setMain(dispatcher)
        try {
          base.evaluate()
        }
        finally {
          Dispatchers.resetMain()
        }
      }
    }
  }
}