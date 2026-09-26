// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import com.intellij.platform.eel.fs.EelFileSystemApi.PathChange
import com.intellij.platform.eel.fs.UnwatchOptionsBuilder
import com.intellij.platform.eel.fs.WatchOptionsBuilder
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal class IjentNioWatchService(
  private val ijentFs: IjentFileSystemApi,
  private val nioFs: IjentNioFileSystem,
) : WatchService {
  private val signalQueue = LinkedBlockingQueue<WatchKey>()
  private val keys = ConcurrentHashMap<EelPath, IjentNioWatchKey>()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile
  private var closed = false
  private var changeFlow: Flow<PathChange>? = null
  @Volatile
  private var collectJob: Job? = null

  /**
   * Ensures that [EelFileSystemApi.watchChanges] has been called and the flow collection coroutine is running.
   * This must be called before [EelFileSystemApi.addWatchRoots] to match the EEL API contract
   * (see [com.intellij.openapi.vfs.impl.eel.EelFileWatcher.setupWatcherJob]).
   */
  private fun ensureCollecting() {
    if (collectJob != null) return
    synchronized(this) {
      if (collectJob != null) return
      // Call watchChanges() synchronously to establish the subscription BEFORE addWatchRoots() is called.
      // This matches the pattern in EelFileWatcher.setupWatcherJob().
      val flow = ijentFs.fsBlocking { watchChanges() }
      changeFlow = flow
      collectJob = scope.launch {
        try {
          flow.collect { change ->
            dispatchChange(change)
          }
        }
        catch (e: UnsupportedOperationException) {
          LOG.warn("EEL watch not supported for ${ijentFs.descriptor}", e)
        }
        catch (e: Exception) {
          if (!closed) {
            LOG.warn("EEL watch flow terminated unexpectedly", e)
          }
        }
      }
    }
  }

  private fun dispatchChange(change: PathChange) {
    val changePath = try {
      EelPath.parse(change.path, ijentFs.descriptor)
    }
    catch (e: Exception) {
      LOG.debug("Cannot parse changed path: ${change.path}", e)
      return
    }

    val parentPath = changePath.parent ?: return
    val key = keys[parentPath] ?: return
    if (!key.isValid) return

    val fileName = changePath.fileName
    val nioFileName = nioFs.getPath(fileName)
    val kind = IjentNioWatchKey.toWatchEventKind(change.type)
    key.addEvent(kind, nioFileName)
    signalQueue.put(key)
  }

  fun register(path: Path, events: Array<out WatchEvent.Kind<*>>): WatchKey {
    if (closed) throw ClosedWatchServiceException()

    val eelPath = path.toEelPath()
    val key = IjentNioWatchKey(path, this)
    keys[eelPath] = key

    // ensureCollecting() calls watchChanges() synchronously, so the subscription is set up
    // before addWatchRoots() is called below.
    ensureCollecting()

    val watchedChangeTypes = mutableSetOf<FileChangeType>()
    for (event in events) {
      when (event) {
        StandardWatchEventKinds.ENTRY_CREATE -> watchedChangeTypes.add(FileChangeType.CREATED)
        StandardWatchEventKinds.ENTRY_DELETE -> watchedChangeTypes.add(FileChangeType.DELETED)
        StandardWatchEventKinds.ENTRY_MODIFY -> watchedChangeTypes.add(FileChangeType.CHANGED)
      }
    }

    ijentFs.fsBlocking {
      ijentFs.addWatchRoots(
        WatchOptionsBuilder()
          .changeTypes(watchedChangeTypes)
          .paths(setOf(EelFileSystemApi.WatchedPath.from(eelPath)))
          .build()
      )
    }

    return key
  }

  internal fun cancelKey(key: IjentNioWatchKey) {
    val eelPath = (key.watchable() as? AbsoluteIjentNioPath)?.eelPath ?: return
    keys.remove(eelPath)
    try {
      ijentFs.fsBlocking {
        ijentFs.unwatch(UnwatchOptionsBuilder(eelPath).build())
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to unwatch $eelPath", e)
    }
  }

  override fun poll(): WatchKey? {
    checkOpen()
    val key = signalQueue.poll() ?: return null
    if (key === CLOSE_SENTINEL) throw ClosedWatchServiceException()
    return key
  }

  override fun poll(timeout: Long, unit: TimeUnit): WatchKey? {
    checkOpen()
    val key = signalQueue.poll(timeout, unit) ?: return null
    if (key === CLOSE_SENTINEL) throw ClosedWatchServiceException()
    return key
  }

  override fun take(): WatchKey {
    checkOpen()
    val key = signalQueue.take()
    if (key === CLOSE_SENTINEL) throw ClosedWatchServiceException()
    return key
  }

  override fun close() {
    if (closed) return
    closed = true
    scope.cancel()
    for ((eelPath, _) in keys) {
      try {
        ijentFs.fsBlocking {
          ijentFs.unwatch(UnwatchOptionsBuilder(eelPath).build())
        }
      }
      catch (_: Exception) {
      }
    }
    keys.clear()
    // Unblock any waiting take()/poll() calls
    signalQueue.put(CLOSE_SENTINEL)
  }

  private fun checkOpen() {
    if (closed) throw ClosedWatchServiceException()
  }

  companion object {
    private val LOG = logger<IjentNioWatchService>()

    private val CLOSE_SENTINEL = object : WatchKey {
      override fun isValid(): Boolean = false
      override fun pollEvents(): List<WatchEvent<*>> = emptyList()
      override fun reset(): Boolean = false
      override fun cancel() {}
      override fun watchable(): Path? = null
    }
  }
}