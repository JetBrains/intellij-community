// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelProcessInfo
import com.intellij.platform.eel.EelProcessManagementApi
import com.intellij.platform.eel.EelProcessManagementPosixApi
import com.intellij.platform.eel.EelProcessManagementWindowsApi
import com.intellij.platform.eel.SafeDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [java.lang.ProcessHandle] implementation for processes running inside an Eel environment.
 *
 * It is backed by an [EelProcessManagementApi] for process-tree navigation ([children], [descendants], [parent]) and, when the handle
 * corresponds to a process spawned by the IDE, by the original [EelProcess] for precise lifecycle information ([isAlive], [onExit],
 * [destroy], [destroyForcibly]).
 *
 * Instances are returned from [java.lang.Process.toHandle] on the adapters [IjentChildProcessAdapter] / [IjentChildPtyProcessAdapter].
 */
@ApiStatus.Internal
class IjentChildProcessHandlerAdapter internal constructor(
  private val processManagement: EelProcessManagementApi,
  private val coroutineScope: CoroutineScope,
  private val pidValue: Long,
  /**
   * The original [EelProcess] when this handle corresponds to a process spawned by the IDE, or `null` for a process discovered by
   * walking the environment's process tree via [EelProcessManagementApi.listProcesses].
   *
   * When present, lifecycle and termination go through this exact process object rather than through the pid: [isAlive] / [onExit]
   * read the process' own exit state, and [destroy] / [destroyForcibly] call [EelProcess.terminate] / [EelProcess.kill]. This is
   * important for two reasons: pids can be reused, so acting by pid could hit an unrelated process; and although terminating an
   * [EelProcess] and terminating the same pid via [EelProcessManagementApi] currently run the same system calls, that may diverge in
   * the future, so the handle deliberately prefers the process it owns.
   */
  private val ownProcess: EelProcess?,
  private val cachedInfo: EelProcessInfo?,
) : ProcessHandle {

  override fun pid(): Long = pidValue

  override fun parent(): Optional<ProcessHandle> {
    val info = cachedInfo ?: runBlockingInScope { processInfoFor(pidValue) }
    val parentPid = info?.parentPid?.value ?: return Optional.empty()
    val parentInfo = runBlockingInScope { processInfoFor(parentPid) } ?: return Optional.empty()
    return Optional.of(handleFor(parentInfo))
  }

  override fun children(): Stream<ProcessHandle> {
    val snapshot = currentSnapshot()
    val ownStart = (cachedInfo ?: snapshot.firstOrNull { it.pid.value == pidValue })?.startInstant
    return snapshot
      .asSequence()
      .filter { it.parentPid?.value == pidValue }
      .filter { isPlausibleChild(ownStart, it) }
      .map { handleFor(it) as ProcessHandle }
      .toList()
      .stream()
  }

  override fun descendants(): Stream<ProcessHandle> {
    val snapshot = currentSnapshot()
    val infoByPid = snapshot.associateBy { it.pid.value }
    val childrenByParent = HashMap<Long, MutableList<EelProcessInfo>>()
    for (info in snapshot) {
      val parent = info.parentPid?.value ?: continue
      childrenByParent.getOrPut(parent) { mutableListOf() }.add(info)
    }
    val result = ArrayList<ProcessHandle>()
    val queue = ArrayDeque<Long>()
    queue.add(pidValue)
    val visited = HashSet<Long>()
    visited.add(pidValue)
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      val currentStart = (if (current == pidValue) cachedInfo else null)?.startInstant ?: infoByPid[current]?.startInstant
      for (child in childrenByParent[current].orEmpty()) {
        if (!isPlausibleChild(currentStart, child)) continue
        if (visited.add(child.pid.value)) {
          result.add(handleFor(child))
          queue.add(child.pid.value)
        }
      }
    }
    return result.stream()
  }

  override fun info(): ProcessHandle.Info {
    val info = cachedInfo ?: runBlockingInScope { processInfoFor(pidValue) }
    // Resolve the arguments lazily: awaiting them can trigger an extra (blocking) request, while most callers only read command().
    return IjentProcessHandleInfo(info) { resolveArguments(info) }
  }

  private fun resolveArguments(info: EelProcessInfo?): List<String> {
    if (info == null) return emptyList()
    return try {
      runBlockingInScope { info.arguments.await() }
    }
    catch (_: SafeDeferred.DeferredException) {
      emptyList()
    }
  }

  override fun onExit(): CompletableFuture<ProcessHandle> {
    val ownProcess = ownProcess
    if (ownProcess != null) {
      return ownProcess.exitCode.asCompletableFuture().thenApply { this }
    }
    return coroutineScope.async {
      while (isAliveSuspend()) {
        delay(POLL_INTERVAL)
      }
      this@IjentChildProcessHandlerAdapter as ProcessHandle
    }.asCompletableFuture()
  }

  override fun supportsNormalTermination(): Boolean =
    when (processManagement) {
      is EelProcessManagementPosixApi -> true
      is EelProcessManagementWindowsApi -> false
    }

  override fun destroy(): Boolean = terminate(force = false)

  override fun destroyForcibly(): Boolean = terminate(force = true)

  override fun isAlive(): Boolean = runBlockingInScope { isAliveSuspend() }

  override fun compareTo(other: ProcessHandle): Int = pidValue.compareTo(other.pid())

  override fun equals(other: Any?): Boolean = other is IjentChildProcessHandlerAdapter && other.pidValue == pidValue

  override fun hashCode(): Int = pidValue.hashCode()

  override fun toString(): String = "IjentChildProcessHandlerAdapter(pid=$pidValue, descriptor=${processManagement.descriptor})"

  private fun terminate(force: Boolean): Boolean {
    val ownProcess = ownProcess
    return runBlockingInScope {
      when {
        ownProcess is EelPosixProcess && !force -> {
          ownProcess.terminate()
          true
        }
        ownProcess != null -> {
          ownProcess.kill()
          true
        }
        else -> killByPid(force)
      }
    }
  }

  private suspend fun killByPid(force: Boolean): Boolean =
    when (val processManagement = processManagement) {
      // Windows has no graceful termination of an arbitrary process by pid, so `destroy()` there behaves like `destroyForcibly()`.
      is EelProcessManagementWindowsApi -> processManagement.kill(pidValue)
      is EelProcessManagementPosixApi -> if (force) processManagement.kill(pidValue) else processManagement.terminate(pidValue)
    }

  private suspend fun isAliveSuspend(): Boolean {
    val ownProcess = ownProcess
    if (ownProcess != null) {
      return when (ownProcess.exitCode.state) {
        SafeDeferred.State.Active -> true
        is SafeDeferred.State.Canceled, is SafeDeferred.State.Completed<*>, is SafeDeferred.State.Failed -> false
      }
    }
    return processInfoFor(pidValue) != null
  }

  private fun handleFor(info: EelProcessInfo): IjentChildProcessHandlerAdapter =
    IjentChildProcessHandlerAdapter(processManagement, coroutineScope, info.pid.value, ownProcess = null, cachedInfo = info)

  private fun currentSnapshot(): List<EelProcessInfo> = runBlockingInScope { currentSnapshotSuspend() }

  private suspend fun currentSnapshotSuspend(): List<EelProcessInfo> =
    try {
      processManagement.listProcesses()
    }
    catch (_: UnsupportedOperationException) {
      emptyList()
    }

  private suspend fun processInfoFor(pid: Long): EelProcessInfo? =
    try {
      processManagement.processInfo(pid)
    }
    catch (_: UnsupportedOperationException) {
      null
    }

  private fun <T> runBlockingInScope(body: suspend () -> T): T =
    @Suppress("SSBasedInspection") runBlocking(coroutineScope.coroutineContext) { body() }

  private class IjentProcessHandleInfo(private val info: EelProcessInfo?, argumentsResolver: () -> List<String>) : ProcessHandle.Info {
    private val arguments: List<String> by lazy(argumentsResolver)

    override fun command(): Optional<String> = Optional.ofNullable(info?.executable)

    override fun commandLine(): Optional<String> {
      val exe = info?.executable ?: return Optional.empty()
      return Optional.of(if (arguments.isEmpty()) exe else "$exe ${arguments.joinToString(" ")}")
    }

    override fun arguments(): Optional<Array<String>> =
      if (arguments.isEmpty()) Optional.empty() else Optional.of(arguments.toTypedArray())

    override fun startInstant(): Optional<Instant> = Optional.ofNullable(info?.startInstant)

    override fun totalCpuDuration(): Optional<Duration> = Optional.empty()

    override fun user(): Optional<String> = Optional.ofNullable(info?.user)

    override fun toString(): String = "IjentProcessHandleInfo(info=$info)"
  }

  companion object {
    private val POLL_INTERVAL = 100.milliseconds

    /**
     * On Windows a process is not reparented to pid 1 when its parent exits, and pids are reused, so a stale parent pid may match an
     * unrelated, newly started process. A real child cannot have been started before its parent, so a candidate that is older than the
     * parent is rejected. When either start time is unknown (`null`) the candidate is accepted.
     */
    private fun isPlausibleChild(parentStart: Instant?, child: EelProcessInfo): Boolean {
      val childStart = child.startInstant ?: return true
      parentStart ?: return true
      return !childStart.isBefore(parentStart)
    }
  }
}
