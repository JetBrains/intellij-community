// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Obsolete
fun EelExecApi.fetchLoginShellEnvVariablesBlocking(): Map<String, String> {
  return runBlockingMaybeCancellable { fetchLoginShellEnvVariables() }
}

@ApiStatus.Experimental
fun EelPlatform.toOs(): OS {
  return when (this) {
    is EelPlatform.Windows -> OS.Windows
    is EelPlatform.Linux -> OS.Linux
    is EelPlatform.Darwin -> OS.macOS
    is EelPlatform.FreeBSD -> OS.FreeBSD
  }
}

private val archMap by lazy {
  BidirectionalMap<CpuArch, EelPlatform.Arch>().apply {
    put(CpuArch.X86, EelPlatform.Arch.X86)
    put(CpuArch.X86_64, EelPlatform.Arch.X86_64)
    put(CpuArch.ARM32, EelPlatform.Arch.ARM_32)
    put(CpuArch.ARM64, EelPlatform.Arch.ARM_64)
    put(CpuArch.OTHER, EelPlatform.Arch.Unknown)
    put(CpuArch.UNKNOWN, EelPlatform.Arch.Unknown)
  }
}

@ApiStatus.Experimental
fun CpuArch.toEelArch(): EelPlatform.Arch = archMap[this] ?: EelPlatform.Arch.Unknown

@ApiStatus.Experimental
fun EelPlatform.Arch.toCpuArch(): CpuArch = archMap.getKeysByValue(this)?.single() ?: CpuArch.UNKNOWN

@ApiStatus.Experimental
fun EelApi.systemOs(): OS = platform.toOs()
