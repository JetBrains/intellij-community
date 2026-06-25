// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader
import kotlin.time.Duration

@ApiStatus.Internal
interface EnvironmentVariablesAwaitReporter {
  fun started(descriptor: EelDescriptor, mode: EelExecApi.EnvironmentVariablesOptions.Mode) {}

  fun finished(
    descriptor: EelDescriptor,
    mode: EelExecApi.EnvironmentVariablesOptions.Mode,
    duration: Duration,
    result: Result<Map<String, String>>,
  )
}

private val reporter: EnvironmentVariablesAwaitReporter? by lazy {
  ServiceLoader.load(
    EnvironmentVariablesAwaitReporter::class.java,
    EnvironmentVariablesAwaitReporter::class.java.classLoader,
  ).firstOrNull()
}

@ApiStatus.Internal
fun environmentVariablesAwaitReporter(): EnvironmentVariablesAwaitReporter? = reporter
