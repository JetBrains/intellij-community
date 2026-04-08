// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelOsFamily
import java.nio.file.Path
import kotlin.io.path.pathString

interface TcpEelPathParser {
  companion object {
    val EP_NAME: ExtensionPointName<TcpEelPathParser> = ExtensionPointName("com.intellij.eelTcpPathParser")
    fun extractInternalMachineId(path: Path): Pair<String, EelOsFamily>? = extractInternalMachineId(path.pathString)
    fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor? = EP_NAME.extensionList.firstNotNullOfOrNull { it.toDescriptor(internalName, osFamily) }
    fun extractInternalMachineId(path: String): Pair<String, EelOsFamily>? {
      if (!isPathGenerallyCompatible(path)) return null
      val pathSegment = path.substringAfter(TcpEelConstants.TCP_PATH_PREFIX).substringBefore("/")
      val pathRest = path.removePrefix("${TcpEelConstants.TCP_PATH_PREFIX}$pathSegment").removeSuffix("/")
      val osFamily = SshEelConsts.osFamilyFromPathSegment(pathSegment)
      if (osFamily == EelOsFamily.Windows && (pathRest == "" || pathRest == "/@")) return null
      return SshEelConsts.internalNameFromPathSegment(pathSegment) to osFamily
    }

    private fun isPathGenerallyCompatible(path: String): Boolean {
      return path.startsWith(TcpEelConstants.TCP_PATH_PREFIX)
    }
  }

  fun isInternalNameCompatible(internalName: String): Boolean
  fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor?
}