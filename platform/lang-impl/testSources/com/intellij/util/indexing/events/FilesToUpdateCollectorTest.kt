// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Verifies that collector snapshots expose a continuous boundary while request versions remain internal. */
@TestApplication
internal class FilesToUpdateCollectorTest {
  /** Ensures each cursor reads only the suffix published after its boundary. */
  @Test
  fun `snapshot contains only requests published after cursor`() {
    val collector = FilesToUpdateCollector()
    val initialSnapshot = collector.collectRequestsNewerThan(0)
    assertEquals(0, initialSnapshot.readUpToVersion(), "An empty collector must publish the initial boundary")
    assertEquals(emptyList<FileIndexingRequest>(), initialSnapshot.requests(), "An empty collector must not expose requests")

    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.collectRequestsNewerThan(initialSnapshot.readUpToVersion())
    assertEquals(1, firstSnapshot.readUpToVersion(), "The first request must advance the publication boundary")
    assertEquals(listOf(firstRequest), firstSnapshot.requests(), "The first suffix must contain the first published request")

    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    val secondSnapshot = collector.collectRequestsNewerThan(firstSnapshot.readUpToVersion())
    assertEquals(2, secondSnapshot.readUpToVersion(), "The next request must advance the publication boundary again")
    assertEquals(listOf(secondRequest), secondSnapshot.requests(), "The next suffix must exclude requests covered by the cursor")
    assertEquals(listOf(firstRequest), firstSnapshot.requests(), "A later publication must not mutate an earlier snapshot")

    val exhaustedSnapshot = collector.collectRequestsNewerThan(secondSnapshot.readUpToVersion())
    assertEquals(2, exhaustedSnapshot.readUpToVersion(), "Reading an exhausted suffix must keep the current boundary")
    assertEquals(emptyList<FileIndexingRequest>(), exhaustedSnapshot.requests(), "A cursor at the boundary must produce an empty suffix")
  }

  /** Ensures the boundary can skip an unchanged collector without hiding older pending requests after a new publication. */
  @Test
  fun `snapshot without version filtering contains all current requests after boundary changes`() {
    val collector = FilesToUpdateCollector()
    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstBoundary = collector.collectRequestsNewerThan(0).readUpToVersion()

    val unchangedSnapshot = collector.collectRequestsNewerThan(firstBoundary, false)
    assertEquals(emptyList<FileIndexingRequest>(), unchangedSnapshot.requests(),
                 "An unchanged publication boundary must produce an empty snapshot")

    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    val changedSnapshot = collector.collectRequestsNewerThan(firstBoundary, false)

    assertEquals(setOf(firstRequest, secondRequest), changedSnapshot.requests().toSet(),
                 "A changed boundary without version filtering must expose all current requests")
  }

  /** Ensures replacing the current request publishes a new generation for the same file ID. */
  @Test
  fun `rescheduled file is visible after previous boundary`() {
    val collector = FilesToUpdateCollector()
    val file = TestVirtualFile("file.txt", 1)
    val firstRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.collectRequestsNewerThan(0)

    val rescheduledRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(rescheduledRequest, emptySet(), emptyList())
    val rescheduledSnapshot = collector.collectRequestsNewerThan(firstSnapshot.readUpToVersion())

    assertEquals(2, rescheduledSnapshot.readUpToVersion(), "Rescheduling must publish a new generation")
    assertEquals(1, rescheduledSnapshot.requests().size, "The suffix must expose only the current request for a file")
    assertSame(rescheduledRequest, rescheduledSnapshot.requests().single(), "The suffix must expose the rescheduled request instance")
  }

  /** Ensures a snapshot cannot observe the collector while a request publication is incomplete. */
  @Test
  fun `snapshot waits for request publication boundary`() {
    val collector = FilesToUpdateCollector()
    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("file.txt", 1))
    val dirtyUpdateStarted = CountDownLatch(1)
    val allowDirtyUpdate = CountDownLatch(1)
    val blockingProjects = object : AbstractCollection<Project>() {
      override val size: Int = 0

      override fun iterator(): Iterator<Project> {
        dirtyUpdateStarted.countDown()
        assertTrue(allowDirtyUpdate.await(10, TimeUnit.SECONDS), "The test must release the blocked publication")
        return emptyList<Project>().iterator()
      }
    }
    val executor = Executors.newFixedThreadPool(2)

    try {
      val scheduleFuture = executor.submit {
        collector.scheduleForUpdate(request, emptySet(), blockingProjects)
      }
      assertTrue(dirtyUpdateStarted.await(10, TimeUnit.SECONDS), "Scheduling must reach the dirty metadata update")

      val snapshotStarted = CountDownLatch(1)
      val snapshotFuture = executor.submit<FilesToUpdateCollector.RequestsSnapshot> {
        snapshotStarted.countDown()
        collector.collectRequestsNewerThan(0)
      }
      assertTrue(snapshotStarted.await(10, TimeUnit.SECONDS), "The snapshot task must start while publication is blocked")
      assertThrows<TimeoutException>("A snapshot must not complete at an intermediate publication boundary") {
        snapshotFuture.get(100, TimeUnit.MILLISECONDS)
      }

      allowDirtyUpdate.countDown()
      scheduleFuture.get(10, TimeUnit.SECONDS)
      val snapshot = snapshotFuture.get(10, TimeUnit.SECONDS)
      assertEquals(1, snapshot.readUpToVersion(), "The completed snapshot must include the published boundary")
      assertEquals(listOf(request), snapshot.requests(), "The completed snapshot must include the request published at its boundary")
    }
    finally {
      allowDirtyUpdate.countDown()
      executor.shutdownNow()
    }
  }

  /** Supplies a stable persistent ID without creating a real VFS file. */
  private class TestVirtualFile(name: String, private val id: Int) : LightVirtualFile(name), VirtualFileWithId {
    override fun getId(): Int = id
  }
}
