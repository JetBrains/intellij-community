// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortImpl
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.sun.jna.Memory
import com.sun.jna.platform.win32.IPHlpAPI
import com.sun.jna.platform.win32.WinError
import com.sun.jna.ptr.IntByReference
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val LOG = logger<ProcessPortsWatcher>()

/**
 * Scans listening TCP ports for the given [pids] on the local Windows host via JNA `IPHlpAPI`
 * (`GetExtendedTcpTable`).
 */
internal fun scanLocalWindowsListeningPorts(pids: Set<Long>): Set<ListeningPort> {
  @OptIn(LowLevelLocalMachineAccess::class)
  check(OS.CURRENT == OS.Windows) { "This method is supposed to be called only in Windows local environment" }

  val sizePtr = IntByReference()
  // First call sizes the buffer; subsequent calls populate it.
  run {
    val retCode = IPHlpAPI.INSTANCE.GetExtendedTcpTable(null,
                                                        sizePtr,
                                                        false,
                                                        IPHlpAPI.AF_INET,
                                                        IPHlpAPI.TCP_TABLE_CLASS.TCP_TABLE_OWNER_PID_LISTENER,
                                                        0)
    when (retCode) {
      WinError.ERROR_INSUFFICIENT_BUFFER -> { /* do nothing, works as expected */
      }
      WinError.ERROR_INVALID_PARAMETER -> {
        LOG.error("Invalid parameter(s) was passed to GetExtendedTcpTable.")
        return mutableSetOf()
      }
      else -> LOG.warn("Unexpected return code from GetExtendedTcpTable. $retCode")
    }
  }
  var size: Int
  var buf: Memory
  do {
    size = sizePtr.value
    buf = Memory(size.toLong())
    val retCode = IPHlpAPI.INSTANCE.GetExtendedTcpTable(buf,
                                                        sizePtr,
                                                        false,
                                                        IPHlpAPI.AF_INET,
                                                        IPHlpAPI.TCP_TABLE_CLASS.TCP_TABLE_OWNER_PID_LISTENER,
                                                        0)
    when (retCode) {
      WinError.ERROR_INSUFFICIENT_BUFFER -> {
        /* do nothing, just call one more time with new extended buffer */
      }
      WinError.ERROR_INVALID_PARAMETER -> {
        LOG.error("Invalid parameter(s) was passed to GetExtendedTcpTable.")
        return mutableSetOf()
      }
      WinError.NO_ERROR -> {}
      else -> LOG.warn("Unexpected return code from GetExtendedTcpTable. $retCode")
    }
  }
  while (size < sizePtr.value)

  val tcpTable = IPHlpAPI.MIB_TCPTABLE_OWNER_PID(buf)
  val result = mutableSetOf<ListeningPort>()
  for (i in 0 until tcpTable.dwNumEntries) {
    val row = tcpTable.table[i]
    try {
      val rowPid = row.dwOwningPid.toLong()
      if (rowPid !in pids) continue

      var localPortBytes = ByteBuffer.allocate(4).putInt(row.dwLocalPort).array()
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