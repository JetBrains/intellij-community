// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.frontend

import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to product information for both the front-end and monolith code.
 */
@ApiStatus.Experimental
interface HostIdeInfoService {
  fun getHostInfo(): HostInfo?
}

@ApiStatus.Experimental
data class HostInfo(
  val productCode: String,
  val osName: String,
  val osVersion: String
)