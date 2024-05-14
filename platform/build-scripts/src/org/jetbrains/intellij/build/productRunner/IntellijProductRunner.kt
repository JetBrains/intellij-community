// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productRunner

/**
 * Provides a way to run an IDE which distribution is currently being built by the build scripts.
 * This can be used to obtain some resources and include them in the distribution.
 */
interface IntellijProductRunner {
  suspend fun runProduct(args: List<String>, additionalSystemProperties: Map<String, String> = emptyMap(), isLongRunning: Boolean = false)
}