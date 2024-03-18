// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.platform.util.coroutines.flow.throttle
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.ConcurrentLinkedQueue

internal class FileTypeIndexChangeNotifier(private val syncPublisher: FileTypeIndex.IndexChangeListener) : AutoCloseable {
  private val sendNotificationsFlow = MutableSharedFlow<Unit>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val queue = ConcurrentLinkedQueue<FileType>()

  // TODO maybe inject coroutine scope through indexes
  @OptIn(DelicateCoroutinesApi::class)
  private val worker = GlobalScope.launch(CoroutineName("FileTypeIndex change notificator")) {
    sendNotificationsFlow
      .throttle(1)
      .collectLatest {
        notifyPending()
      }
  }

  @Synchronized
  fun notifyPending() {
    val fileTypes = hashSetOf<FileType>()
    while (true) {
      fileTypes.add(queue.poll() ?: break)
    }
    for (fileType in fileTypes) {
      try {
        syncPublisher.onChangedForFileType(fileType)
      }
      catch (e: Throwable) {
        logger<FileTypeIndexChangeNotifier>().error(e)
      }
    }
  }

  fun enqueueNotification(fileType: FileType) {
    queue.add(fileType)
    sendNotificationsFlow.tryEmit(Unit)
  }

  fun clearPending() {
    queue.clear()
  }

  override fun close() {
    runBlocking {
      worker.cancelAndJoin()
    }
    notifyPending()
  }
}