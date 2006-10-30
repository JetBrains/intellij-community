package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsFilesTest extends LocalVcsTestCase {
  @Test
  public void testCreatingFiles() {
    myVcs.createFile(fn("file"), "");
    assertFalse(myVcs.hasRevision(fn("file")));

    myVcs.commit();
    assertTrue(myVcs.hasRevision(fn("file")));
    assertEquals(FileEntry.class, myVcs.getRevision(fn("file")).getClass());
  }

  @Test
  public void testCreatingTwoFiles() {
    myVcs.createFile(fn("file1"), "");
    myVcs.createFile(fn("file2"), "");
    myVcs.commit();

    assertTrue(myVcs.hasRevision(fn("file1")));
    assertTrue(myVcs.hasRevision(fn("file2")));

    assertFalse(myVcs.hasRevision(fn("unknown file")));
  }

  @Test
  public void testCommitThrowsException() {
    myVcs.createFile(fn("file"), "");
    myVcs.createFile(fn("file"), "");

    try {
      myVcs.commit();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDoesNotApplyAnyChangeIfCommitFail() {
    myVcs.createFile(fn("file1"), "");
    myVcs.createFile(fn("file2"), "");
    myVcs.createFile(fn("file2"), "");

    try { myVcs.commit(); } catch (LocalVcsException e) { }

    assertFalse(myVcs.hasRevision(fn("file1")));
    assertFalse(myVcs.hasRevision(fn("file2")));
  }

  @Test
  public void testClearingChangesOnCommit() {
    myVcs.createFile(fn("file"), "content");
    myVcs.changeFile(fn("file"), "new content");
    myVcs.renameFile(fn("file"), "new file");
    myVcs.deleteFile(fn("new file"));

    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.changeFile(fn("file"), "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getRevision(fn("file")));
  }

  @Test
  public void testRevisionOfUnknownFile() {
    assertNull(myVcs.getRevision(fn("unknown file")));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.createFile(fn("file1"), "content1");
    myVcs.createFile(fn("file2"), "content2");
    myVcs.commit();

    myVcs.changeFile(fn("file1"), "new content");
    myVcs.commit();

    assertRevisionContent("new content",
                          myVcs.getRevision(fn("file1")));
    assertRevisionContent("content2", myVcs.getRevision(fn("file2")));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.changeFile(fn("file"), "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getRevisions(fn("file")));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.changeFile(fn("file"), "new content");

    assertRevisionContent("content", myVcs.getRevision(fn("file")));
  }

  @Test
  public void testDoesNotIncludeUncommittedChangesInRevisions() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.changeFile(fn("file"), "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getRevisions(fn("file")));
  }

  @Test
  public void testRenaming() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.renameFile(fn("file"), "new file");
    myVcs.commit();

    assertFalse(myVcs.hasRevision(fn("file")));
    assertTrue(myVcs.hasRevision(fn("new file")));

    assertRevisionContent("content", myVcs.getRevision(fn("new file")));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.renameFile(fn("file"), "new file");
    myVcs.commit();

    List<Entry> revs = myVcs.getRevisions(fn("new file"));

    assertEquals(2, revs.size());

    assertEquals(fn("new file"), revs.get(0).getPath());
    assertEquals("content", revs.get(0).getContent());

    assertEquals(fn("file"), revs.get(1).getPath());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testDeleting() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.deleteFile(fn("file"));
    assertRevisionContent("content", myVcs.getRevision(fn("file")));

    myVcs.commit();
    assertFalse(myVcs.hasRevision(fn("file")));
    assertNull(myVcs.getRevision(fn("file")));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.createFile(fn("file1"), "");
    myVcs.createFile(fn("file2"), "");
    myVcs.commit();

    myVcs.deleteFile(fn("file2"));
    myVcs.commit();

    assertTrue(myVcs.hasRevision(fn("file1")));
    assertFalse(myVcs.hasRevision(fn("file2")));
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeCommit() {
    myVcs.createFile(fn("file"), "");
    myVcs.deleteFile(fn("file"));
    myVcs.commit();

    assertFalse(myVcs.hasRevision(fn("file")));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.createFile(fn("file"), "");
    myVcs.commit();

    myVcs.deleteFile(fn("file"));
    myVcs.createFile(fn("file"), "");
    myVcs.commit();

    assertTrue(myVcs.hasRevision(fn("file")));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.createFile(fn("file"), "content");
    myVcs.changeFile(fn("file"), "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getRevision(fn("file")));
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile(fn("file"), "old");
    myVcs.commit();

    myVcs.deleteFile(fn("file"));
    myVcs.createFile(fn("file"), "new");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new"},
                           myVcs.getRevisions(fn("file")));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile(fn("file1"), "content1");
    myVcs.commit();

    myVcs.renameFile(fn("file1"), "file2");
    myVcs.createFile(fn("file1"), "content2");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content1", "content1"},
                           myVcs.getRevisions(fn("file2")));

    assertRevisionsContent(new String[]{"content2"},
                           myVcs.getRevisions(fn("file1")));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getRevisions(fn("file")).isEmpty());

    myVcs.createFile(fn("file"), "");
    myVcs.commit();

    assertEquals(1, myVcs.getRevisions(fn("file")).size());
  }

  @Test
  public void testRevisionsForUnknownFile() {
    myVcs.createFile(fn("file"), "");
    myVcs.commit();

    assertTrue(myVcs.getRevisions(fn("unknown file")).isEmpty());
  }

  @Test
  public void testFileRevisionsForDeletedFile() {
    myVcs.createFile(fn("file"), "content");
    myVcs.commit();

    myVcs.deleteFile(fn("file"));
    myVcs.commit();

    // todo what should we return?
    //myVcs.getFileRevisions()
  }
}
