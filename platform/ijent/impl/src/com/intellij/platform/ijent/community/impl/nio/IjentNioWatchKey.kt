// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.fs.EelFileSystemApi.FileChangeType
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

internal class IjentNioWatchKey(
  private val watchable: Path,
  private val watchService: IjentNioWatchService,
) : WatchKey {
  private val events = ConcurrentLinkedQueue<WatchEvent<*>>()
  private val valid = AtomicBoolean(true)

  fun addEvent(kind: WatchEvent.Kind<Path>, context: Path) {
    events.add(SimpleWatchEvent(kind, context))
  }

  override fun isValid(): Boolean = valid.get()

  override fun pollEvents(): List<WatchEvent<*>> {
    val result = mutableListOf<WatchEvent<*>>()
    while (true) {
      val event = events.poll() ?: break
      result.add(event)
    }
    return result
  }

  override fun reset(): Boolean = valid.get()

  override fun cancel() {
    if (valid.compareAndSet(true, false)) {
      watchService.cancelKey(this)
    }
  }

  override fun watchable(): Path = watchable

  companion object {
    fun toWatchEventKind(type: FileChangeType): WatchEvent.Kind<Path> = when (type) {
      FileChangeType.CREATED -> StandardWatchEventKinds.ENTRY_CREATE
      FileChangeType.DELETED -> StandardWatchEventKinds.ENTRY_DELETE
      FileChangeType.CHANGED -> StandardWatchEventKinds.ENTRY_MODIFY
    }
  }
}

private class SimpleWatchEvent<T>(
  private val kind: WatchEvent.Kind<T>,
  private val context: T,
) : WatchEvent<T> {
  override fun kind(): WatchEvent.Kind<T> = kind
  override fun count(): Int = 1
  override fun context(): T = context
}