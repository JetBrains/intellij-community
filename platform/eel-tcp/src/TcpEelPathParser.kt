// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelOsFamily
import java.nio.file.Path
import kotlin.io.path.pathString

interface TcpEelPathParser {
  companion object {
    val EP_NAME: ExtensionPointName<TcpEelPathParser> = ExtensionPointName("com.intellij.eelTcpPathParser")
    private fun findCompatibleParser(path: String): TcpEelPathParser? {
      if (!path.startsWith(TcpEelConstants.TCP_PATH_PREFIX)) return null
      return EP_NAME.findFirstSafe { it.isPathCompatible(path) }
    }
    fun extractInternalMachineId(path: String): Pair<String, EelOsFamily>? = findCompatibleParser(path)?.extractInternalMachineId(path)
    fun extractInternalMachineId(path: Path): Pair<String, EelOsFamily>? = extractInternalMachineId(path.pathString)
    fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor? = EP_NAME.extensionList.firstNotNullOfOrNull { it.toDescriptor(internalName, osFamily) }
  }

  fun isPathCompatible(path: String): Boolean
  fun extractInternalMachineId(path: String): Pair<String, EelOsFamily>?
  fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor?
}