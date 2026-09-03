// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.execution.wsl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * List of physical disk roots of Windows. Such drives could be used for Windows<->WSL mapping (c:\ --> /mnt/c/)
 * [java.io.File.listRoots] checks permissions for all roots and may freeze trying to fetch it for disconnected network drive till timeout.
 * We use Win32API to only get physical volumes.
 */
fun listWindowsLocalDriveRoots(): List<Path> {
  if (!SystemInfoRt.isWindows) {
    Logger.getInstance(WindowsDrives::class.java).warn("listWindowsRoots called not on windows!")
    return emptyList()
  }
  //https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getdrivetypea
  return WindowsDrives.fixedDriveRoots().map { Path.of(it) }
}