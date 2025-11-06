// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptorWithoutNativeFileChooserSupport
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.ijent.tcp.TcpEndpoint
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

class TcpEelDescriptor internal constructor(@ApiStatus.Internal val tcpEndpoint: TcpEndpoint) : EelDescriptorWithoutNativeFileChooserSupport, EelPathBoundDescriptor {
  internal val rootPathString = "/tcp-${tcpEndpoint.toPath()}"
  override val rootPath: Path
    get() = Path.of(rootPathString)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TcpEelDescriptor
    return rootPathString == other.rootPathString
  }

  override fun hashCode(): Int = rootPathString.hashCode()

  override val osFamily: EelOsFamily = EelOsFamily.Posix // FIXME
  override val name: @NonNls String = "TCP ${tcpEndpoint.host}"
}