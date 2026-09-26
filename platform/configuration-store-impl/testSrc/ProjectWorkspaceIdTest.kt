// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectWorkspaceIdTest {
  @Test
  fun `accepts a Ksuid-shaped id`() {
    val id = ProjectWorkspaceId.parseOrNull("0ujtsYcgvSTl8PAuAdqWYSMnLOv")
    assertNotNull(id)
    assertEquals("0ujtsYcgvSTl8PAuAdqWYSMnLOv", id!!.value)
  }

  @Test
  fun `generate produces a parseable id`() {
    val generated = ProjectWorkspaceId.generate()
    assertNotNull(ProjectWorkspaceId.parseOrNull(generated.value))
  }

  @Test
  fun `rejects a path-traversal value`() {
    assertNull(ProjectWorkspaceId.parseOrNull("../../../tmp/evil"))
  }

  @Test
  fun `rejects values with separators or dots`() {
    assertNull(ProjectWorkspaceId.parseOrNull("a/b"))
    assertNull(ProjectWorkspaceId.parseOrNull("a\\b"))
    assertNull(ProjectWorkspaceId.parseOrNull("a.b"))
  }

  @Test
  fun `rejects a UUID`() {
    // A UUID contains '-', which is outside the Ksuid (Base62) alphabet.
    assertNull(ProjectWorkspaceId.parseOrNull("123e4567-e89b-12d3-a456-426614174000"))
  }

  @Test
  fun `rejects an empty value`() {
    assertNull(ProjectWorkspaceId.parseOrNull(""))
  }
}