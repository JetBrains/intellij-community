// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
enum class EelIpPreference {
  PREFER_V4, PREFER_V6, USE_SYSTEM_DEFAULT
}