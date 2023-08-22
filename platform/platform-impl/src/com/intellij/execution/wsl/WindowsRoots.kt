// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinBase
import java.nio.file.Path

/**
 * List of physical disk roots of Windows. Such drives could be used for Windows<->WSL mapping (c:\ --> /mnt/c/)
 * [java.io.File.listRoots] checks permissions for all roots and may freeze trying to fetch it for disconnected network drive till timeout.
 * We use Win32API to only get physical volumes.
 */
fun listWindowsLocalDriveRoots(): List<Path> {
  if (!SystemInfoRt.isWindows) {
    Logger.getInstance(Kernel32::class.java).warn("listWindowsRoots called not on windows!")
    return emptyList()
  }
  val kernel32 = Kernel32.INSTANCE
  //https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getdrivetypea
  return Kernel32Util.getLogicalDriveStrings()
    .filter { kernel32.GetDriveType(it) == WinBase.DRIVE_FIXED }
    .map { Path.of(it) }
}