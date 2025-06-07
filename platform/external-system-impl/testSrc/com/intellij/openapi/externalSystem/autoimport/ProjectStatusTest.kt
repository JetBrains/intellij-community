// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.*
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectState.*
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import org.junit.Assert.*
import org.junit.Test

class ProjectStatusTest {
  @Test
  fun `test open project with broken state`() {
    val status = ProjectStatus()
    status.markDirty(Stamp.nextStamp()) as Dirty
    status.markModified(Stamp.nextStamp()) as Dirty
    assertFalse(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test generate project with broken state`() {
    val status = ProjectStatus()
    status.markModified(Stamp.nextStamp()) as Modified
    status.markDirty(Stamp.nextStamp()) as Dirty
    assertFalse(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test generate project with lag and broken state`() {
    val stamp1 = Stamp.nextStamp()
    val stamp2 = Stamp.nextStamp()
    val stamp3 = Stamp.nextStamp()

    val status = ProjectStatus()
    status.markDirty(stamp1) as Dirty
    assertFalse(status.isUpToDate())
    status.markSynchronized(stamp3) as Synchronized
    assertTrue(status.isUpToDate())
    status.markModified(stamp2) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test delayed modification event`() {
    val stamp1 = Stamp.nextStamp()
    val stamp2 = Stamp.nextStamp()

    val status = ProjectStatus()
    status.markSynchronized(stamp2) as Synchronized
    status.markModified(stamp1) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test delayed invalidation event`() {
    val stamp1 = Stamp.nextStamp()
    val stamp2 = Stamp.nextStamp()

    val status = ProjectStatus()
    status.markSynchronized(stamp2) as Synchronized
    status.markDirty(stamp1) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test common sample`() {
    val status = ProjectStatus()
    status.markModified(Stamp.nextStamp()) as Modified
    status.markModified(Stamp.nextStamp()) as Modified
    status.markModified(Stamp.nextStamp()) as Modified
    assertFalse(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test revert changes`() {
    val status = ProjectStatus()
    status.markModified(Stamp.nextStamp()) as Modified
    status.markReverted(Stamp.nextStamp()) as Reverted
    assertTrue(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test revert dirty changes`() {
    val status = ProjectStatus()
    status.markModified(Stamp.nextStamp()) as Modified
    status.markDirty(Stamp.nextStamp()) as Dirty
    status.markReverted(Stamp.nextStamp()) as Dirty
    assertFalse(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test modification after revert event`() {
    val status = ProjectStatus()
    status.markModified(Stamp.nextStamp()) as Modified
    status.markReverted(Stamp.nextStamp()) as Reverted
    status.markModified(Stamp.nextStamp()) as Modified
    assertFalse(status.isUpToDate())
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertTrue(status.isUpToDate())
  }

  @Test
  fun `test modification types`() {
    val status = ProjectStatus()

    status.markModified(Stamp.nextStamp(), INTERNAL) as Modified
    assertEquals(INTERNAL, status.getModificationType())
    status.markModified(Stamp.nextStamp(), EXTERNAL) as Modified
    assertEquals(INTERNAL, status.getModificationType())

    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertEquals(UNKNOWN, status.getModificationType())
    status.markModified(Stamp.nextStamp(), EXTERNAL) as Modified
    assertEquals(EXTERNAL, status.getModificationType())
    status.markModified(Stamp.nextStamp(), INTERNAL) as Modified
    assertEquals(INTERNAL, status.getModificationType())

    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertEquals(UNKNOWN, status.getModificationType())
    status.markDirty(Stamp.nextStamp(), INTERNAL) as Dirty
    assertEquals(INTERNAL, status.getModificationType())
    status.markModified(Stamp.nextStamp(), EXTERNAL) as Dirty
    assertEquals(INTERNAL, status.getModificationType())
    status.markModified(Stamp.nextStamp(), INTERNAL) as Dirty
    assertEquals(INTERNAL, status.getModificationType())

    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    assertEquals(UNKNOWN, status.getModificationType())
    status.markDirty(Stamp.nextStamp(), EXTERNAL) as Dirty
    assertEquals(EXTERNAL, status.getModificationType())
    status.markModified(Stamp.nextStamp(), INTERNAL) as Dirty
    assertEquals(INTERNAL, status.getModificationType())
  }

  @Test
  fun `test tracking status after failed import`() {
    val status = ProjectStatus()

    status.markModified(Stamp.nextStamp(), INTERNAL) as Modified
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    status.markBroken(Stamp.nextStamp()) as Broken

    assertFalse(status.isUpToDate())
    assertFalse(status.isDirty())

    status.markModified(Stamp.nextStamp()) as Dirty
    status.markReverted(Stamp.nextStamp()) as Dirty
    status.markSynchronized(Stamp.nextStamp()) as Synchronized
    status.markBroken(Stamp.nextStamp()) as Broken

    assertFalse(status.isUpToDate())
    assertFalse(status.isDirty())

    val stamp1 = Stamp.nextStamp()
    val stamp2 = Stamp.nextStamp()
    val stamp3 = Stamp.nextStamp()
    val stamp4 = Stamp.nextStamp()

    status.markReverted(stamp1) as Broken
    status.markSynchronized(stamp2) as Synchronized
    status.markModified(stamp4) as Modified
    status.markBroken(stamp3) as Dirty

    assertFalse(status.isUpToDate())
    assertTrue(status.isDirty())
  }
}