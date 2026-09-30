// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * An explicit save cancels a running auto-save (IJPL-9299).
 * When that happens while the VFS events of a written storage file are applied,
 * the cancellation must not surface inside the write action, where VFS and PSI listeners call `checkCanceled`.
 * The saving coroutine itself observes the cancellation once the VFS operation has returned.
 */
@TestApplication
@Timeout(60)
internal class StorageWriteCancellationTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @TempDir
  private lateinit var tempDir: Path

  private val requestor = object : StorageManagerFileWriteRequestor {}

  /** The project save path: the file is written with NIO, and the collected events are applied by the refresh queue. */
  @Test
  fun `NIO write followed by refresh queue events`(): Unit = timeoutRunBlocking {
    val file = tempDir.resolve("test.xml")
    file.writeText("<a/>")
    val virtualFile = findFile(file)
    file.writeText("<b/>")

    val probe = CancellationProbe(file)
    runCancelledSave(probe) {
      RefreshQueue.getInstance().processEvents(listOf(updatingEvent(file, virtualFile)))
    }
    probe.awaitAfterEvent()

    probe.assertCancellationDidNotSurface()
    assertThat(virtualFile.contentsToByteArray().decodeToString()).isEqualTo("<b/>")
  }

  /** A direct VFS write under the write lock, as `XmlProjectFileManager` does it. */
  @Test
  fun `VFS content write`(): Unit = timeoutRunBlocking {
    val file = tempDir.resolve("test.xml")
    file.writeText("<a/>")
    val virtualFile = findFile(file)

    val probe = CancellationProbe(file)
    runCancelledSave(probe) {
      edtWriteAction {
        virtualFile.getOutputStream(requestor).use { it.write("<b/>".toByteArray()) }
      }
    }
    probe.awaitAfterEvent()

    probe.assertCancellationDidNotSurface()
    assertThat(file.readText()).isEqualTo("<b/>")
  }

  /** A direct VFS operation under the write lock, as `getOrCreateVirtualFile` does it. */
  @Test
  fun `VFS file creation`(): Unit = timeoutRunBlocking {
    val dir = findFile(tempDir)
    val file = tempDir.resolve("test.xml")

    val probe = CancellationProbe(file)
    runCancelledSave(probe) {
      edtWriteAction {
        dir.createChildData(requestor, file.fileName.toString())
      }
    }
    probe.awaitAfterEvent()

    probe.assertCancellationDidNotSurface()
    assertThat(dir.findChild(file.fileName.toString())).isNotNull()
  }

  private suspend fun findFile(path: Path): VirtualFile {
    return withContext(Dispatchers.EDT) {
      checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    }
  }

  /**
   * Runs [operation] in a coroutine that [probe] cancels from the VFS before-listener, and waits for the coroutine.
   * The VFS operation runs to its end under the write lock; the coroutine observes the cancellation when [operation] returns,
   * so each test checks the effect of the operation instead of a completion flag.
   */
  private suspend fun runCancelledSave(probe: CancellationProbe, operation: suspend () -> Unit) {
    // a cancelled child does not cancel the scope, so the test continues after the coroutine ends
    val savingJob = coroutineScope {
      launch {
        probe.cancelOnBeforeEvent(coroutineContext.job)
        operation()
      }
    }
    assertThat(savingJob.isCancelled).describedAs("saving job cancelled").isTrue()
  }

  /** Cancels [Job] from the VFS before-listener of the file and records what `checkCanceled` does in the after-listener. */
  private inner class CancellationProbe(file: Path) {
    private val path = file.invariantSeparatorsPathString
    private val nonCancellableInBefore = CopyOnWriteArrayList<Boolean>()
    private val afterCalls = AtomicInteger()
    private val checkCanceledErrors = CopyOnWriteArrayList<Throwable>()
    private val afterEvent = CompletableDeferred<Unit>()

    fun cancelOnBeforeEvent(job: Job) {
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun before(events: List<VFileEvent>) {
          if (events.any { it.path == path }) {
            job.cancel()
            nonCancellableInBefore.add(Cancellation.isInNonCancelableSection())
          }
        }

        override fun after(events: List<VFileEvent>) {
          if (events.any { it.path == path }) {
            afterCalls.incrementAndGet()
            try {
              ProgressManager.checkCanceled()
            }
            catch (e: Throwable) {
              checkCanceledErrors.add(e)
            }
            afterEvent.complete(Unit)
          }
        }
      })
    }

    suspend fun awaitAfterEvent() {
      afterEvent.await()
    }

    fun assertCancellationDidNotSurface() {
      assertThat(afterCalls.get()).describedAs("after-listener calls").isPositive()
      assertThat(checkCanceledErrors).describedAs("checkCanceled errors in the after-listener").isEmpty()
      assertThat(nonCancellableInBefore).describedAs("non-cancellable section in the before-listener").isNotEmpty().containsOnly(true)
    }
  }
}
