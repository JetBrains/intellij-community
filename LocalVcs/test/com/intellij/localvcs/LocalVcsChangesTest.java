package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsChangesTest extends LocalVcsTestCase {
  @Test
  public void testCreatingFiles() {
    myVcs.createFile("file", "");
    assertFalse(myVcs.hasFile("file"));

    myVcs.commit();
    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testCreatingTwoFiles() {
    myVcs.createFile("file1", "");
    myVcs.createFile("file2", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertTrue(myVcs.hasFile("file2"));

    assertFalse(myVcs.hasFile("unknown file"));
  }

  @Test
  public void testCommitThrowsException() {
    myVcs.createFile("file", "");
    myVcs.createFile("file", "");

    try {
      myVcs.commit();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDoesNotApplyAnyChangeIfCommitFail() {
    myVcs.createFile("file1", "");
    myVcs.createFile("file2", "");
    myVcs.createFile("file2", "");

    try { myVcs.commit(); } catch (LocalVcsException e) { }

    assertFalse(myVcs.hasFile("file1"));
    assertFalse(myVcs.hasFile("file2"));
  }

  @Test
  public void testClearingChangesOnCommit() {
    myVcs.createFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.renameFile("file", "new file");
    myVcs.deleteFile("new file");

    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testRevisionOfUnknownFile() {
    assertNull(myVcs.getFileRevision("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.createFile("file1", "content1");
    myVcs.createFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file1"));
    assertRevisionContent("content2", myVcs.getFileRevision("file2"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionContent("content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testDoesNotIncludeUncommittedChangesInRevisions() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testRenaming() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
    assertTrue(myVcs.hasFile("new file"));

    assertRevisionContent("content", myVcs.getFileRevision("new file"));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.createFile("file", "content");
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
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    assertRevisionContent("content", myVcs.getFileRevision("file"));

    myVcs.commit();
    assertFalse(myVcs.hasFile("file"));
    assertNull(myVcs.getFileRevision("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.createFile("file1", "");
    myVcs.createFile("file2", "");
    myVcs.commit();

    myVcs.deleteFile("file2");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file1"));
    assertFalse(myVcs.hasFile("file2"));
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeCommit() {
    myVcs.createFile("file", "");
    myVcs.deleteFile("file");
    myVcs.commit();

    assertFalse(myVcs.hasFile("file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.createFile("file", "");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.createFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.hasFile("file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.createFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getFileRevision("file"));
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile("file", "old");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.createFile("file", "new");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new"},
                           myVcs.getFileRevisions("file"));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile("file1", "content1");
    myVcs.commit();

    myVcs.renameFile("file1", "file2");
    myVcs.createFile("file1", "content2");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content1", "content1"},
                           myVcs.getFileRevisions("file2"));

    assertRevisionsContent(new String[]{"content2"},
                           myVcs.getFileRevisions("file1"));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getFileRevisions("file").isEmpty());

    myVcs.createFile("file", "");
    myVcs.commit();

    assertEquals(1, myVcs.getFileRevisions("file").size());
  }

  @Test
  public void testRevisionsForUnknownFile() {
    myVcs.createFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.getFileRevisions("unknown file").isEmpty());
  }

  @Test
  public void testFileRevisionsForDeletedFile() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.commit();

    // todo what should we return?
    //myVcs.getFileRevisions()
  }
}
