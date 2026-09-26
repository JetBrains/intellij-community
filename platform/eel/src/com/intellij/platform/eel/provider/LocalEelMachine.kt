// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data object LocalEelMachine : EelMachine {
  override val internalName: String = "Local"

  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    check(descriptor === LocalEelDescriptor) { "Wrong descriptor: $descriptor for machine: $this" }
    return localEel
  }

  override fun ownsDescriptor(descriptor: EelDescriptor): Boolean = descriptor === LocalEelDescriptor
}
