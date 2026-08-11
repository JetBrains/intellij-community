// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * Which IP protocol to prefer when a hostname resolves to both IPv4 and IPv6 addresses. Used by [EelTunnelsApi.HostAddress].
 */
@ApiStatus.Experimental
enum class EelIpPreference {
  /** Prefer an IPv4 address. */
  PREFER_V4,

  /** Prefer an IPv6 address. */
  PREFER_V6,

  /** Let the environment's OS choose. */
  USE_SYSTEM_DEFAULT,
}