// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
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
    val project = mock<Project>()
    collector.registerProject(project)
    val initialSnapshot = collector.requestsFor(project, true)
    assertEquals(0, initialSnapshot.readUpToVersion(), "An empty collector must publish the initial boundary")
    assertEquals(emptyList<FileIndexingRequest>(), initialSnapshot.requests(), "An empty collector must not expose requests")

    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.requestsFor(project, true)
    assertEquals(1, firstSnapshot.readUpToVersion(), "The first request must advance the publication boundary")
    assertEquals(listOf(firstRequest), firstSnapshot.requests(), "The first suffix must contain the first published request")
    collector.advanceWithoutProcessing(project, firstSnapshot)

    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    val secondSnapshot = collector.requestsFor(project, true)
    assertEquals(2, secondSnapshot.readUpToVersion(), "The next request must advance the publication boundary again")
    assertEquals(listOf(secondRequest), secondSnapshot.requests(), "The next suffix must exclude requests covered by the cursor")
    assertEquals(listOf(firstRequest), firstSnapshot.requests(), "A later publication must not mutate an earlier snapshot")
    collector.advanceWithoutProcessing(project, secondSnapshot)

    val exhaustedSnapshot = collector.requestsFor(project, true)
    assertEquals(2, exhaustedSnapshot.readUpToVersion(), "Reading an exhausted suffix must keep the current boundary")
    assertEquals(emptyList<FileIndexingRequest>(), exhaustedSnapshot.requests(), "A cursor at the boundary must produce an empty suffix")
  }

  /** Ensures the boundary can skip an unchanged collector without hiding older pending requests after a new publication. */
  @Test
  fun `snapshot without version filtering contains all current requests after boundary changes`() {
    val collector = FilesToUpdateCollector(false)
    val project = mock<Project>()
    collector.registerProject(project)
    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.requestsFor(project, false)
    collector.advanceWithoutProcessing(project, firstSnapshot)

    val unchangedSnapshot = collector.requestsFor(project, false)
    assertEquals(emptyList<FileIndexingRequest>(), unchangedSnapshot.requests(),
                 "An unchanged publication boundary must produce an empty snapshot")

    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    val changedSnapshot = collector.requestsFor(project, false)

    assertEquals(setOf(firstRequest, secondRequest), changedSnapshot.requests().toSet(),
                 "A changed boundary without version filtering must expose all current requests")
  }

  /** Ensures replacing the current request publishes a new generation for the same file ID. */
  @Test
  fun `rescheduled file is visible after previous boundary`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    val file = TestVirtualFile("file.txt", 1)
    val firstRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.requestsFor(project, true)
    collector.advanceWithoutProcessing(project, firstSnapshot)

    val rescheduledRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(rescheduledRequest, emptySet(), emptyList())
    val rescheduledSnapshot = collector.requestsFor(project, true)

    assertEquals(2, rescheduledSnapshot.readUpToVersion(), "Rescheduling must publish a new generation")
    assertEquals(1, rescheduledSnapshot.requests().size, "The suffix must expose only the current request for a file")
    assertSame(rescheduledRequest, rescheduledSnapshot.requests().single(), "The suffix must expose the rescheduled request instance")
  }

  /** Ensures concurrent registration creates one dirty-file entry. */
  @Test
  fun `concurrent project registration is idempotent`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(2)

    try {
      val registrations = List(2) {
        executor.submit {
          assertTrue(start.await(10, TimeUnit.SECONDS), "Both registration tasks must start together")
          collector.registerProject(project)
        }
      }
      start.countDown()
      registrations.forEach { it.get(10, TimeUnit.SECONDS) }

      assertEquals(listOf(project), collector.dirtyFiles.getProjects(), "Concurrent registration must create one dirty-file entry")
    }
    finally {
      start.countDown()
      executor.shutdownNow()
    }
  }

  /** Ensures the range reports one consistent cursor and publication state. */
  @Test
  fun `cursor range reports current boundaries`() {
    val collector = FilesToUpdateCollector()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()
    collector.registerProject(firstProject)
    collector.registerProject(secondProject)
    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    collector.scheduleForUpdate(request, emptySet(), emptyList())

    assertTrue(collector.rangeOfVersions().minimumCursor().isEmpty,
               "An uninitialized project must prevent a minimum cursor")
    assertEquals(1, collector.rangeOfVersions().publishedVersion(),
                 "The first request must advance the publication version")

    collector.advanceWithoutProcessing(firstProject, collector.requestsFor(firstProject, true))
    collector.advanceWithoutProcessing(secondProject, collector.requestsFor(secondProject, true))
    val initializedRange = collector.rangeOfVersions()
    assertEquals(1, initializedRange.minimumCursor().asLong, "The range must report the slowest project cursor")
    assertEquals(1, initializedRange.publishedVersion(), "Cursor advancement must preserve the publication version")

    collector.scheduleForUpdate(FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2)), emptySet(), emptyList())
    val advancedRange = collector.rangeOfVersions()
    assertEquals(1, advancedRange.minimumCursor().asLong, "A new request must not advance a project cursor")
    assertEquals(2, advancedRange.publishedVersion(), "A new request must advance the publication version")
  }

  /** Ensures identity distinguishes equal request instances published for successive generations. */
  @Test
  fun `old request instance does not remove rescheduled request`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    val file = TestVirtualFile("file.txt", 1)
    val firstRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    val firstSnapshot = collector.requestsFor(project, true)
    val firstScheduledInstance = firstSnapshot.requests().single()
    collector.advanceWithoutProcessing(project, firstSnapshot)

    val secondRequest = FileIndexingRequest.deleteRequest(file)
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    val secondScheduledInstance = collector.requestsFor(project, true).requests().single()

    assertEquals(firstScheduledInstance, secondScheduledInstance,
                 "Equal file requests must remain serialized across schedule generations")
    assertNotSame(firstScheduledInstance, secondScheduledInstance, "Each publication must have a distinct request identity")
    assertFalse(collector.removeIfCurrent(firstScheduledInstance), "Completion of the old generation must not remove its replacement")
    assertTrue(collector.isCurrent(secondScheduledInstance), "The replacement must remain current after stale completion")
    assertEquals(listOf(secondRequest), collector.filesToUpdate.toList(), "The replacement must remain scheduled")
    assertTrue(collector.removeIfCurrent(secondScheduledInstance), "Completion of the current generation must remove its request")
    assertEquals(emptyList<FileIndexingRequest>(), collector.filesToUpdate.toList(), "Current completion must empty the collector")
  }

  /** Ensures each project can consume its own suffix while requests remain available to projects that lag behind. */
  @Test
  fun `project cursors select independent request suffixes`() {
    val collector = FilesToUpdateCollector()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()
    collector.registerProject(firstProject)
    collector.registerProject(secondProject)

    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("file.txt", 1))
    collector.scheduleForUpdate(request, emptySet(), emptyList())

    val firstSnapshot = collector.requestsFor(firstProject, true)
    collector.advanceWithoutProcessing(firstProject, firstSnapshot)
    val repeatedFirstSnapshot = collector.requestsFor(firstProject, true)
    val secondSnapshot = collector.requestsFor(secondProject, true)

    assertEquals(emptyList<FileIndexingRequest>(), repeatedFirstSnapshot.requests(),
                 "A project at the publication boundary must not reconsider an old request")
    assertEquals(listOf(request), secondSnapshot.requests(), "A project with an uninitialized cursor must still receive the request")
    assertEquals(listOf(request), collector.filesToUpdate.toList(),
                 "Advancing one project must not remove work needed by another project")
  }

  /** Ensures collection removes the inclusive cleanup boundary and preserves later requests. */
  @Test
  fun `collection removes requests through cleanup boundary`() {
    val collector = FilesToUpdateCollector()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()
    collector.registerProject(firstProject)
    collector.registerProject(secondProject)
    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(firstProject, collector.requestsFor(firstProject, true))
    collector.advanceWithoutProcessing(secondProject, collector.requestsFor(secondProject, true))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())

    val snapshot = collector.requestsFor(firstProject, true)

    assertEquals(listOf(secondRequest), snapshot.requests(), "The snapshot must contain only requests after the project cursor")
    assertEquals(1, snapshot.droppedRequestsBeforeVersion(), "The snapshot must report the applied cleanup boundary")
    assertEquals(1, snapshot.droppedRequestsCount(), "The collection pass must remove the request at the cleanup boundary")
    assertFalse(collector.containsFileId(1), "The request covered by both projects must be removed")
    assertFalse(collector.dirtyFiles.getProjectDirtyFiles(null)!!.containsFile(1),
                "Cleanup must remove dirty metadata together with its request")
    assertTrue(collector.containsFileId(2), "A request after the cleanup boundary must remain available")
    assertEquals(2, snapshot.readUpToVersion(), "Cleanup must preserve the publication boundary")
  }

  /** Ensures a restricted collection accepts a cleanup boundary after its synthetic cursor. */
  @Test
  fun `restricted collection permits cleanup after synthetic cursor`() {
    val collector = FilesToUpdateCollector()
    val registeredProject = mock<Project>()
    val unregisteredProject = mock<Project>()
    collector.registerProject(registeredProject)
    val coveredRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("covered.txt", 1))
    val pendingRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("pending.txt", 2))
    collector.scheduleForUpdate(coveredRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(registeredProject, collector.requestsFor(registeredProject, false))
    collector.scheduleForUpdate(pendingRequest, emptySet(), emptyList())

    val snapshot = assertDoesNotThrow {
      collector.requestsFor(unregisteredProject, false)
    }

    assertEquals(listOf(pendingRequest), snapshot.requests(), "The restricted collection must retain requests after the cleanup boundary")
    assertEquals(1, snapshot.droppedRequestsCount(), "The restricted collection must remove the covered request")
  }

  /** Ensures project removal applies the new shared cleanup boundary. */
  @Test
  fun `project removal cleans requests through remaining cursor`() {
    val collector = FilesToUpdateCollector()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()
    val uninitializedProject = mock<Project>()
    collector.registerProject(firstProject)
    collector.registerProject(secondProject)
    collector.registerProject(uninitializedProject)
    val firstRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("first.txt", 1))
    val secondRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("second.txt", 2))
    collector.scheduleForUpdate(firstRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(firstProject, collector.requestsFor(firstProject, true))
    collector.advanceWithoutProcessing(secondProject, collector.requestsFor(secondProject, true))
    collector.scheduleForUpdate(secondRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(firstProject, collector.requestsFor(firstProject, true))

    collector.unregisterProject(uninitializedProject)

    assertFalse(collector.containsFileId(1), "Project removal must clean the request at the remaining cursor")
    assertFalse(collector.dirtyFiles.getProjectDirtyFiles(null)!!.containsFile(1),
                "Project removal must clean dirty metadata with its request")
    assertTrue(collector.containsFileId(2), "Project removal must preserve requests after the remaining cursor")
  }

  /** Ensures cleanup removes a covered request and preserves a later generation of another file. */
  @Test
  fun `cleanup preserves rescheduled request beyond cursor`() {
    val collector = FilesToUpdateCollector()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()
    collector.registerProject(firstProject)
    collector.registerProject(secondProject)
    val rescheduledFile = TestVirtualFile("rescheduled.txt", 1)
    val coveredFile = TestVirtualFile("covered.txt", 2)
    collector.scheduleForUpdate(FileIndexingRequest.deleteRequest(rescheduledFile), emptySet(), emptyList())
    collector.scheduleForUpdate(FileIndexingRequest.deleteRequest(coveredFile), emptySet(), emptyList())
    collector.advanceWithoutProcessing(firstProject, collector.requestsFor(firstProject, true))
    collector.advanceWithoutProcessing(secondProject, collector.requestsFor(secondProject, true))
    val rescheduledRequest = FileIndexingRequest.deleteRequest(rescheduledFile)
    collector.scheduleForUpdate(rescheduledRequest, emptySet(), emptyList())

    val snapshot = collector.requestsFor(firstProject, true)

    assertEquals(listOf(rescheduledRequest), snapshot.requests(), "The new generation must remain visible after the project cursor")
    assertEquals(1, snapshot.droppedRequestsCount(), "Cleanup must remove the covered request at the cleanup boundary")
    assertFalse(collector.containsFileId(coveredFile.id), "Cleanup must remove the covered request")
    assertEquals(listOf(rescheduledRequest), collector.filesToUpdate.toList(),
                 "The current generation beyond the cursor must remain scheduled")
  }

  /** Ensures the unchanged publication fast path does not scan the request map for cleanup. */
  @Test
  fun `unchanged publication skips cleanup traversal`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("file.txt", 1))
    collector.scheduleForUpdate(request, emptySet(), emptyList())
    collector.advanceWithoutProcessing(project, collector.requestsFor(project, true))

    val snapshot = collector.requestsFor(project, true)

    assertEquals(emptyList<FileIndexingRequest>(), snapshot.requests(), "The current cursor must produce an empty snapshot")
    assertEquals(0, snapshot.droppedRequestsCount(), "The fast path must not traverse the request map for cleanup")
    assertTrue(collector.isCurrent(request), "The request must remain until a later collection traverses the request map")
  }

  /** Ensures an unregistered project gets every request after the shared cleanup boundary. */
  @Test
  fun `unregistered project reads remaining requests after cleanup`() {
    val collector = FilesToUpdateCollector()
    val registeredProject = mock<Project>()
    val unregisteredProject = mock<Project>()
    collector.registerProject(registeredProject)
    val coveredRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("covered.txt", 1))
    collector.scheduleForUpdate(coveredRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(registeredProject, collector.requestsFor(registeredProject, true))

    val pendingRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("pending.txt", 2))
    collector.scheduleForUpdate(pendingRequest, emptySet(), emptyList())
    val snapshot = collector.requestsFor(unregisteredProject, true)

    assertEquals(listOf(pendingRequest), snapshot.requests(),
                 "The unregistered project must receive every request after cleanup")
    assertFalse(collector.isCurrent(coveredRequest), "The read must remove the request before the shared cursor")
    assertTrue(collector.isCurrent(pendingRequest), "The read must preserve the request after the shared cursor")
  }

  /** Ensures a null project gets every request after the shared cleanup boundary. */
  @Test
  fun `null project reads remaining requests after cleanup`() {
    val collector = FilesToUpdateCollector()
    val registeredProject = mock<Project>()
    collector.registerProject(registeredProject)
    val coveredRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("covered.txt", 1))
    collector.scheduleForUpdate(coveredRequest, emptySet(), emptyList())
    collector.advanceWithoutProcessing(registeredProject, collector.requestsFor(registeredProject, true))

    val pendingRequest = FileIndexingRequest.deleteRequest(TestVirtualFile("pending.txt", 2))
    collector.scheduleForUpdate(pendingRequest, emptySet(), emptyList())
    val snapshot = collector.requestsFor(null, true)

    assertEquals(listOf(pendingRequest), snapshot.requests(), "A null project must receive every request after cleanup")
    assertFalse(collector.isCurrent(coveredRequest), "The read must remove the request before the shared cursor")
    assertTrue(collector.isCurrent(pendingRequest), "The read must preserve the request after the shared cursor")
  }

  /** Ensures a snapshot cannot observe the collector while a request publication is incomplete. */
  @Test
  fun `snapshot waits for request publication boundary`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
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
        collector.requestsFor(project, true)
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

  /** Ensures cursor metrics cannot observe an incomplete request publication. */
  @Test
  fun `cursor range waits for request publication boundary`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    collector.advanceWithoutProcessing(project, collector.requestsFor(project, true))
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

      val rangeFuture = executor.submit<FilesToUpdateCollector.CursorsRange> {
        collector.rangeOfVersions()
      }
      assertThrows<TimeoutException>("The cursor range must not expose an incomplete publication") {
        rangeFuture.get(100, TimeUnit.MILLISECONDS)
      }

      allowDirtyUpdate.countDown()
      scheduleFuture.get(10, TimeUnit.SECONDS)
      val range = rangeFuture.get(10, TimeUnit.SECONDS)
      assertEquals(0, range.minimumCursor().asLong, "The range must contain the cursor from the completed state")
      assertEquals(1, range.publishedVersion(), "The range must contain the completed publication boundary")
    }
    finally {
      allowDirtyUpdate.countDown()
      executor.shutdownNow()
    }
  }

  /** Ensures a snapshot from an old registration cannot advance a new cursor. */
  @Test
  fun `old snapshot does not advance new registration`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("file.txt", 1))
    collector.scheduleForUpdate(request, emptySet(), emptyList())
    val oldSnapshot = collector.requestsFor(project, true).filter { false }

    collector.unregisterProject(project)
    collector.registerProject(project)
    collector.advanceCursor(project, oldSnapshot)

    assertEquals(listOf(request), collector.requestsFor(project, true).requests(),
                 "The new registration must receive requests from before registration")
  }

  /** Ensures the collector advances a cursor only after every accepted request completes. */
  @Test
  fun `cursor waits for accepted requests`() {
    val collector = FilesToUpdateCollector()
    val project = mock<Project>()
    collector.registerProject(project)
    val request = FileIndexingRequest.deleteRequest(TestVirtualFile("file.txt", 1))
    collector.scheduleForUpdate(request, emptySet(), emptyList())
    val snapshot = collector.requestsFor(project, true).filter { true }

    collector.advanceCursor(project, snapshot)
    assertEquals(listOf(request), collector.requestsFor(project, true).requests(),
                 "A current accepted request must block cursor advancement")

    assertTrue(collector.removeIfCurrent(request), "The test must complete the accepted request")
    collector.advanceCursor(project, snapshot)
    assertEquals(emptyList<FileIndexingRequest>(), collector.requestsFor(project, true).requests(),
                 "The completed accepted request must allow cursor advancement")
  }

  /** Simulates a pass that accepts no requests and can advance its project cursor. */
  private fun FilesToUpdateCollector.advanceWithoutProcessing(project: Project, snapshot: FilesToUpdateCollector.RequestsSnapshot) {
    advanceCursor(project, snapshot.filter { false })
  }

  /** Supplies a stable persistent ID without creating a real VFS file. */
  private class TestVirtualFile(name: String, private val id: Int) : LightVirtualFile(name), VirtualFileWithId {
    override fun getId(): Int = id
  }
}
