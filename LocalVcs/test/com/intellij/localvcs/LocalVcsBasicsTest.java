package com.intellij.localvcs;

import org.junit.Test;

public class LocalVcsBasicsTest extends TestCase {
  // todo clean up LocalVcs tests
  private LocalVcs vcs = new LocalVcs(new TestStorage());

  @Test
  public void testOnlyApplyThrowsException() {
    vcs.createFile(p("file"), "");
    vcs.createFile(p("file"), "");

    try {
      vcs.apply();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testDoesNotApplyAnyChangeIfOneFailed() {
    vcs.createFile(p("file1"), "");
    vcs.createFile(p("file2"), "");
    vcs.createFile(p("file2"), "");

    try { vcs.apply(); } catch (LocalVcsException e) { }

    assertFalse(vcs.hasEntry(p("file1")));
    assertFalse(vcs.hasEntry(p("file2")));
  }

  @Test
  public void testClearingChangesOnApply() {
    vcs.createFile(p("file"), "content");
    vcs.changeFileContent(p("file"), "new content");
    vcs.rename(p("file"), "new file");
    vcs.delete(p("new file"));

    assertFalse(vcs.isClean());

    vcs.apply();
    assertTrue(vcs.isClean());
  }

  @Test
  public void testDoesNotMakeAnyChangesBeforeApply() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.changeFileContent(p("file"), "new content");

    assertEquals("content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testIncrementingIdOnEntryCreation() {
    vcs.createDirectory(p("dir"));
    vcs.createFile(p("file"), null);
    vcs.apply();

    Integer id1 = vcs.getEntry(p("dir")).getObjectId();
    Integer id2 = vcs.getEntry(p("file")).getObjectId();

    assertFalse(id1.equals(id2));
  }

  @Test
  public void testKeepingIdOnChangingFileContent() {
    vcs.createFile(p("file"), "content");
    vcs.apply();
    Integer id1 = vcs.getEntry(p("file")).getObjectId();

    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();
    Integer id2 = vcs.getEntry(p("file")).getObjectId();

    assertEquals(id1, id2);
  }

  @Test
  public void testKeepingIdOnRenamingFile() {
    vcs.createFile(p("file"), null);
    vcs.apply();
    Integer id1 = vcs.getEntry(p("file")).getObjectId();

    vcs.rename(p("file"), "new file");
    vcs.apply();
    Integer id2 = vcs.getEntry(p("new file")).getObjectId();

    assertEquals(id1, id2);
  }

  @Test
  public void testKeepingIdOnMovingFile() {
    vcs.createDirectory(p("dir1"));
    vcs.createDirectory(p("dir2"));
    vcs.createFile(p("dir1/file"), null);
    vcs.apply();
    Integer id1 = vcs.getEntry(p("dir1/file")).getObjectId();

    vcs.move(p("dir1/file"), p("dir2"));
    vcs.apply();
    Integer id2 = vcs.getEntry(p("dir2/file")).getObjectId();

    assertEquals(id1, id2);
  }

  @Test
  public void testKeepingIdOnRestoringDeletedFile() {
    vcs.createFile(p("file"), null);
    vcs.apply();
    Integer id1 = vcs.getEntry(p("file")).getObjectId();

    vcs.delete(p("file"));
    vcs.apply();

    vcs.revert();
    Integer id2 = vcs.getEntry(p("file")).getObjectId();

    assertEquals(id1, id2);
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeApply() {
    vcs.createFile(p("file"), "");
    vcs.delete(p("file"));
    vcs.apply();

    assertFalse(vcs.hasEntry(p("file")));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeApply() {
    vcs.createFile(p("file"), "");
    vcs.apply();

    vcs.delete(p("file"));
    vcs.createFile(p("file"), "");
    vcs.apply();

    assertTrue(vcs.hasEntry(p("file")));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeApply() {
    vcs.createFile(p("file"), "content");
    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();

    assertEquals("new content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testReverting() {
    vcs.createFile(p("file"), null);
    vcs.apply();
    assertTrue(vcs.hasEntry(p("file")));

    vcs.revert();
    assertFalse(vcs.hasEntry(p("file")));
  }

  @Test
  public void testRevertingSeveralTimesRunning() {
    vcs.createFile(p("file1"), null);
    vcs.apply();

    vcs.createFile(p("file2"), null);
    vcs.apply();

    assertTrue(vcs.hasEntry(p("file1")));
    assertTrue(vcs.hasEntry(p("file2")));

    vcs.revert();
    assertTrue(vcs.hasEntry(p("file1")));
    assertFalse(vcs.hasEntry(p("file2")));

    vcs.revert();
    assertFalse(vcs.hasEntry(p("file1")));
    assertFalse(vcs.hasEntry(p("file2")));
  }

  @Test
  public void testRevertingClearsAllPendingChanges() {
    vcs.createFile(p("file1"), null);
    vcs.apply();

    vcs.createFile(p("file2"), null);
    assertFalse(vcs.isClean());

    vcs.revert();
    assertTrue(vcs.isClean());
  }

  @Test
  public void testRevertingWhenNoPreviousVersions() {
    try {
      vcs.revert();
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testCreatingFile() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    assertTrue(vcs.hasEntry(p("file")));
    assertEquals("content", vcs.getEntry(p("file")).getContent());

    vcs.revert();
    assertFalse(vcs.hasEntry(p("file")));
  }

  @Test
  public void testCreatingDirectory() {
    vcs.createDirectory(p("dir"));
    vcs.apply();
    assertTrue(vcs.hasEntry(p("dir")));

    vcs.revert();
    assertFalse(vcs.hasEntry(p("dir")));
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    vcs.createDirectory(p("dir"));
    vcs.apply();

    vcs.createFile(p("dir/file"), null);
    vcs.apply();
    assertTrue(vcs.hasEntry(p("dir/file")));

    vcs.revert();
    assertFalse(vcs.hasEntry(p("dir/file")));
    assertTrue(vcs.hasEntry(p("dir")));
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile(p("file"), "content");
    vcs.apply();
    assertEquals("content", vcs.getEntry(p("file")).getContent());

    vcs.changeFileContent(p("file"), "new content");
    vcs.apply();
    assertEquals("new content", vcs.getEntry(p("file")).getContent());

    vcs.revert();
    assertEquals("content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testRenamingFile() {
    vcs.createFile(p("file"), null);
    vcs.apply();
    assertTrue(vcs.hasEntry(p("file")));

    vcs.rename(p("file"), "new file");
    vcs.apply();
    assertFalse(vcs.hasEntry(p("file")));
    assertTrue(vcs.hasEntry(p("new file")));

    vcs.revert();
    assertTrue(vcs.hasEntry(p("file")));
    assertFalse(vcs.hasEntry(p("new file")));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    vcs.createDirectory(p("dir1"));
    vcs.createDirectory(p("dir1/dir2"));
    vcs.createFile(p("dir1/dir2/file"), null);
    vcs.apply();

    vcs.rename(p("dir1/dir2"), "new dir");
    vcs.apply();

    assertTrue(vcs.hasEntry(p("dir1/new dir")));
    assertTrue(vcs.hasEntry(p("dir1/new dir/file")));

    assertFalse(vcs.hasEntry(p("dir1/dir2")));

    vcs.revert();

    assertTrue(vcs.hasEntry(p("dir1/dir2")));
    assertTrue(vcs.hasEntry(p("dir1/dir2/file")));

    assertFalse(vcs.hasEntry(p("dir1/new dir")));
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    vcs.createDirectory(p("dir1"));
    vcs.createDirectory(p("dir2"));
    vcs.createFile(p("dir1/file"), null);
    vcs.apply();

    vcs.move(p("dir1/file"), p("dir2"));
    vcs.apply();

    assertTrue(vcs.hasEntry(p("dir2/file")));
    assertFalse(vcs.hasEntry(p("dir1/file")));

    vcs.revert();

    assertFalse(vcs.hasEntry(p("dir2/file")));
    assertTrue(vcs.hasEntry(p("dir1/file")));
  }

  @Test
  public void testMovingDirectory() {
    vcs.createDirectory(p("root1"));
    vcs.createDirectory(p("root2"));
    vcs.createDirectory(p("root1/dir"));
    vcs.createFile(p("root1/dir/file"), null);
    vcs.apply();

    vcs.move(p("root1/dir"), p("root2"));
    vcs.apply();

    assertTrue(vcs.hasEntry(p("root2/dir")));
    assertTrue(vcs.hasEntry(p("root2/dir/file")));
    assertFalse(vcs.hasEntry(p("root1/dir")));

    vcs.revert();

    assertTrue(vcs.hasEntry(p("root1/dir")));
    assertTrue(vcs.hasEntry(p("root1/dir/file")));
    assertFalse(vcs.hasEntry(p("root2/dir")));
  }

  @Test
  public void testDeletingFile() {
    vcs.createFile(p("file"), "content");
    vcs.apply();

    vcs.delete(p("file"));
    vcs.apply();
    assertFalse(vcs.hasEntry(p("file")));

    vcs.revert();
    assertTrue(vcs.hasEntry(p("file")));
    assertEquals("content", vcs.getEntry(p("file")).getContent());
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    // todo i dont trust to deletion reverting yet... i need some more tests

    vcs.createDirectory(p("dir1"));
    vcs.createDirectory(p("dir1/dir2"));
    vcs.createFile(p("dir1/file1"), "content1");
    vcs.createFile(p("dir1/dir2/file2"), "content2");
    vcs.apply();

    vcs.delete(p("dir1"));
    vcs.apply();
    assertFalse(vcs.hasEntry(p("dir1")));

    vcs.revert();
    assertTrue(vcs.hasEntry(p("dir1")));
    assertTrue(vcs.hasEntry(p("dir1/dir2")));
    assertTrue(vcs.hasEntry(p("dir1/file1")));
    assertTrue(vcs.hasEntry(p("dir1/dir2/file2")));

    assertEquals("content1", vcs.getEntry(p("dir1/file1")).getContent());
    assertEquals("content2", vcs.getEntry(p("dir1/dir2/file2")).getContent());
  }

  @Test
  public void testRevertingDeletionWithSameId() {
    // todo add move tests for this case (history etc).
    vcs.createFile(p("file"), "content");
    vcs.apply();
    Integer originalId = vcs.getEntry(p("file")).getObjectId();

    vcs.delete(p("file"));
    vcs.apply();

    vcs.revert();
    Integer restoredId = vcs.getEntry(p("file")).getObjectId();

    assertEquals(originalId, restoredId);
  }

  @Test
  public void testRevertingDeletionOfDirectoryWithSameId() {
    vcs.createDirectory(p("dir"));
    vcs.createFile(p("dir/file"), null);
    vcs.apply();

    Integer originalDirId = vcs.getEntry(p("dir")).getObjectId();
    Integer originalFileId = vcs.getEntry(p("dir/file")).getObjectId();

    vcs.delete(p("dir"));
    vcs.apply();

    vcs.revert();
    Integer restoredDirId = vcs.getEntry(p("dir")).getObjectId();
    Integer restoredFileId = vcs.getEntry(p("dir/file")).getObjectId();

    assertEquals(originalDirId, restoredDirId);
    assertEquals(originalFileId, restoredFileId);
  }
}
