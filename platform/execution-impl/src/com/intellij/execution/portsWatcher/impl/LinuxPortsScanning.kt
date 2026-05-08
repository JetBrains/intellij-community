// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortImpl
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.readFile
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.isLinux
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.CancellationException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.io.path.listDirectoryEntries

private val LOG = logger<ProcessPortsWatcher>()

private val socketInodeRegex = Regex("socket:\\[(\\d+)]")

private const val B2 = "[0-9a-fA-F]{2}"
private const val B4 = "[0-9a-fA-F]{4}"
private const val B8 = "[0-9a-fA-F]{8}"
private const val B32 = "[0-9a-fA-F]{32}"

private val TCP4_LINE_REGEX =
  Regex("^\\s*(?<entryNum>\\d+):\\s+" +
        "(?<localAddr>$B8):(?<localPort>$B4)\\s+" +
        "(?<remoteAddr>$B8):(?<remotePort>$B4)\\s+" +
        "(?<state>$B2)\\s+" +
        "(?<txQueue>$B8):(?<rxQueue>$B8)\\s+" +
        "(?<tr>$B2):(?<tmWhen>$B8)\\s+" +
        "(?<retransmit>$B8)\\s+" +
        "(?<uid>[0-9a-fA-F]+)\\s+" +
        "(?<timeout>[0-9a-fA-F]+)\\s+" +
        "(?<inode>\\d+)\\s+.*")

private val TCP6_LINE_REGEX =
  Regex("^\\s*(?<entryNum>\\d+):\\s+" +
        "(?<localAddr>$B32):(?<localPort>$B4)\\s+" +
        "(?<remoteAddr>$B32):(?<remotePort>$B4)\\s+" +
        "(?<state>$B2)\\s+" +
        "(?<txQueue>$B8):(?<rxQueue>$B8)\\s+" +
        "(?<tr>$B2):(?<tmWhen>$B8)\\s+" +
        "(?<retransmit>$B8)\\s+" +
        "(?<uid>[0-9a-fA-F]+)\\s+" +
        "(?<timeout>[0-9a-fA-F]+)\\s+" +
        "(?<inode>\\d+)\\s+.*")

// Hex representation of TCP_LISTEN state, per Linux's net/tcp_states.h.
private const val LINUX_TCP_LISTEN_HEX = "0A"

private data class LinuxTcpLine(val inode: Long, val localPort: Int)

/**
 * Scans listening TCP ports for the given [pids] on a Linux target via `/proc`.
 *
 * Reads `/proc/<pid>/fd` (for socket inode ownership) and `/proc/net/tcp[6]` (for the inode → address mapping) through [EelApi.fs].
 */
internal suspend fun scanLinuxListeningPorts(eelApi: EelApi, pids: Set<Long>): Set<ListeningPort> {
  check(eelApi.platform.isLinux) { "This method is supposed to be called only in Linux environments, but was: ${eelApi.descriptor}" }

  data class InodeWithPid(val pid: Long, val inode: Long)

  val inodesWithPid = mutableListOf<InodeWithPid>()
  for (pid in pids) {
    val inodes = runCatchingWarn("Failed to get socket inodes for pid $pid") {
      readLinuxSocketInodesForPid(eelApi.descriptor, pid)
    }
    inodesWithPid.addAll(inodes.map { InodeWithPid(pid, it) })
  }

  val listeningPorts4 = runCatchingWarn("Failed to get listening ipv4 ports") {
    parseLinuxTcpTable(eelApi, "/proc/net/tcp", TCP4_LINE_REGEX)
  }
  val listeningPorts6 = runCatchingWarn("Failed to get listening ipv6 ports") {
    parseLinuxTcpTable(eelApi, "/proc/net/tcp6", TCP6_LINE_REGEX)
  }

  val result = mutableSetOf<ListeningPort>()
  for (listeningPort in listeningPorts4 + listeningPorts6) {
    val matchedPort = inodesWithPid.find { it.inode == listeningPort.inode }
    if (matchedPort != null) {
      result.add(ListeningPortImpl(listeningPort.localPort, matchedPort.pid))
    }
  }
  return result
}

/**
 * Lists /proc/[pid]/fd and extracts socket inodes from the symlink targets, like "socket:[12345]" -> 12345.
 */
private fun readLinuxSocketInodesForPid(eelDescriptor: EelDescriptor, pid: Long): List<Long> {
  // Have to use the java.nio.file.Path API instead of EEL API for listing directory entries and reading symlinks.
  // Because LocalEelFileSystemPosixApi doesn't resolve hyperlinks - it always returns the unresolved link in this case,
  // so it is not possible to get the actual symlink target.
  val fdDir = EelPath.parse("/proc/$pid/fd", eelDescriptor).asNioPath()
  val entries = try {
    fdDir.listDirectoryEntries()
  }
  catch (_: NoSuchFileException) {
    return emptyList()  // Process can be already terminated
  }

  val result = mutableListOf<Long>()
  for (fdEntry in entries) {
    val target = try {
      Files.readSymbolicLink(fdEntry).toString()
    }
    catch (_: NoSuchFileException) {
      continue  // Process can be already terminated
    }
    val match = socketInodeRegex.matchEntire(target) ?: continue
    val inode = match.groupValues[1].toLongOrNull() ?: continue
    result.add(inode)
  }
  return result
}

private suspend fun parseLinuxTcpTable(eelApi: EelApi, path: String, tcpLineRegex: Regex): MutableList<LinuxTcpLine> {
  val eelPath = EelPath.parse(path, eelApi.descriptor)
  val readResult = eelApi.fs.readFile(eelPath).eelIt().getOrThrow()
  val bytes = readResult.bytes.toByteArray()
  val content = String(bytes, Charsets.UTF_8)

  val result = mutableListOf<LinuxTcpLine>()
  for (line in content.lineSequence().drop(1)) {
    if (line.isBlank()) continue

    val match = tcpLineRegex.find(line)
    if (match == null) {
      LOG.warn("Failed to parse tcp line from $path, line: '$line'")
      continue
    }

    val groups = match.groups as? MatchNamedGroupCollection ?: run {
      LOG.warn("Failed to convert match groups into MatchNamedGroupCollection")
      continue
    }

    if (groups["state"]!!.value != LINUX_TCP_LISTEN_HEX) {
      LOG.trace { "TCP line is not in listening state: '$line'" }
      continue
    }

    val inode = groups["inode"]!!.value.toLongOrNull() ?: run {
      LOG.warn("inode of TCP line in not a number: '$line'")
      continue
    }

    val localPort = try {
      groups["localPort"]!!.value.toInt(16)
    }
    catch (_: NumberFormatException) {
      LOG.warn("local port of TCP line in not a number: '$line'")
      continue
    }

    result.add(LinuxTcpLine(inode, localPort))
  }
  return result
}

private inline fun <T> runCatchingWarn(message: String, block: () -> List<T>): List<T> {
  return try {
    block()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    LOG.warn(message, e)
    emptyList()
  }
}