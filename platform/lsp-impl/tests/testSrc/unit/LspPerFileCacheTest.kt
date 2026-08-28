// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.cache.LspPerFileCache
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class LspPerFileCacheTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private suspend fun createFile(name: String, text: String): VirtualFile = withContext(Dispatchers.EDT) {
    codeInsightFixture.configureByText(name, text).virtualFile
  }

  @Test
  fun `equality matcher hits same key and misses different key`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "hello")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(file, 10, compute))
    assertEquals("value-1", cache.getOrCompute(file, 10, compute))
    assertEquals(1, calls)

    assertEquals("value-2", cache.getOrCompute(file, 20, compute))
    assertEquals(2, calls)
  }

  @Test
  fun `containment matcher reuses stored value for matching queries`() = timeoutRunBlocking {
    data class Span(val start: Int, val end: Int) { fun contains(i: Int) = i in start until end }
    val cache = LspPerFileCache<Int, Span>(
      project,
      matches = { _, storedValue, queriedKey -> storedValue.contains(queriedKey) },
    )
    val file = createFile("a.txt", "hello")
    var calls = 0
    val firstSpan = Span(10, 20)
    val secondSpan = Span(100, 200)

    assertEquals(firstSpan, cache.getOrCompute(file, 12) { calls++; firstSpan })
    assertEquals(firstSpan, cache.getOrCompute(file, 15) { calls++; secondSpan })
    assertEquals(1, calls)

    assertEquals(secondSpan, cache.getOrCompute(file, 150) { calls++; secondSpan })
    assertEquals(2, calls)
  }

  @Test
  fun `switching file evicts the previous entry`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val fileA = createFile("a.txt", "A")
    val fileB = createFile("b.txt", "B")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(fileA, 0, compute))
    assertEquals("value-2", cache.getOrCompute(fileB, 0, compute))
    assertEquals("value-3", cache.getOrCompute(fileA, 0, compute))
    assertEquals(3, calls)
  }

  @Test
  fun `PSI modification invalidates the slot`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "A")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(file, 0, compute))
    assertEquals("value-1", cache.getOrCompute(file, 0, compute))
    assertEquals(1, calls)

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        codeInsightFixture.editor.document.insertString(0, "X")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }

    assertEquals("value-2", cache.getOrCompute(file, 0, compute))
    assertEquals(2, calls)
  }

  @Test
  fun `Unit key works as file-level cache`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Unit, String>(project)
    val file = createFile("a.txt", "A")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(file, Unit, compute))
    assertEquals("value-1", cache.getOrCompute(file, Unit, compute))
    assertEquals(1, calls)
  }

  @Test
  fun `clearCache forces recompute`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "A")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(file, 0, compute))
    cache.clearCache()
    assertEquals("value-2", cache.getOrCompute(file, 0, compute))
  }

  @Test
  fun `null result from compute is not cached`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "A")
    var calls = 0
    val compute: () -> String? = { calls++; null }

    assertNull(cache.getOrCompute(file, 0, compute))
    assertNull(cache.getOrCompute(file, 0, compute))
    assertEquals(2, calls)
  }

  @Test
  fun `document-stamp cache survives a change in another file`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project, invalidateOnlyOnDocumentChange = true)
    val otherFile = codeInsightFixture.addFileToProject("other.txt", "other").virtualFile
    val file = createFile("a.txt", "A")
    var calls = 0
    val compute = { calls++; "value-$calls" }

    assertEquals("value-1", cache.getOrCompute(file, 0, compute))

    // A change in another file bumps the global PSI count but not this document's stamp.
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        FileDocumentManager.getInstance().getDocument(otherFile)!!.insertString(0, "x")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }
    assertEquals("value-1", cache.getOrCompute(file, 0, compute))
    assertEquals(1, calls)

    // A change in this document invalidates the slot.
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        codeInsightFixture.editor.document.insertString(0, "X")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }
    assertEquals("value-2", cache.getOrCompute(file, 0, compute))
    assertEquals(2, calls)
  }

  @Test
  fun `concurrent same-key callers coalesce into one compute`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "hello")
    val calls = AtomicInteger()
    val computeEntered = CountDownLatch(1)
    val releaseCompute = CountDownLatch(1)

    val first = async(Dispatchers.IO) {
      cache.getOrCompute(file, 0) {
        calls.incrementAndGet()
        computeEntered.countDown()
        releaseCompute.await()
        "shared"
      }
    }
    val second = async(Dispatchers.IO) {
      computeEntered.await() // the first compute is in flight now
      cache.getOrCompute(file, 0) { calls.incrementAndGet(); "second" }
    }

    delay(100.milliseconds) // give the second caller time to join the in-flight future
    releaseCompute.countDown()

    assertEquals("shared", first.await())
    assertEquals("shared", second.await())
    assertEquals(1, calls.get())
  }

  @Test
  fun `concurrent different-key caller reuses a containment match from the in-flight compute`() = timeoutRunBlocking {
    data class Span(val start: Int, val end: Int) { fun contains(i: Int) = i in start until end }
    val cache = LspPerFileCache<Int, Span>(
      project,
      matches = { _, storedValue, queriedKey -> storedValue.contains(queriedKey) },
    )
    val file = createFile("a.txt", "hello")
    val calls = AtomicInteger()
    val computeEntered = CountDownLatch(1)
    val releaseCompute = CountDownLatch(1)
    val span = Span(10, 20)

    val first = async(Dispatchers.IO) {
      cache.getOrCompute(file, 12) {
        calls.incrementAndGet()
        computeEntered.countDown()
        releaseCompute.await()
        span
      }
    }
    val second = async(Dispatchers.IO) {
      computeEntered.await() // the first compute is in flight now
      cache.getOrCompute(file, 15) { calls.incrementAndGet(); Span(100, 200) }
    }

    delay(100.milliseconds) // give the second caller time to join the in-flight future
    releaseCompute.countDown()

    assertEquals(span, first.await())
    assertEquals(span, second.await()) // waited for the in-flight result and got a containment hit
    assertEquals(1, calls.get())
  }

  @Test
  fun `waiter cancellation does not cancel the shared compute`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "hello")
    val calls = AtomicInteger()
    val computeEntered = CountDownLatch(1)
    val releaseCompute = CountDownLatch(1)

    val owner = async(Dispatchers.IO) {
      cache.getOrCompute(file, 0) {
        calls.incrementAndGet()
        computeEntered.countDown()
        releaseCompute.await()
        "shared"
      }
    }

    val waiterIndicator = EmptyProgressIndicator()
    val waiterGotPce = async(Dispatchers.IO) {
      computeEntered.await()
      try {
        ProgressManager.getInstance().runProcess(
          { cache.getOrCompute(file, 0) { calls.incrementAndGet(); "waiter" } },
          waiterIndicator,
        )
        false
      }
      catch (_: ProcessCanceledException) {
        true
      }
    }

    delay(100.milliseconds) // let the waiter join the in-flight future
    waiterIndicator.cancel()
    assertTrue(waiterGotPce.await(), "the waiter must be cancellable while the owner's request is in flight")

    releaseCompute.countDown()
    assertEquals("shared", owner.await())
    assertEquals(1, calls.get())

    // The owner's result must be cached despite the waiter's cancellation.
    assertEquals("shared", cache.getOrCompute(file, 0) { calls.incrementAndGet(); "recomputed" })
    assertEquals(1, calls.get())
  }

  @Test
  fun `owner exception clears the slot`() = timeoutRunBlocking {
    val cache = LspPerFileCache<Int, String>(project)
    val file = createFile("a.txt", "hello")
    val calls = AtomicInteger()

    try {
      cache.getOrCompute(file, 0) { calls.incrementAndGet(); throw IllegalStateException("simulated failure") }
    }
    catch (e: IllegalStateException) {
      assertEquals("simulated failure", e.message)
    }

    assertEquals("recovered", cache.getOrCompute(file, 0) { calls.incrementAndGet(); "recovered" })
    assertEquals(2, calls.get())
  }
}
