// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productRunner

import org.jetbrains.intellij.build.VmProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Provides a way to run an IDE which distribution is currently being built by the build scripts.
 * This can be used to collect some resources and include them in the distribution.
 */
interface IntellijProductRunner {
  suspend fun runProduct(
    args: List<String>,
    additionalVmProperties: VmProperties = VmProperties(emptyMap()),
    timeout: Duration = 30.seconds,
  )
}