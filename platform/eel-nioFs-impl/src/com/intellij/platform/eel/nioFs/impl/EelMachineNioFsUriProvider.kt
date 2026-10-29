// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.nioFs.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Returns the URI used to register and look up the machine-bound NIO [java.nio.file.FileSystem] for this [EelMachine],
 * or `null` if no provider handles this machine.
 */
@get:ApiStatus.Internal
val EelMachine.nioFsUri: URI?
  get() = EelMachineNioFsUriProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getNioFsUri(this) }

/**
 * Provides the deterministic URI used to identify the NIO [java.nio.file.FileSystem] of an [EelMachine].
 */
@ApiStatus.Internal
interface EelMachineNioFsUriProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EelMachineNioFsUriProvider> = ExtensionPointName("com.intellij.eelMachineNioFsUriProvider")
  }

  fun getNioFsUri(machine: EelMachine): URI?
}
