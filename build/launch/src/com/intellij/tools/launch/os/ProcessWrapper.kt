package com.intellij.tools.launch.os

import kotlinx.coroutines.Deferred

/**
 * This abstraction allows consuming the underlying process's streams by several consumers.
 */
data class ProcessWrapper(
  val processOutputInfo: ProcessOutputInfo,
  val terminationDeferred: Deferred<Int>,
)