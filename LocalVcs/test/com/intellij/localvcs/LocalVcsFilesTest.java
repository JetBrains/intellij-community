package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class LocalVcsFilesTest extends LocalVcsTestCase {
  @Test
  public void testCreatingFiles() {
    myVcs.createFile(p("file"), "");
    assertFalse(myVcs.hasEntry(p("file")));

    myVcs.commit();
    assertTrue(myVcs.hasEntry(p("file")));
    assertEquals(FileEntry.class, myVcs.getEntry(p("file")).getClass());
  }

  @Test
  public void testCreatingTwoFiles() {
    myVcs.createFile(p("file1"), "");
    myVcs.createFile(p("file2"), "");
    myVcs.commit();

    assertTrue(myVcs.hasEntry(p("file1")));
    assertTrue(myVcs.hasEntry(p("file2")));

    assertFalse(myVcs.hasEntry(p("unknown file")));
  }

  @Test
  public void testCommitThrowsException() {
    myVcs.createFile(p("file"), "");
    myVcs.createFile(p("file"), "");

    try {
      myVcs.commit();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDoesNotApplyAnyChangeIfCommitFail() {
    myVcs.createFile(p("file1"), "");
    myVcs.createFile(p("file2"), "");
    myVcs.createFile(p("file2"), "");

    try { myVcs.commit(); } catch (LocalVcsException e) { }

    assertFalse(myVcs.hasEntry(p("file1")));
    assertFalse(myVcs.hasEntry(p("file2")));
  }

  @Test
  public void testClearingChangesOnCommit() {
    myVcs.createFile(p("file"), "content");
    myVcs.changeFile(p("file"), "new content");
    myVcs.renameFile(p("file"), "new file");
    myVcs.deleteFile(p("new file"));

    assertFalse(myVcs.isClean());

    myVcs.commit();
    assertTrue(myVcs.isClean());
  }

  @Test
  public void testChangingContent() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.changeFile(p("file"), "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getEntry(p("file")));
  }

  @Test
  public void testRevisionOfUnknownFile() {
    assertNull(myVcs.getEntry(p("unknown file")));
  }

  @Test
  public void testChangingOnlyOneFile() {
    myVcs.createFile(p("file1"), "content1");
    myVcs.createFile(p("file2"), "content2");
    myVcs.commit();

    myVcs.changeFile(p("file1"), "new content");
    myVcs.commit();

    assertRevisionContent("new content",
                          myVcs.getEntry(p("file1")));
    assertRevisionContent("content2", myVcs.getEntry(p("file2")));
  }

  @Test
  public void testKeepingOldVersions() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.changeFile(p("file"), "new content");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new content", "content"},
                           myVcs.getEntries(p("file")));
  }

  @Test
  public void testDoesNotChangeContentBeforeCommit() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.changeFile(p("file"), "new content");

    assertRevisionContent("content", myVcs.getEntry(p("file")));
  }

  @Test
  public void testDoesNotIncludeUncommittedChangesInRevisions() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.changeFile(p("file"), "new content");

    assertRevisionsContent(new String[]{"content"},
                           myVcs.getEntries(p("file")));
  }

  @Test
  public void testRenaming() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.renameFile(p("file"), "new file");
    myVcs.commit();

    assertFalse(myVcs.hasEntry(p("file")));
    assertTrue(myVcs.hasEntry(p("new file")));

    assertRevisionContent("content", myVcs.getEntry(p("new file")));
  }

  @Test
  public void testRenamingKeepsOldNameAndContent() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.renameFile(p("file"), "new file");
    myVcs.commit();

    List<Entry> revs = myVcs.getEntries(p("new file"));

    assertEquals(2, revs.size());

    assertEquals(p("new file"), revs.get(0).getPath());
    assertEquals("content", revs.get(0).getContent());

    assertEquals(p("file"), revs.get(1).getPath());
    assertEquals("content", revs.get(1).getContent());
  }

  @Test
  public void testDeleting() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.deleteFile(p("file"));
    assertRevisionContent("content", myVcs.getEntry(p("file")));

    myVcs.commit();
    assertFalse(myVcs.hasEntry(p("file")));
    assertNull(myVcs.getEntry(p("file")));
  }

  @Test
  public void testDeletingOnlyOneFile() {
    myVcs.createFile(p("file1"), "");
    myVcs.createFile(p("file2"), "");
    myVcs.commit();

    myVcs.deleteFile(p("file2"));
    myVcs.commit();

    assertTrue(myVcs.hasEntry(p("file1")));
    assertFalse(myVcs.hasEntry(p("file2")));
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeCommit() {
    myVcs.createFile(p("file"), "");
    myVcs.deleteFile(p("file"));
    myVcs.commit();

    assertFalse(myVcs.hasEntry(p("file")));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeCommit() {
    myVcs.createFile(p("file"), "");
    myVcs.commit();

    myVcs.deleteFile(p("file"));
    myVcs.createFile(p("file"), "");
    myVcs.commit();

    assertTrue(myVcs.hasEntry(p("file")));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeCommit() {
    myVcs.createFile(p("file"), "content");
    myVcs.changeFile(p("file"), "new content");
    myVcs.commit();

    assertRevisionContent("new content", myVcs.getEntry(p("file")));
  }

  @Test
  public void testDeletingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile(p("file"), "old");
    myVcs.commit();

    myVcs.deleteFile(p("file"));
    myVcs.createFile(p("file"), "new");
    myVcs.commit();

    assertRevisionsContent(new String[]{"new"},
                           myVcs.getEntries(p("file")));
  }

  @Test
  public void testRenamingFileAndCreatingNewOneWithSameName() {
    myVcs.createFile(p("file1"), "content1");
    myVcs.commit();

    myVcs.renameFile(p("file1"), "file2");
    myVcs.createFile(p("file1"), "content2");
    myVcs.commit();

    assertRevisionsContent(new String[]{"content1", "content1"},
                           myVcs.getEntries(p("file2")));

    assertRevisionsContent(new String[]{"content2"},
                           myVcs.getEntries(p("file1")));
  }

  @Test
  public void testFileRevisions() {
    assertTrue(myVcs.getEntries(p("file")).isEmpty());

    myVcs.createFile(p("file"), "");
    myVcs.commit();

    assertEquals(1, myVcs.getEntries(p("file")).size());
  }

  @Test
  public void testRevisionsForUnknownFile() {
    myVcs.createFile(p("file"), "");
    myVcs.commit();

    assertTrue(myVcs.getEntries(p("unknown file")).isEmpty());
  }

  @Test
  public void testFileRevisionsForDeletedFile() {
    myVcs.createFile(p("file"), "content");
    myVcs.commit();

    myVcs.deleteFile(p("file"));
    myVcs.commit();

    // todo what should we return?
    //myVcs.getFileRevisions()
  }
}
