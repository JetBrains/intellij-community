// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.INTERNAL
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AsyncFileChangesListenerTest {

  @Test
  fun `reports matching file change`(): Unit = runBlocking {
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(AsyncSupplier.blocking { setOf("/a.txt") }, recording, asDisposable())

    listener.init()
    listener.onFileChange("/a.txt", 1L, EXTERNAL)
    listener.apply()

    assertThat(recording.changes).containsExactly(ReceivedChange("/a.txt", 1L, EXTERNAL))
    assertThat(recording.initCount).isEqualTo(1)
    assertThat(recording.applyCount).isEqualTo(1)
  }

  @Test
  fun `no callback when no files changed`(): Unit = runBlocking {
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(AsyncSupplier.blocking { setOf("/a.txt") }, recording, asDisposable())

    listener.init()
    listener.apply()

    assertThat(recording.changes).isEmpty()
    assertThat(recording.initCount).isEqualTo(0)
    assertThat(recording.applyCount).isEqualTo(0)
  }

  @Test
  fun `no callback when changed file is not watched`(): Unit = runBlocking {
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(AsyncSupplier.blocking { emptySet() }, recording, asDisposable())

    listener.init()
    listener.onFileChange("/a.txt", 1L, EXTERNAL)
    listener.apply()

    assertThat(recording.changes).isEmpty()
    assertThat(recording.initCount).isEqualTo(0)
    assertThat(recording.applyCount).isEqualTo(0)
  }

  @Test
  fun `init discards changes accumulated in previous cycle`(): Unit = runBlocking {
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(AsyncSupplier.blocking { setOf("/a.txt", "/b.txt") }, recording, asDisposable())

    // Cycle 1 — change /a.txt, never apply (abandoned)
    listener.init()
    listener.onFileChange("/a.txt", 1L, EXTERNAL)

    // Cycle 2 — fresh start, only /b.txt
    listener.init()
    listener.onFileChange("/b.txt", 2L, INTERNAL)
    listener.apply()

    assertThat(recording.changes).containsExactly(ReceivedChange("/b.txt", 2L, INTERNAL))
  }

  @Test
  fun `last change for the same path wins within a cycle`(): Unit = runBlocking {
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(AsyncSupplier.blocking { setOf("/a.txt") }, recording, asDisposable())

    listener.init()
    listener.onFileChange("/a.txt", 1L, EXTERNAL)
    listener.onFileChange("/a.txt", 2L, INTERNAL) // overrides previous stamp and type
    listener.apply()

    assertThat(recording.changes).containsExactly(ReceivedChange("/a.txt", 2L, INTERNAL))
  }

  @Test
  fun `delayed supplier sees snapshot from its own apply call`(): Unit = runBlocking {
    val supplier = DeferredSupplier(setOf("/a.txt", "/b.txt"))
    val recording = RecordingListener()
    val listener = AsyncFileChangesListener(supplier, recording, asDisposable())

    // Cycle 1: change /a.txt, apply — supplier queued but not yet fired
    listener.init()
    listener.onFileChange("/a.txt", 1L, EXTERNAL)
    listener.apply()
    assertThat(supplier.pendingCount).isEqualTo(1)

    // Cycle 2: change /b.txt, apply — supplier queued again, cycle 1 still pending
    listener.init()
    listener.onFileChange("/b.txt", 2L, INTERNAL)
    listener.apply()
    assertThat(supplier.pendingCount).isEqualTo(2)

    // Nothing delivered yet
    assertThat(recording.changes).isEmpty()

    // Fire cycle 1's supplier — must see only /a.txt (snapshot captured at cycle 1's apply)
    supplier.deliverNext()
    assertThat(recording.changes).containsExactly(ReceivedChange("/a.txt", 1L, EXTERNAL))
    assertThat(recording.initCount).isEqualTo(1)
    assertThat(recording.applyCount).isEqualTo(1)

    recording.reset()

    // Fire cycle 2's supplier — must see only /b.txt (snapshot captured at cycle 2's apply)
    supplier.deliverNext()
    assertThat(recording.changes).containsExactly(ReceivedChange("/b.txt", 2L, INTERNAL))
    assertThat(recording.initCount).isEqualTo(1)
    assertThat(recording.applyCount).isEqualTo(1)
  }

  private data class ReceivedChange(
    val path: String,
    val modificationStamp: Long,
    val modificationType: ExternalSystemModificationType,
  )

  private class RecordingListener : FilesChangesListener {

    val changes = mutableListOf<ReceivedChange>()
    var initCount = 0
    var applyCount = 0

    fun reset() {
      changes.clear()
      initCount = 0
      applyCount = 0
    }

    override fun init() {
      initCount++
    }

    override fun apply() {
      applyCount++
    }

    override fun onFileChange(stamp: Stamp, path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
      changes.add(ReceivedChange(path, modificationStamp, modificationType))
    }
  }

  /** Queues supply() callbacks; lets the test deliver results one at a time. */
  private class DeferredSupplier<R>(private val value: R) : AsyncSupplier<R> {

    private val pending = ArrayDeque<(R) -> Unit>()
    val pendingCount get() = pending.size

    override fun supply(parentDisposable: Disposable, consumer: (R) -> Unit) {
      pending.addLast(consumer)
    }

    fun deliverNext() {
      pending.removeFirst()(value)
    }
  }
}
