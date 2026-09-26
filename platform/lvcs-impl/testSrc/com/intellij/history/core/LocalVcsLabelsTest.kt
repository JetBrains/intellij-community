// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.getByteContentBefore
import com.intellij.platform.lvcs.impl.RevisionId
import org.junit.Test
import java.nio.charset.StandardCharsets

class LocalVcsLabelsTest : LocalHistoryTestCase() {
  private val facade = createInMemoryFacade()
  private val root = RootEntry()

  override fun nextId(): Long {
    return facade.changeListInTests.nextId()
  }

  @Test
  fun testUserLabels() {
    add(facade, createFile(root, "file"))
    facade.putUserLabel("1", "project")
    add(facade, changeContent(root, "file", null))
    facade.putUserLabel("2", "project")

    val changes = collectChanges(facade, "file", "project", null)
    assertEquals(4, changes.size.toLong())

    assertEquals("2", changes[0].label)
    assertNull(changes[1].label)
    assertEquals("1", changes[2].label)
    assertNull(changes[3].label)
  }

  @Test
  fun testLabelTimestamps() {
    setCurrentTimestamp(10)
    add(facade, createFile(root, "file"))

    setCurrentTimestamp(20)
    facade.putUserLabel("", "project")

    setCurrentTimestamp(30)
    facade.putUserLabel("", "project")

    val changes = collectChanges(facade, "file", "project", null)
    assertEquals(30, changes[0].timestamp)
    assertEquals(20, changes[1].timestamp)
    assertEquals(10, changes[2].timestamp)
  }

  @Test
  fun testContent() {
    add(facade, createFile(root, "file", "one"))
    facade.putUserLabel("", "project")
    add(facade, changeContent(root, "file", "two"))
    facade.putUserLabel("", "project")

    val changes = collectChanges(facade, "file", "project", null)

    assertContent("two", getEntryFor(facade, root, RevisionId.Current, "file"))
    assertContent("one", getEntryFor(facade, root, RevisionId.ChangeSet(changes[1].id), "file"))
  }

  @Test
  fun testGlobalUserLabels() {
    add(facade, createFile(root, "one"))
    facade.putUserLabel("1", "project")
    add(facade, createFile(root, "two"))
    facade.putUserLabel("2", "project")

    var changes = collectChanges(facade, "one", "project", null)
    assertEquals(3, changes.size.toLong())
    assertEquals("2", changes[0].label)
    assertEquals("1", changes[1].label)

    changes = collectChanges(facade, "two", "project", null)
    assertEquals(2, changes.size.toLong())
    assertEquals("2", changes[0].label)
  }

  @Test
  fun testGlobalLabelTimestamps() {
    setCurrentTimestamp(10)
    add(facade, createFile(root, "file"))
    setCurrentTimestamp(20)
    facade.putUserLabel("", "project")

    val changes = collectChanges(facade, "file", "project", null)
    assertEquals(20, changes[0].timestamp)
    assertEquals(10, changes[1].timestamp)
  }

  @Test
  fun testLabelsDuringChangeSet() {
    add(facade, createFile(root, "file"))
    facade.beginChangeSet()
    add(facade, changeContent(root, "file", null))
    facade.putUserLabel("label", "project")
    facade.endChangeSet("changeSet")

    val changes = collectChanges(facade, "file", "project", null)
    assertEquals(2, changes.size.toLong())
    assertEquals("changeSet", changes[0].name)
    assertNull(changes[1].name)
  }

  @Test
  fun testSystemLabels() {
    facade.created("f1", false)
    facade.created("f2", false)

    setCurrentTimestamp(123)
    facade.putSystemLabel("label", "project", 456)

    val changes1 = collectChanges(facade, "f1", "project", null)
    val changes2 = collectChanges(facade, "f2", "project", null)
    assertEquals(2, changes1.size.toLong())
    assertEquals(2, changes2.size.toLong())

    assertEquals("label", changes1[0].label)
    assertEquals("label", changes2[0].label)

    val r = changes1[0]
    assertEquals(123, r.timestamp)
    assertEquals(456, r.labelColor.toLong())
  }

  @Test
  fun testGettingByteContent() {
    val l1 = facade.putSystemLabel("label", "project", -1)
    add(facade, createFile(root, "f", "one"))

    val l2 = facade.putSystemLabel("label", "project", -1)
    add(facade, changeContent(root, "f", "two"))

    val l3 = facade.putSystemLabel("label", "project", -1)

    assertNull(facade.getByteContentBefore(root, "f", l1.id).bytes)
    assertEquals("one", String(facade.getByteContentBefore(root, "f", l2.id).bytes, StandardCharsets.UTF_8))
    assertEquals("two", String(facade.getByteContentBefore(root, "f", l3.id).bytes, StandardCharsets.UTF_8))

    add(facade, createDirectory(root, "dir"))
    val l4 = facade.putSystemLabel("label", "project", -1)

    assertTrue(facade.getByteContentBefore(root, "dir", l4.id).isDirectory)
    assertNull(facade.getByteContentBefore(root, "dir", l4.id).bytes)
  }

  @Test
  fun testGettingByteContentInsideChangeSet() {
    facade.beginChangeSet()
    add(facade, createFile(root, "f", "one"))
    val l1 = facade.putSystemLabel("label", "project", -1)
    add(facade, changeContent(root, "f", "two"))
    val l2 = facade.putSystemLabel("label", "project", -1)
    facade.endChangeSet(null)

    assertEquals("one", String(facade.getByteContentBefore(root, "f", l1.id).bytes, StandardCharsets.UTF_8))
    assertEquals("two", String(facade.getByteContentBefore(root, "f", l2.id).bytes, StandardCharsets.UTF_8))
  }

  @Test
  fun testGettingByteContentAfterRename() {
    add(facade, createFile(root, "f", "one"))
    val l1 = facade.putSystemLabel("label", "project", -1)

    add(facade, changeContent(root, "f", "two"))
    val l2 = facade.putSystemLabel("label", "project", -1)
    add(facade, rename(root, "f", "f_r"))

    val l3 = facade.putSystemLabel("label", "project", -1)

    assertEquals("one", String(facade.getByteContentBefore(root, "f_r", l1.id).bytes, StandardCharsets.UTF_8))
    assertEquals("two", String(facade.getByteContentBefore(root, "f_r", l2.id).bytes, StandardCharsets.UTF_8))
    assertEquals("two", String(facade.getByteContentBefore(root, "f_r", l3.id).bytes, StandardCharsets.UTF_8))
  }
}