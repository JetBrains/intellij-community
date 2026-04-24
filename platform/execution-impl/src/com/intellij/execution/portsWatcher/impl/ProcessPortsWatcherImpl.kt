// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher.impl

import com.intellij.execution.portsWatcher.ListeningPort
import com.intellij.execution.portsWatcher.ListeningPortHandler
import com.intellij.execution.portsWatcher.PortListeningOptions
import com.intellij.execution.portsWatcher.ProcessPortsWatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.isLinux
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import kotlin.coroutines.cancellation.CancellationException
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class ProcessPortsWatcherImpl(
  private val eelDescriptor: EelDescriptor,
  private val pid: Long,
  private val handler: ListeningPortHandler,
  private val options: PortListeningOptions,
) : ProcessPortsWatcher {
  private val exponentialBackoffCounters = listOf(0L, 0L, 2000L, 2000L, 4000L, 7000L, 14000L, 30000L, 60000L)
  private val resetCounterChannel = Channel<Unit>(
    capacity = Channel.CONFLATED,
    onUndeliveredElement = { logger.trace { "signal was not delivered" } }
  )

  @get:VisibleForTesting
  val isProcessWatchForListeningPortsEnabled: Boolean
    get() {
      val isProcessWatchEnabled = Registry.`is`("rdct.portForwarding.processWatch.enabled", true)
      logger.debug { "Process watch for listening ports enabled: $isProcessWatchEnabled" }
      return isProcessWatchEnabled
    }

  override fun resetDelay() {
    logger.debug { "Reset delay" }
    // Doesn't really matter whether send succeeded or not.
    // If it failed, then someone already requested reset recently, and we will reset the delay soon.
    resetCounterChannel.trySend(Unit)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  internal fun startWatch(scope: CoroutineScope) {
    if (!isProcessWatchForListeningPortsEnabled) {
      logger.info("Process ports watcher is disabled. Skip.")
      return
    }

    logger.info("ProcessPortsWatcher. Start watching for processes: <$pid>, options: <$options>")

    scope.launch(Dispatchers.IO + CoroutineName(this.toString())) {
      var countersIterator = exponentialBackoffCounters.iterator()
      var currentBackoff = countersIterator.next()

      while (isActive) {
        logger.trace { "ProcessPortsWatcher is going to suspend in select" }

        @Suppress("RemoveExplicitTypeArguments")
        select<Unit> {
          resetCounterChannel.onReceive {
            logger.trace { "ProcessPortsWatcher received counter reset in select" }
            countersIterator = exponentialBackoffCounters.iterator()
            currentBackoff = countersIterator.next()
          }

          onTimeout(currentBackoff.milliseconds) {
            logger.trace { "ProcessPortsWatcher received timeout in select" }
            if (countersIterator.hasNext()) {
              currentBackoff = countersIterator.next()
            }
          }
        }

        val newState = try {
          scanPortsOnce()
        }
        catch (ex: CancellationException) {
          throw ex
        }
        catch (ex: Exception) {
          logger.error("Failed to scan ports", ex)
          emptySet()
        }

        updateState(newState)
        logger.trace { "ProcessPortsWatcher going to suspend in unconditional delay in 1 second" }
        delay(1.seconds)
      }
    }
  }

  @VisibleForTesting
  suspend fun scanPortsOnce(): Set<ListeningPort> {
    val eelApi = eelDescriptor.toEelApi()

    val pids = if (options.includesChildren()) {
      getPidsTreeForPpid(eelApi, pid)
    }
    else mutableSetOf(pid)

    if (!options.includesSelf()) {
      pids.remove(pid)
    }

    if (pids.isEmpty()) {
      return emptySet()
    }

    return getPortsOfPids(eelApi, pids)
  }

  private suspend fun getPortsOfPids(eelApi: EelApi, pids: Set<Long>): Set<ListeningPort> {
    val platform = eelApi.platform
    return when {
      platform.isLinux -> scanLinuxListeningPorts(eelApi, pids)
      platform.isMac -> scanMacListeningPorts(eelApi, pids)
      // Non-local Windows targets are not supported
      platform.isWindows && eelApi.descriptor == LocalEelDescriptor -> scanLocalWindowsListeningPorts(pids)
      else -> {
        logger.warn("Unsupported EEL platform ($platform); port detection disabled")
        emptySet()
      }
    }
  }

  private var currentState = setOf<ListeningPort>()

  private fun updateState(newState: Set<ListeningPort>) {
    val stoppedPorts = currentState.minus(newState)
    logger.trace {
      "ProcessPortsWatcher received stopped listening ports:\n" + stoppedPorts.joinToString(separator = "\n") { " # pid: ${it.pid}, port: ${it.port}" }
    }

    val newPorts = newState.minus(currentState)
    logger.trace {
      "ProcessPortsWatcher received new listening ports:\n" + newPorts.joinToString(separator = "\n") { " # pid: ${it.pid}, port: ${it.port}" }
    }

    currentState = newState

    newPorts.forEach { handler.onPortListeningStarted(it) }
    stoppedPorts.forEach { handler.onPortListeningEnded(it) }
  }

  private suspend fun getPidsTreeForPpid(eelApi: EelApi, parentPid: Long): MutableSet<Long> {
    val ppidToPid = getPPidToPidMapping(eelApi)

    // Grouped process tree by ppid value
    val pidsMap = ppidToPid.groupBy({ it.first }, { it.second })

    val resultPids = getAllChildPids(parentPid, pidsMap)
    if (options.includesSelf()) {
      resultPids.add(parentPid)
    }

    return resultPids
  }

  private fun getAllChildPids(ppid: Long, pidsMap: Map<Long, List<Long>>): MutableSet<Long> {
    val result = mutableSetOf<Long>()
    val children = pidsMap[ppid]
    logger.debug { "ProcessPortsWatcher got children of $ppid are: $children" }
    if (children == null) {
      return result
    }

    result.addAll(children)
    children.flatMapTo(result) { childPid -> getAllChildPids(childPid, pidsMap) }

    return result
  }

  private suspend fun getPPidToPidMapping(eelApi: EelApi): List<Pair<Long, Long>> {
    // Use JVM API if environment is local, otherwise use POSIX `ps` command
    return if (eelApi.descriptor == LocalEelDescriptor) {
      listLocalProcessTree()
    }
    else if (eelApi.descriptor.osFamily.isPosix) {
      readPosixPPidToPidMapping(eelApi)
    }
    else emptyList()
  }

  private fun listLocalProcessTree(): List<Pair<Long, Long>> {
    return ProcessHandle.allProcesses()
      .asSequence()
      .mapNotNull { process ->
        val parent = process.parent()
        if (parent.isEmpty) null else parent.get().pid() to process.pid()
      }
      .toList()
  }

  /**
   * Lists (ppid, pid) pairs by running `ps -eo pid,ppid` inside [eelApi]'s environment.
   * The header line is skipped. Returns an empty list if the command fails or produces no parseable rows.
   */
  private suspend fun readPosixPPidToPidMapping(eelApi: EelApi): List<Pair<Long, Long>> {
    val processResult = try {
      withTimeout(10.seconds) {
        val coroutineScope = this
        eelApi.exec.spawnProcess("ps", "-eo", "pid,ppid")
          .scope(coroutineScope)
          .eelIt()
          .awaitProcessResult()
      }
    }
    catch (_: TimeoutCancellationException) {
      logger.warn("ps command hanged for more than 10 seconds, pid tree not available")
      return emptyList()
    }
    catch (ex: ExecuteProcessException) {
      logger.warn("ps command failed", ex)
      return emptyList()
    }

    val stdout = processResult.stdout.decodeToString()
    val exit = processResult.exitCode
    if (exit != 0) {
      logger.warn("ps returned non-zero exit code ($exit), pid tree not available")
      return emptyList()
    }
    return stdout.lineSequence()
      .drop(1) // header
      .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 2) return@mapNotNull null
        val pid = parts[0].toLongOrNull() ?: return@mapNotNull null
        val ppid = parts[1].toLongOrNull() ?: return@mapNotNull null
        ppid to pid
      }
      .toList()
  }

  override fun toString(): String {
    return "ProcessPortsWatcherImpl(eelDescriptor=$eelDescriptor, pid=$pid, handler=$handler, options=$options)"
  }

  companion object {
    private val logger: Logger = logger<ProcessPortsWatcher>()
  }
}