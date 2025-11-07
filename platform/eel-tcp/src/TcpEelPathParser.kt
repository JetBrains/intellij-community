// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path
import kotlin.io.path.pathString

interface TcpEelPathParser {
  companion object {
    val EP_NAME: ExtensionPointName<TcpEelPathParser> = ExtensionPointName("com.intellij.eelTcpPathParser")
    fun findCompatibleParser(path: String): TcpEelPathParser? = EP_NAME.findFirstSafe { it.isPathCompatible(path) }
    fun findCompatibleParser(path: Path): TcpEelPathParser? = EP_NAME.findFirstSafe { it.isPathCompatible(path) }
    fun extractInternalMachineId(path: String): String? = findCompatibleParser(path)?.extractInternalMachineId(path)
    fun extractInternalMachineId(path: Path): String? = findCompatibleParser(path)?.extractInternalMachineId(path)
    fun toDescriptor(internalName: String): TcpEelDescriptor? = EP_NAME.extensionList.firstNotNullOfOrNull { it.toDescriptor(internalName) }
  }

  fun isPathCompatible(path: String): Boolean
  fun isPathCompatible(path: Path): Boolean = isPathCompatible(path.pathString)
  fun extractInternalMachineId(path: String): String?
  fun extractInternalMachineId(path: Path): String? = extractInternalMachineId(path.pathString)
  fun toDescriptor(internalName: String): TcpEelDescriptor?
}