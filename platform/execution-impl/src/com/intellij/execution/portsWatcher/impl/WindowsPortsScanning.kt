// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortImpl
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val LOG = logger<ProcessPortsWatcher>()

/**
 * Scans listening TCP ports for the given [pids] on the local Windows host via [TcpTableApi]
 * (`GetExtendedTcpTable`).
 */
internal fun scanLocalWindowsListeningPorts(pids: Set<Long>): Set<ListeningPort> {
  @OptIn(LowLevelLocalMachineAccess::class)
  check(OS.CURRENT == OS.Windows) { "This method is supposed to be called only in Windows local environment" }

  val rows = try {
    TcpTableApi.listeningRows()
  }
  catch (e: TcpTableApi.Win32Exception) {
    if (e.errorCode == TcpTableApi.ERROR_INVALID_PARAMETER) {
      LOG.error("Invalid parameter(s) was passed to GetExtendedTcpTable.", e)
    }
    else {
      LOG.warn("Unexpected return code from GetExtendedTcpTable. ${e.errorCode}", e)
    }
    return mutableSetOf()
  }

  val result = mutableSetOf<ListeningPort>()
  for (row in rows) {
    try {
      val rowPid = row.owningPid
      if (rowPid !in pids) continue

      var localPortBytes = ByteBuffer.allocate(4).putInt(row.localPort).array()
      if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
        localPortBytes = localPortBytes.reversedArray()
      }
      val localPort = localPortBytes[0].toUByte() * 256u + localPortBytes[1].toUByte()

      result.add(ListeningPortImpl(localPort.toInt(), rowPid))
    }
    catch (e: Exception) {
      LOG.warn("Failed to parse one tcp row. '$row'", e)
    }
  }
  return result
}
