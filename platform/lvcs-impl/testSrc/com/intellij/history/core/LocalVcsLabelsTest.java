// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.platform.lvcs.impl.RevisionId;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class LocalVcsLabelsTest extends LocalHistoryTestCase {
  LocalHistoryFacade myVcs = new InMemoryLocalHistoryFacade();
  RootEntry myRoot = new RootEntry();

  @Override
  public long nextId() {
    return myVcs.getChangeListInTests().nextId();
  }

  @Test
  public void testUserLabels() {
    add(myVcs, createFile(myRoot, "file"));
    myVcs.putUserLabel("1", "project");
    add(myVcs, changeContent(myRoot, "file", null));
    myVcs.putUserLabel("2", "project");

    List<ChangeSet> changes = collectChanges(myVcs, "file", "project", null);
    assertEquals(4, changes.size());

    assertEquals("2", changes.get(0).getLabel());
    assertNull(changes.get(1).getLabel());
    assertEquals("1", changes.get(2).getLabel());
    assertNull(changes.get(3).getLabel());
  }

  @Test
  public void testLabelTimestamps() {
    setCurrentTimestamp(10);
    add(myVcs, createFile(myRoot, "file"));

    setCurrentTimestamp(20);
    myVcs.putUserLabel("", "project");

    setCurrentTimestamp(30);
    myVcs.putUserLabel("", "project");

    List<ChangeSet> changes = collectChanges(myVcs, "file", "project", null);
    assertEquals(30, changes.get(0).getTimestamp());
    assertEquals(20, changes.get(1).getTimestamp());
    assertEquals(10, changes.get(2).getTimestamp());
  }

  @Test
  public void testContent() {
    add(myVcs, createFile(myRoot, "file", "one"));
    myVcs.putUserLabel("", "project");
    add(myVcs, changeContent(myRoot, "file", "two"));
    myVcs.putUserLabel("", "project");

    List<ChangeSet> changes = collectChanges(myVcs, "file", "project", null);

    assertContent("two", getEntryFor(myVcs, myRoot, RevisionId.Current.INSTANCE, "file"));
    assertContent("one", getEntryFor(myVcs, myRoot, new RevisionId.ChangeSet(changes.get(1).getId()), "file"));
  }

  @Test
  public void testGlobalUserLabels() {
    add(myVcs, createFile(myRoot, "one"));
    myVcs.putUserLabel("1", "project");
    add(myVcs, createFile(myRoot, "two"));
    myVcs.putUserLabel("2", "project");

    List<ChangeSet> changes = collectChanges(myVcs, "one", "project", null);
    assertEquals(3, changes.size());
    assertEquals("2", changes.get(0).getLabel());
    assertEquals("1", changes.get(1).getLabel());

    changes = collectChanges(myVcs, "two", "project", null);
    assertEquals(2, changes.size());
    assertEquals("2", changes.get(0).getLabel());
  }

  @Test
  public void testGlobalLabelTimestamps() {
    setCurrentTimestamp(10);
    add(myVcs, createFile(myRoot, "file"));
    setCurrentTimestamp(20);
    myVcs.putUserLabel("", "project");

    List<ChangeSet> changes = collectChanges(myVcs, "file", "project", null);
    assertEquals(20, changes.get(0).getTimestamp());
    assertEquals(10, changes.get(1).getTimestamp());
  }

  @Test
  public void testLabelsDuringChangeSet() {
    add(myVcs, createFile(myRoot, "file"));
    myVcs.beginChangeSet();
    add(myVcs, changeContent(myRoot, "file", null));
    myVcs.putUserLabel("label", "project");
    myVcs.endChangeSet("changeSet");

    List<ChangeSet> changes = collectChanges(myVcs, "file", "project", null);
    assertEquals(2, changes.size());
    assertEquals("changeSet", changes.get(0).getName());
    assertNull(changes.get(1).getName());
  }

  @Test
  public void testSystemLabels() {
    myVcs.created("f1", false);
    myVcs.created("f2", false);

    setCurrentTimestamp(123);
    myVcs.putSystemLabel("label", "project", 456);

    List<ChangeSet> changes1 = collectChanges(myVcs, "f1", "project", null);
    List<ChangeSet> changes2 = collectChanges(myVcs, "f2", "project", null);
    assertEquals(2, changes1.size());
    assertEquals(2, changes2.size());

    assertEquals("label", changes1.get(0).getLabel());
    assertEquals("label", changes2.get(0).getLabel());

    ChangeSet r = changes1.get(0);
    assertEquals(123, r.getTimestamp());
    assertEquals(456, r.getLabelColor());
  }

  @Test
  public void testGettingByteContent() {
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, createFile(myRoot, "f", "one"));

    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, changeContent(myRoot, "f", "two"));

    LabelImpl l3 = myVcs.putSystemLabel("label", "project", -1);

    assertNull(l1.getByteContent(myRoot, "f").getBytes());
    assertEquals("one", new String(l2.getByteContent(myRoot, "f").getBytes(), StandardCharsets.UTF_8));
    assertEquals("two", new String(l3.getByteContent(myRoot, "f").getBytes(), StandardCharsets.UTF_8));

    add(myVcs, createDirectory(myRoot, "dir"));
    LabelImpl l4 = myVcs.putSystemLabel("label", "project", -1);

    assertTrue(l4.getByteContent(myRoot, "dir").isDirectory());
    assertNull(l4.getByteContent(myRoot, "dir").getBytes());
  }

  @Test
  public void testGettingByteContentInsideChangeSet() {
    myVcs.beginChangeSet();
    add(myVcs, createFile(myRoot, "f", "one"));
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, changeContent(myRoot, "f", "two"));
    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    myVcs.endChangeSet(null);

    assertEquals("one", new String(l1.getByteContent(myRoot, "f").getBytes(), StandardCharsets.UTF_8));
    assertEquals("two", new String(l2.getByteContent(myRoot, "f").getBytes(), StandardCharsets.UTF_8));
  }

  @Test
  public void testGettingByteContentAfterRename() {
    add(myVcs, createFile(myRoot, "f", "one"));
    LabelImpl l1 = myVcs.putSystemLabel("label", "project", -1);

    add(myVcs, changeContent(myRoot, "f", "two"));
    LabelImpl l2 = myVcs.putSystemLabel("label", "project", -1);
    add(myVcs, rename(myRoot, "f", "f_r"));

    LabelImpl l3 = myVcs.putSystemLabel("label", "project", -1);

    assertEquals("one", new String(l1.getByteContent(myRoot, "f_r").getBytes(), StandardCharsets.UTF_8));
    assertEquals("two", new String(l2.getByteContent(myRoot, "f_r").getBytes(), StandardCharsets.UTF_8));
    assertEquals("two", new String(l3.getByteContent(myRoot, "f_r").getBytes(), StandardCharsets.UTF_8));
  }
}