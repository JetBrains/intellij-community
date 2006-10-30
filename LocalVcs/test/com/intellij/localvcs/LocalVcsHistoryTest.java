package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsHistoryTest extends LocalVcsTestCase {
  @Test
  public void testRevertingToPreviousVersion() {
    myVcs.createFile(p("file"), "");
    myVcs.commit();
    assertTrue(myVcs.hasEntry(p("file")));

    myVcs.revert();
    assertFalse(myVcs.hasEntry(p("file")));
  }

  @Test
  public void testRevertingClearsAllPendingChanges() {
    myVcs.createFile(p("file1"), "");
    myVcs.commit();

    myVcs.createFile(p("file2"), "");
    assertFalse(myVcs.isClean());

    myVcs.revert();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testRevertingWhenNoPreviousVersions() {
    try {
      myVcs.revert();
      myVcs.revert();
    } catch (Exception e) {
      fail(e.toString());
    }
  }

  @Test
  public void testClearingChangesAfterRevertWhenNoPreviousVersions() {
    myVcs.createFile(p("file"), "");
    assertFalse(myVcs.isClean());

    myVcs.revert();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testGettingSnapshots() {
    myVcs.createFile(p("file1"), "content1");
    myVcs.createFile(p("file2"), "content2");
    myVcs.commit();

    myVcs.createFile(p("file3"), "content3");
    myVcs.changeFile(p("file1"), "new content1");
    myVcs.commit();

    Integer id1 = myVcs.getEntry(p("file1")).getObjectId();
    Integer id2 = myVcs.getEntry(p("file2")).getObjectId();
    Integer id3 = myVcs.getEntry(p("file3")).getObjectId();

    List<Snapshot> snapshots = myVcs.getSnapshots();
    assertEquals(2, snapshots.size());

    assertElements(
        new Object[]{
            new FileEntry(id1, "file1", "new content1"),
            new FileEntry(id2, "file2", "content2"),
            new FileEntry(id3, "file3", "content3")},
        snapshots.get(0).getEntries());

    assertElements(
        new Object[]{
            new FileEntry(id1, "file1", "content1"),
            new FileEntry(id2, "file2", "content2")},
        snapshots.get(1).getEntries());
  }

  @Test
  public void testGettingSnapshotsOnCleanVcs() {
    assertTrue(myVcs.getSnapshots().isEmpty());
  }

  @Test
  public void testGettingLabeledSnapshot() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.putLabel("label");

    myVcs.changeFile(p("file"), "new content");
    myVcs.commit();

    Snapshot s = myVcs.getSnapshot("label");
    assertNotNull(s);
    assertRevisionContent("content", s.getEntry(p("file")));
  }

  @Test
  public void testGettingSnapshotWithUnknownLabel() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();
    myVcs.putLabel("label");

    assertNull(myVcs.getSnapshot("unknown label"));
  }
}
