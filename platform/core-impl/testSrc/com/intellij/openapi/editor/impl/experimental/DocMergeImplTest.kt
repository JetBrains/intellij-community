// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.experimental

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.experimental.Agent
import com.intellij.openapi.editor.ex.experimental.DocumentBranch
import com.intellij.openapi.editor.ex.experimental.DocMerge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests [DocMergeImpl]: ready ops, deferred ops, and deferred ops that many threads read at once.
 */
internal class DocMergeImplTest {

  @Test
  fun `ready ops come back as given`() {
    val ops = listOf(DocumentOp.insertOp(0, "x"))
    val merge = DocMerge.ready(BRANCH, ops)
    assertSame(BRANCH, merge.branch())
    assertSame(ops, merge.ops())
    assertEquals("DocMerge($BRANCH, 1 ops)", merge.toString())
  }

  @Test
  fun `deferred ops wait for the first call and are built once`() {
    val builds = AtomicInteger()
    val ops = listOf(DocumentOp.deleteOp(0, 1))
    val merge = DocMerge.deferred(BRANCH) {
      builds.incrementAndGet()
      ops
    }
    assertSame(BRANCH, merge.branch())
    // Neither the branch nor a message may build the ops.
    assertEquals("DocMerge($BRANCH, ops deferred)", merge.toString())
    assertEquals(0, builds.get())
    assertSame(ops, merge.ops())
    assertSame(ops, merge.ops())
    assertEquals(1, builds.get())
    assertEquals("DocMerge($BRANCH, 1 ops)", merge.toString())
  }

  /**
   * The publication mode lets two threads build at once, but every caller must get the list that
   * won. Each build returns a list of its own and waits until a second build has started, so the
   * builds overlap for sure. A lazy value without thread safety would then hand out both lists.
   */
  @Test
  fun `threads that read deferred ops at once all get one list`() {
    val pool = Executors.newFixedThreadPool(THREADS)
    var overlaps = 0
    try {
      repeat(ROUNDS) {
        val builds = AtomicInteger()
        val twoBuilds = CountDownLatch(2)
        val merge = DocMerge.deferred(BRANCH) {
          builds.incrementAndGet()
          twoBuilds.countDown()
          // A lock that allowed one build only would time out here, and still give one list.
          twoBuilds.await(BUILD_WAIT_MILLIS, TimeUnit.MILLISECONDS)
          listOf(DocumentOp.insertOp(0, "x"))
        }
        val start = CountDownLatch(1)
        val results = (0 until THREADS).map {
          pool.submit<List<DocumentOp.Text>> {
            start.await()
            merge.ops()
          }
        }
        start.countDown()
        val lists = results.map { it.get(10, TimeUnit.SECONDS) }
        for (list in lists) {
          assertSame(lists[0], list)
        }
        assertSame(lists[0], merge.ops())
        if (builds.get() >= 2) {
          overlaps++
        }
      }
    } finally {
      pool.shutdownNow()
    }
    // Without overlapping builds the test would prove nothing.
    assertTrue(overlaps > 0) { "No round built the ops twice at once" }
  }

  private companion object {
    val BRANCH: DocumentBranch = DocumentBranch.createBranch("abc", Agent.createAgent("a"))
    const val THREADS = 8
    const val ROUNDS = 50

    /**
     * How long a build waits for a second build to start.
     */
    const val BUILD_WAIT_MILLIS = 2_000L
  }
}
