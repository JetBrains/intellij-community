// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data object LocalEelDescriptor : EelDescriptor {
  override val name: String = "Local: ${System.getProperty("os.name")}"

  override val osFamily: EelOsFamily by lazy {
    val osName = System.getProperty("os.name").lowercase()
    when {
      osName.startsWith("windows") -> EelOsFamily.Windows
      else -> EelOsFamily.Posix
    }
  }
}
