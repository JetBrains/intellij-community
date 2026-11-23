// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.codeInsight.multiverse.CodeInsightContextProvider.Answer
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.project.processIndexedContentFilesByType
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class CodeInsightContextFusCollectorTest {

  /** One module, one content root and one source root. No root holds two or more contexts. */
  @Nested
  inner class WithoutOverlappingRoot {
    private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
      module("m0") {
        contentRoot("content") {
          sourceRoot("src") {
            repeat(FILES_OF_ONE_TYPE) { index -> file("f$index.txt", "a") }
          }
        }
      }
    }
    private val project by projectFixture

    /**
     * Without a root of two or more contexts the collector asks no provider, so the read action count follows the
     * batch count of the index read. The walk of the pre-change collector took two read actions per file.
     */
    @Test
    fun `takes a read action per batch of files, not per file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      prepare(project)

      val counter = ReadActionCounter()
      val counterDisposable = Disposer.newDisposable()
      val events = try {
        ApplicationManagerEx.getApplicationEx().addReadActionListener(counter, counterDisposable)
        CodeInsightContextFusCollector().collect(project)
      }
      finally {
        Disposer.dispose(counterDisposable)
      }

      assertEquals(mapOf(1 to StatisticsUtil.roundToPowerOfTwo(FILES_OF_ONE_TYPE)), countsOfEvents(events)["PLAIN_TEXT"].orEmpty())
      assertTrue(
        counter.count <= MAX_READ_ACTIONS,
        "$FILES_OF_ONE_TYPE files took ${counter.count} read actions, and the limit is $MAX_READ_ACTIONS",
      )
    }

    /** A provider besides the platform one does not follow the root rule, so the collector asks it for each file. */
    @Test
    fun `a provider besides the project model one is asked for each indexed file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      prepare(project)
      val indexedFiles = countIndexedFiles(project)
      val provider = CountingContextProvider()
      val providerDisposable = Disposer.newDisposable()
      try {
        withContext(Dispatchers.EDT) {
          CodeInsightContextManager.getInstance(project).registerTestOnlyCodeInsightContextProvider(provider, providerDisposable)
        }
        CodeInsightContextFusCollector().collect(project)
      }
      finally {
        Disposer.dispose(providerDisposable)
      }

      assertEquals(indexedFiles, provider.count)
    }

    /**
     * A cancel of the collection stops the batch at the next file. Without a check per file, the collection asks the
     * provider for each file of the batch, and the batch holds 1,000 files.
     */
    @Test
    fun `a cancel stops the batch at the next file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      prepare(project)
      val provider = CountingContextProvider()
      val providerDisposable = Disposer.newDisposable()
      try {
        withContext(Dispatchers.EDT) {
          CodeInsightContextManager.getInstance(project).registerTestOnlyCodeInsightContextProvider(provider, providerDisposable)
        }
        val collection = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
          CodeInsightContextFusCollector().collect(project)
        }
        provider.onCall = { collection.cancel() }
        collection.join()
        assertTrue(collection.isCancelled, "The collection is not cancelled")
      }
      finally {
        Disposer.dispose(providerDisposable)
      }

      assertEquals(1, provider.count)
    }

    /**
     * A cancel stops the index read at the next file, also with a processor that checks no cancellation. The batch
     * holds 1,000 files, so a check per batch only would give the processor 1,000 files.
     */
    @Test
    fun `a cancel stops the index read at the next file`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      prepare(project)
      val processed = AtomicInteger()
      val read = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
        processIndexedContentFilesByType(project) { _, _ ->
          processed.incrementAndGet()
          coroutineContext.job.cancel()
        }
      }
      read.join()

      assertTrue(read.isCancelled, "The index read is not cancelled")
      assertEquals(1, processed.get())
    }
  }

  /** The module `m0` owns three source roots. Two other modules share `s0`, and one more shares `s1`. */
  @Nested
  inner class WithOverlappingRoots {
    private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
      module("m0") {
        contentRoot("content") {
          sourceRoot("s0", "s0") {
            file("a.txt", "a")
            file("b.txt", "b")
            file("c.xml", "<c/>")
          }
          sourceRoot("s1", "s1") {
            file("d.txt", "d")
            file("e.xml", "<e/>")
          }
          sourceRoot("s2") {
            file("f.txt", "f")
          }
        }
      }
      module("sharesS0First") { sharedSourceRoot("s0") }
      module("sharesS0Second") { sharedSourceRoot("s0") }
      module("sharesS1") { sharedSourceRoot("s1") }
    }
    private val project by projectFixture

    /** The root rule must give the counts of the per-file walk of the pre-change collector. */
    @Test
    fun `overlapping roots give the counts of the per-file walk`(): Unit = timeoutRunBlocking(timeout = 10.minutes) {
      prepare(project)
      val expected = countsOfPerFileWalk(project)

      val events = CodeInsightContextFusCollector().collect(project)

      assertTrue(expected.isNotEmpty(), "The per-file walk found no indexed file")
      assertTrue(expected.values.any { counts -> counts.keys.any { it >= 2 } }, "No file has two or more contexts")
      assertEquals(expected, countsOfEvents(events))
    }
  }
}

/** The counts per file type and per context count, as the pre-change collector computed them. */
private suspend fun countsOfPerFileWalk(project: Project): Map<String, Map<Int, Int>> {
  val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
  val counts = HashMap<String, HashMap<Int, Int>>()
  withContext(Dispatchers.Default) {
    processIndexedContentFilesByType(project) { fileType, file ->
      val contextCount = contextManager.getCodeInsightContextsWithoutCaching(file).size
      counts.getOrPut(fileType.name) { HashMap() }.merge(contextCount, 1, Int::plus)
    }
  }
  return counts.mapValues { entry -> entry.value.mapValues { StatisticsUtil.roundToPowerOfTwo(it.value) } }
}

private fun countsOfEvents(events: Set<MetricEvent>): Map<String, Map<Int, Int>> {
  val counts = HashMap<String, HashMap<Int, Int>>()
  for (event in events.filter { it.eventId == "context_report" }) {
    val data = event.data.build()
    counts.getOrPut(data["file_type"] as String) { HashMap() }[data["context_number"] as Int] = data["file_number"] as Int
  }
  return counts
}

private suspend fun countIndexedFiles(project: Project): Int {
  val count = AtomicInteger()
  withContext(Dispatchers.Default) {
    processIndexedContentFilesByType(project) { _, _ -> count.incrementAndGet() }
  }
  return count.get()
}

/** The fixture writes the files to the disk after the VFS has seen their directory, so the test refreshes it. */
private suspend fun prepare(project: Project) {
  val roots = readAction { ProjectRootManager.getInstance(project).contentRoots.toList() }
  withContext(Dispatchers.IO) {
    for (root in roots) {
      root.refresh(false, true)
    }
  }
  IndexingTestUtil.suspendUntilIndexesAreReady(project)
}

/** Counts the outermost read actions of each thread. */
private class ReadActionCounter : ReadActionListener {
  private val depth = ThreadLocal.withInitial { 0 }
  private val counter = AtomicInteger()

  val count: Int get() = counter.get()

  override fun readActionStarted(action: Class<*>) {
    depth.set(depth.get() + 1)
  }

  override fun readActionFinished(action: Class<*>) {
    val left = depth.get() - 1
    depth.set(left)
    if (left == 0) {
      counter.incrementAndGet()
    }
  }
}

/** Owns no file, and counts the calls. It stands for the Rust and the Radler providers. */
private class CountingContextProvider : CodeInsightContextProvider {
  private val counter = AtomicInteger()

  /** Runs on each call, after the count. */
  @Volatile
  var onCall: () -> Unit = {}

  val count: Int get() = counter.get()

  override fun getContexts(file: VirtualFile, project: Project): Answer {
    counter.incrementAndGet()
    onCall()
    return Answer.NotOwned
  }

  override fun isOwnerOf(context: CodeInsightContext): Boolean = false

  override fun subscribeToChanges(project: Project, invalidator: CodeInsightContextProvider.Invalidator) {}
}

private const val FILES_OF_ONE_TYPE = 1200

/** One read action for the roots, one for the index keys, and for each indexed type one for the iterator and one per batch. */
private const val MAX_READ_ACTIONS = 25
