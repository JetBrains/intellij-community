// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.IjentPosixApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private suspend fun WSLDistribution.getIjent(descriptor: EelDescriptor): IjentPosixApi {
  return WslIjentManager.instanceAsync().getIjentApi(descriptor, this, null, false)
}

internal class WslEelMachineResolver : EelMachineResolver {
  override suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
    return getResolvedEelMachine(eelDescriptor)
  }

  override suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine? {
    return if (internalName.startsWith("WSL-"))
      WslEelMachine(WSLDistribution(internalName.substring(4)))
    else
      null
  }

  override fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
    val wslDescriptor = eelDescriptor as? WslEelDescriptor ?: return null
    return WslEelMachine(wslDescriptor.distribution)
  }
}

@ApiStatus.Internal
class WslEelMachine internal constructor(val distribution: WSLDistribution) : EelMachine {
  override val internalName: String = "WSL-" + distribution.id

  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    check(descriptor is WslEelDescriptor && descriptor.distribution == distribution) {
      "Wrong descriptor: $descriptor for machine: $this"
    }

    return distribution.getIjent(descriptor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WslEelMachine

    if (distribution != other.distribution) return false

    return true
  }

  override fun hashCode(): Int {
    var result = distribution.hashCode()
    return result
  }

  override fun ownsPath(path: Path): Boolean {
    val descriptor = path.getEelDescriptor() as? WslEelDescriptor ?: return false
    return descriptor.distribution == distribution
  }
}
