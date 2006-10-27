package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsFilesTest extends LocalVcsTestCase {
  @Test
  public void testCreatingFiles() {
    myVcs.createFile("file", "");
    assertFalse(myVcs.hasRevision("file"));

    myVcs.commit();
    assertTrue(myVcs.hasRevision("file"));
    assertEquals(FileRevision.class, myVcs.getRevision("file").getClass());
  }

  @Test
  public void testCreatingTwoFiles() {
    myVcs.createFile("file1", "");
    myVcs.createFile("file2", "");
    myVcs.commit();

    assertTrue(myVcs.hasRevision("file1"));
    assertTrue(myVcs.hasRevision("file2"));

    assertFalse(myVcs.hasRevision("unknown file"));
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

    assertFalse(myVcs.hasRevision("file1"));
    assertFalse(myVcs.hasRevision("file2"));
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

    assertRevisionContent("new content", myVcs.getRevision("file"));
  }

  @Test
  public void testRevisionOfUnknownFile() {
    assertNull(myVcs.getRevision("unknown file"));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.createFile("file1", "content1");
    myVcs.createFile("file2", "content2");
    myVcs.commit();

    myVcs.changeFile("file1", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getRevision("file1"));
    assertRevisionContent("content2", myVcs.getRevision("file2"));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getRevisions("file"));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionContent("content", myVcs.getRevision("file"));
  }

  @Test
  public void testDoesNotIncludeUncommittedChangesInRevisions() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.changeFile("file", "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getRevisions("file"));
  }

  @Test
  public void testRenaming() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    assertFalse(myVcs.hasRevision("file"));
    assertTrue(myVcs.hasRevision("new file"));

    assertRevisionContent("content", myVcs.getRevision("new file"));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.createFile("file", "content");
    myVcs.commit();

    myVcs.renameFile("file", "new file");
    myVcs.commit();

    List<Revision> revs = myVcs.getRevisions("new file");

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
    assertRevisionContent("content", myVcs.getRevision("file"));

    myVcs.commit();
    assertFalse(myVcs.hasRevision("file"));
    assertNull(myVcs.getRevision("file"));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.createFile("file1", "");
    myVcs.createFile("file2", "");
    myVcs.commit();

    myVcs.deleteFile("file2");
    myVcs.commit();

    assertTrue(myVcs.hasRevision("file1"));
    assertFalse(myVcs.hasRevision("file2"));
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeCommit() {
    myVcs.createFile("file", "");
    myVcs.deleteFile("file");
    myVcs.commit();

    assertFalse(myVcs.hasRevision("file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.createFile("file", "");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.createFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.hasRevision("file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.createFile("file", "content");
    myVcs.changeFile("file", "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getRevision("file"));
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile("file", "old");
    myVcs.commit();

    myVcs.deleteFile("file");
    myVcs.createFile("file", "new");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new"},
                           myVcs.getRevisions("file"));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile("file1", "content1");
    myVcs.commit();

    myVcs.renameFile("file1", "file2");
    myVcs.createFile("file1", "content2");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content1", "content1"},
                           myVcs.getRevisions("file2"));

    assertRevisionsContent(new String[]{"content2"},
                           myVcs.getRevisions("file1"));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getRevisions("file").isEmpty());

    myVcs.createFile("file", "");
    myVcs.commit();

    assertEquals(1, myVcs.getRevisions("file").size());
  }

  @Test
  public void testRevisionsForUnknownFile() {
    myVcs.createFile("file", "");
    myVcs.commit();

    assertTrue(myVcs.getRevisions("unknown file").isEmpty());
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
