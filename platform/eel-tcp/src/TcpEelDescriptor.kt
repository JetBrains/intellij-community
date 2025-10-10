// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptorWithoutNativeFileChooserSupport
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.ijent.tcp.TcpEndpoint
import java.nio.file.Path

class TcpEelDescriptor internal constructor (tcpEndpoint: TcpEndpoint) : EelDescriptorWithoutNativeFileChooserSupport, EelPathBoundDescriptor {
  override val machine: EelMachine = TcpEelMachine(tcpEndpoint)
  internal val rootPathString = "/tcp-${tcpEndpoint.toPath()}"
  override val rootPath: Path
    get() = Path.of(rootPathString)
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TcpEelDescriptor

    return machine == other.machine
  }

  override fun hashCode(): Int {
    return machine.hashCode()
  }
}