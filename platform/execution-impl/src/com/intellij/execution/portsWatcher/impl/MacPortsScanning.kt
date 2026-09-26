// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortImpl
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<ProcessPortsWatcher>()

// example output of lsof -Pan:
//   Python  48684 vladislav.ertel    3u  IPv4 0xd1e5dd8b2551795      0t0  TCP 127.0.0.1:8000 (LISTEN)
private val MAC_TCP_LISTENING_PORTS_REGEX =
  Regex("TCP\\s+(?<interface>[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}|\\*):(?<port>\\d+) \\(LISTEN\\)")

/**
 * Scans listening TCP ports for the given [pids] on a macOS target by invoking `lsof` per pid via [EelApi.exec].
 */
internal suspend fun scanMacListeningPorts(eelApi: EelApi, pids: Set<Long>): Set<ListeningPort> {
  check(eelApi.platform.isMac) { "This method is supposed to be called only in macOS environments, but was: ${eelApi.descriptor}" }

  val scannedOncePorts = mutableSetOf<ListeningPort>()
  val outputs = mutableMapOf<Long, String>()

  for (pidToCheck in pids) {
    val processResult = try {
      withTimeout(10.seconds) {
        val coroutineScope = this
        eelApi.exec.spawnProcess("lsof", "-Pan", "-p", pidToCheck.toString(), "-i")
          .scope(coroutineScope)
          .eelIt()
          .awaitProcessResult()
      }
    }
    catch (_: TimeoutCancellationException) {
      LOG.warn("lsof listening ports detection process hanged for more than 10 seconds for pid $pidToCheck")
      continue
    }
    catch (ex: ExecuteProcessException) {
      LOG.warn("lsof process execution failed for pid $pidToCheck", ex)
      continue
    }

    val stdout = processResult.stdout.decodeToString()
    val exitValue = processResult.exitCode
    if (exitValue != 0) {
      LOG.debug { "lsof returned non-zero exit code ($exitValue) for pid $pidToCheck" }
      LOG.debug { "lsof STDOUT:\n$stdout" }
      LOG.debug { "lsof STDERR:\n${processResult.stderr.decodeToString()}" }
      continue
    }

    LOG.debug { "lsof output:\n$stdout" }
    outputs[pidToCheck] = stdout
    LOG.debug { "Asking lsof for pid $pidToCheck done" }
  }

  outputs.forEach { (pid, output) ->
    if (!output.contains("(LISTEN)")) return@forEach
    for (line in output.lines().drop(1)) {
      LOG.trace { "Parsing line: \"$line\"" }
      for (match in MAC_TCP_LISTENING_PORTS_REGEX.findAll(line)) {
        val namedMatchGroup = match.groups as MatchNamedGroupCollection
        val port = namedMatchGroup["port"]!!.value
        LOG.trace { "Parsed port: \"$port\"" }
        val intPort = try {
          port.toInt()
        }
        catch (_: NumberFormatException) {
          LOG.warn("Failed to parse int from port: '$port'")
          continue
        }
        if (intPort !in 1..65535) {
          LOG.info("Parsed port is not in an adequate port range. port: $intPort")
          continue
        }

        scannedOncePorts.add(ListeningPortImpl(intPort, pid))
      }
    }
  }
  return scannedOncePorts
}