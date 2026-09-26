// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class VisitedRequestsVersionPerProjectTest {
  /** Ensures registration starts uninitialized and cursor advancement is monotonic. */
  @Test
  fun `registered cursor advances monotonically`() {
    val registry = FilesToUpdateCollector.VisitedRequestsVersionPerProject()
    val project = mock<Project>()

    registry.registerProject(project)
    assertEquals(-1, registry.cursorFor(project, -1), "A newly registered project must require its first full pass")

    registry.advanceCursor(project, 5)
    assertEquals(5, registry.cursorFor(project, -1), "A successful pass must publish its processed boundary")

    registry.advanceCursor(project, 3)
    assertEquals(5, registry.cursorFor(project, -1), "An older completion must not move the cursor backwards")

    registry.registerProject(project)
    assertEquals(5, registry.cursorFor(project, -1), "Duplicate registration must not reset an initialized cursor")
  }

  /** Ensures the minimum is available only when every participating project has initialized its cursor. */
  @Test
  fun `minimum cursor follows registered project lifecycle`() {
    val registry = FilesToUpdateCollector.VisitedRequestsVersionPerProject()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()

    assertTrue(registry.minimumCursor().isEmpty, "No projects must not imply a cleanup boundary")
    registry.registerProject(firstProject)
    registry.registerProject(secondProject)
    registry.advanceCursor(firstProject, 5)
    assertTrue(registry.minimumCursor().isEmpty, "An uninitialized project must block the shared cleanup boundary")

    registry.advanceCursor(secondProject, 8)
    assertEquals(5, registry.minimumCursor().asLong, "The slowest project must define the shared cleanup boundary")

    registry.advanceCursor(firstProject, 10)
    assertEquals(8, registry.minimumCursor().asLong, "The minimum must follow cursor advancement")

    registry.unregisterProject(secondProject)
    assertEquals(10, registry.minimumCursor().asLong, "A closed project must stop constraining the shared boundary")
  }

  /** Ensures a new project invalidates a minimum until its cursor becomes initialized. */
  @Test
  fun `new project invalidates initialized minimum`() {
    val registry = FilesToUpdateCollector.VisitedRequestsVersionPerProject()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()

    registry.registerProject(firstProject)
    registry.advanceCursor(firstProject, 5)
    assertEquals(5, registry.minimumCursor().asLong, "The initialized project must define the first minimum")

    registry.registerProject(secondProject)
    assertTrue(registry.minimumCursor().isEmpty, "The new uninitialized project must invalidate the cached minimum")
    registry.advanceCursor(secondProject, 8)
    assertEquals(5, registry.minimumCursor().asLong, "The earlier project must define the minimum after initialization")
  }

  /** Ensures equal minimum cursors constrain the minimum until each slow project advances. */
  @Test
  fun `equal minimum advances after every slow project`() {
    val registry = FilesToUpdateCollector.VisitedRequestsVersionPerProject()
    val firstProject = mock<Project>()
    val secondProject = mock<Project>()

    registry.registerProject(firstProject)
    registry.registerProject(secondProject)
    registry.advanceCursor(firstProject, 5)
    registry.advanceCursor(secondProject, 5)

    registry.advanceCursor(firstProject, 8)
    assertEquals(5, registry.minimumCursor().asLong, "The second project must keep the shared minimum at its cursor")

    registry.advanceCursor(secondProject, 7)
    assertEquals(7, registry.minimumCursor().asLong, "The smaller advanced cursor must become the new minimum")
  }

  /** Ensures a completion racing with project close cannot recreate a removed registry entry. */
  @Test
  fun `advance does not resurrect unregistered project`() {
    val registry = FilesToUpdateCollector.VisitedRequestsVersionPerProject()
    val project = mock<Project>()
    registry.registerProject(project)
    registry.unregisterProject(project)

    registry.advanceCursor(project, 5)
    registry.registerProject(project)

    assertEquals(-1, registry.cursorFor(project, -1), "A completion after close must not restore a cursor")
    assertTrue(registry.minimumCursor().isEmpty, "A re-registered project must require its first full pass")
  }
}
