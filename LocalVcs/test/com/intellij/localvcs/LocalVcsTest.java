package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalVcsTest extends Assert {
  private LocalVcs myVcs;

  @Before
  public void setUp() {
    myVcs = new LocalVcs();
  }

  @Test
  public void testAddingFiles() {
    myVcs.addFile("file", "");
    assertFalse(myVcs.hasFile("file"));

    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingTwoFiles() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertTrue(myVcs.hasFile("file2"));

    assertFalse(myVcs.hasFile("unknown file"));
  }

  @Test
  public void testClearingChangesOnCommit() {
    myVcs.addFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.renameFile("file", "new file");
    myVcs.deleteFile("new file");

    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testDoesNotKeepUncommittedChanges() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testContentOfUnknownFile() {
    assertNull(myVcs.getFileRevision("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.addFile("file1", "content1");
    myVcs.addFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file1"));
    assertRevisionContent("content2", myVcs.getFileRevision("file2"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionContent("content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testRenaming() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
    assertTrue(myVcs.hasFile("new file"));

    assertRevisionContent("content", myVcs.getFileRevision("new file"));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    List<Revision> revs = myVcs.getFileRevisions("new file");

    assertEquals(2, revs.size());

    assertEquals("new file", revs.get(0).getName());
    assertEquals("content", revs.get(0).getContent());

    assertEquals("file", revs.get(1).getName());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testDeleting() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    assertRevisionContent("content", myVcs.getFileRevision("file"));

    myVcs.commit();
    assertFalse(myVcs.hasFile("file"));
    assertNull(myVcs.getFileRevision("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.addFile("file1", "");
    myVcs.addFile("file2", "");
    myVcs.commit();

    myVcs.deleteFile("file2");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertFalse(myVcs.hasFile("file2"));
  }

  @Test
  public void testAddingAndDeletingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.deleteFile("file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.addFile("file", "");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.addFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.addFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    myVcs.addFile("file", "old");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.addFile("file", "new");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    myVcs.addFile("file1", "content1");
    myVcs.commit();

    myVcs.renameFile("file1", "file2");
    myVcs.addFile("file1", "content2");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content1", "content1"},
                           myVcs.getFileRevisions("file2"));

    assertRevisionsContent(new String[]{"content2"},
                           myVcs.getFileRevisions("file1"));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getFileRevisions("file").isEmpty());

    myVcs.addFile("file", "");
    myVcs.commit();

    assertEquals(1, myVcs.getFileRevisions("file").size());
  }

  @Test
  public void testFileForUnknownFile() {
    myVcs.addFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.getFileRevisions("unknown file").isEmpty());
  }

  @Test
  public void testFileRevisionsForDeletedFile() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.commit();

    // todo what should we return? 
    //myVcs.getFileRevisions()
  }

  @Test
  public void testRevertingToPreviousVersion() {
    myVcs.addFile("file", "");
    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));

    myVcs.revert();
    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testRevertingClearsAllPendingChanges() {
    myVcs.addFile("file1", "");
    myVcs.commit();

    myVcs.addFile("file2", "");
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
    myVcs.addFile("file", "");
    assertFalse(myVcs.isClean());

    myVcs.revert();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testGettingSnapshots() {
    myVcs.addFile("file1", "content1");
    myVcs.addFile("file2", "content2");
    myVcs.commit();

    myVcs.addFile("file3", "content3");
    myVcs.changeFile("file1", "new content1");
    myVcs.commit();

    Integer id1 = myVcs.getFileRevision("file1").getObjectId();
    Integer id2 = myVcs.getFileRevision("file2").getObjectId();
    Integer id3 = myVcs.getFileRevision("file3").getObjectId();

    List<Snapshot> snapshots = myVcs.getSnapshots();
    assertEquals(2, snapshots.size());

    assertElements(
        new Object[]{
            new Revision(id1, "file1", "new content1"),
            new Revision(id2, "file2", "content2"),
            new Revision(id3, "file3", "content3")},
        snapshots.get(0).getRevisions());

    assertElements(
        new Object[]{
            new Revision(id1, "file1", "content1"),
            new Revision(id2, "file2", "content2")},
        snapshots.get(1).getRevisions());
  }

  @Test
  public void testGettingSnapshotsOnCleanVcs() {
    assertTrue(myVcs.getSnapshots().isEmpty());
  }

  @Test
  public void testGettingLabeledSnapshot() {
    myVcs.addFile("file", "content");
    myVcs.commit();

    myVcs.putLabel("label");

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    Snapshot s = myVcs.getSnapshot("label");
    assertNotNull(s);
    assertRevisionContent("content", s.getFileRevision("file"));
  }

  @Test
  public void testGettingSnapshotWithUnknownLabel() {
    myVcs.addFile("file", "content");
    myVcs.commit();
    myVcs.putLabel("label");

    assertNull(myVcs.getSnapshot("unknown label"));
  }

  private void assertRevisionContent(String expectedContent,
                                     Revision actualRevision) {
    assertEquals(expectedContent, actualRevision.getContent());
  }

  private void assertRevisionsContent(String[] expectedContents,
                                      Collection<Revision> actualRevisions) {
    List<String> actualContents = new ArrayList<String>();
    for (Revision rev : actualRevisions) {
      actualContents.add(rev.getContent());
    }
    assertEquals(expectedContents, actualContents.toArray(new Object[0]));
  }

  private void assertElements(Object[] expected, Collection actual) {
    assertEquals(expected.length, actual.size());
    assertTrue(actual.containsAll(Arrays.asList(expected)));
  }
}
