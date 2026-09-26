// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.EelMachineWithConnectionState
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What the IDE knows about a path of a run configuration.
 *
 * A property is `null` while the answer is unknown. A caller must report a problem only for `false`.
 */
@ApiStatus.Internal
class PathVerdict internal constructor(val exists: Boolean?, val executable: Boolean?) {
  internal fun sameAs(other: PathVerdict?): Boolean = other != null && exists == other.exists && executable == other.executable

  internal companion object {
    val UNKNOWN: PathVerdict = PathVerdict(null, null)
  }
}

/**
 * Reports a new verdict of [AsyncPathVerdictCache] for a path.
 *
 * A listener runs on a background thread. It must switch to the thread it needs by itself.
 */
@ApiStatus.Internal
fun interface PathVerdictListener {
  /**
   * Tells that the cache holds a new verdict for [path].
   *
   * [RunConfiguration.checkConfiguration] can now report a problem it could not see before, so a caller revalidates the configuration.
   */
  fun verdictChanged(path: Path)
}

/**
 * Answers the existence question and the executable question for a path without a file system call on the calling thread.
 *
 * [RunConfiguration.checkConfiguration] can run after each typed character, so it must not touch the file system.
 * A path inside WSL turns each probe into a blocking IJent call under a read lock, which freezes the IDE.
 * This cache answers from the VFS and from a stored verdict, and it refreshes the verdict on a background thread.
 *
 * A probe waits for a quiet period of [DebounceDelay] ms, so a typed character does not start a probe of an intermediate path.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class AsyncPathVerdictCache(private val coroutineScope: CoroutineScope) {
  private val cache = ConcurrentHashMap<Path, Entry>()
  private val inFlight = ConcurrentHashMap.newKeySet<Path>()
  private val requested = ConcurrentHashMap<Path, TimeMark>()
  private val debounceLock = Any()
  private var debounceJob: Job? = null

  /**
   * Returns the known verdict for [path] at once.
   *
   * The call schedules a background probe when the stored verdict is absent or older than the time to live.
   */
  fun getVerdict(path: Path): PathVerdict {
    val entry = cache[path]
    if (entry == null || entry.probeTime.elapsedNow() > TimeToLive) {
      requestProbe(path)
    }
    val verdict = entry?.verdict ?: PathVerdict.UNKNOWN
    if (verdict.exists == null && isCachedInVfs(path)) {
      return PathVerdict(true, verdict.executable)
    }
    return verdict
  }

  /**
   * Remembers the request for [path] and restarts the quiet period.
   *
   * The probe starts only for a path that the last request burst still asks about.
   */
  private fun requestProbe(path: Path) {
    if (requested.size > MAX_ENTRIES) {
      requested.clear()
    }
    requested[path] = TimeSource.Monotonic.markNow()
    synchronized(debounceLock) {
      debounceJob?.cancel()
      debounceJob = coroutineScope.launch(Dispatchers.IO) {
        delay(DebounceDelay)
        for (fresh in takeFreshRequests()) {
          probeInBackground(fresh)
        }
      }
    }
  }

  /**
   * Drains every request and returns the paths of the last request burst.
   *
   * A path that no later request repeats belongs to an intermediate state of the typed text, so the cache drops it.
   */
  private fun takeFreshRequests(): List<Path> {
    val snapshot = requested.keys.toList()
    val ages = snapshot.mapNotNull { path -> requested[path]?.let { path to it.elapsedNow() } }
    val newest = ages.minOfOrNull { it.second } ?: return emptyList()
    for (path in snapshot) {
      requested.remove(path)
    }
    return ages.filter { it.second - newest <= DebounceDelay }.map { it.first }
  }

  private fun probeInBackground(path: Path) {
    if (!inFlight.add(path)) return
    coroutineScope.launch(Dispatchers.IO) {
      try {
        val verdict = probe(path)
        val previous = cache[path]?.verdict
        if (cache.size > MAX_ENTRIES) {
          cache.clear()
        }
        cache[path] = Entry(verdict, TimeSource.Monotonic.markNow())
        if (!verdict.sameAs(previous) && !(previous == null && verdict.sameAs(PathVerdict.UNKNOWN))) {
          fireVerdictChanged(path)
        }
      }
      finally {
        inFlight.remove(path)
      }
    }
  }

  /**
   * Probes [path] with at most two calls. The second call runs only for a path that exists.
   */
  private fun probe(path: Path): PathVerdict {
    if (!isEnvironmentStarted(path)) return PathVerdict.UNKNOWN
    return try {
      if (Files.exists(path)) PathVerdict(true, Files.isExecutable(path)) else PathVerdict(false, false)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      thisLogger().debug("Cannot probe $path", e)
      PathVerdict.UNKNOWN
    }
  }

  /**
   * Tells whether a probe of [path] reaches its environment without a start of that environment.
   *
   * A validation call must never start a WSL distribution or a container.
   */
  private fun isEnvironmentStarted(path: Path): Boolean {
    val descriptor = path.getEelDescriptor()
    if (descriptor === LocalEelDescriptor) return true
    val machine = descriptor.getResolvedEelMachine() ?: return false
    return machine !is EelMachineWithConnectionState || machine.isConnected
  }

  private fun fireVerdictChanged(path: Path) {
    val application = ApplicationManager.getApplication()
    if (application == null || application.isDisposed) return
    application.messageBus.syncPublisher(VERDICT_TOPIC).verdictChanged(path)
  }

  private fun isCachedInVfs(path: Path): Boolean {
    val file = LocalFileSystem.getInstance().findFileByPathIfCached(FileUtil.toSystemIndependentName(path.toString()))
    return file != null && file.isValid
  }

  private class Entry(val verdict: PathVerdict, val probeTime: TimeMark)

  companion object {
    private val TimeToLive = 5.seconds
    private val DebounceDelay = 300.milliseconds
    private const val MAX_ENTRIES = 200

    /**
     * Fires when a background probe stores a verdict that differs from the previous one.
     */
    @Topic.AppLevel
    @JvmField
    val VERDICT_TOPIC: Topic<PathVerdictListener> = Topic(PathVerdictListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstance(): AsyncPathVerdictCache = ApplicationManager.getApplication().service<AsyncPathVerdictCache>()
  }
}
