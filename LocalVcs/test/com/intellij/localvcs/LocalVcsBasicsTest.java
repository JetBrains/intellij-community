package com.intellij.localvcs;

import org.junit.Ignore;
import org.junit.Test;

public class LocalVcsBasicsTest extends LocalVcsTestCase {
  // todo clean up LocalVcs tests
  private LocalVcs vcs = new TestLocalVcs();

  @Test
  public void testClearingChangesOnEachChange() {
    vcs.createFile("file", b("content"), null);
    assertTrue(vcs.isClean());
  }

  @Test
  public void testClearingChangesAfterChangeSetFinifhed() {
    vcs.beginChangeSet();

    vcs.createFile("file", b("content"), null);
    assertFalse(vcs.isClean());

    vcs.endChangeSet(null);
    assertTrue(vcs.isClean());
  }

  @Test
  public void testApplyingChangesRightAfterChange() {
    vcs.createFile("file", b("content"), null);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", b("new content"), null);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testIncrementingIdOnEntryCreation() {
    vcs.createDirectory("dir", null);
    vcs.createFile("file", null, null);

    Integer id1 = vcs.getEntry("dir").getId();
    Integer id2 = vcs.getEntry("file").getId();

    assertFalse(id1.equals(id2));
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testStartingUpdateTwiceThrowsException() {
    vcs.beginChangeSet();
    try {
      vcs.beginChangeSet();
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  @Ignore("unignore when service states will be completed")
  public void testFinishingUpdateWithoutStartingItThrowsException() {
    try {
      vcs.endChangeSet(null);
      fail();
    }
    catch (IllegalStateException e) {
    }
  }

  @Test
  public void testCreatingFile() {
    vcs.createFile("file", b("content"), 123L);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testCreatingLongFiles() {
    vcs.createFile("file", new byte[IContentStorage.MAX_CONTENT_LENGTH + 1], 777L);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(UnavailableContent.class, e.getContent().getClass());
    assertEquals(777L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectory() {
    vcs.createDirectory("dir", 456L);

    Entry e = vcs.findEntry("dir");

    assertNotNull(e);
    assertEquals(456L, e.getTimestamp());
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    vcs.createDirectory("dir", null);
    vcs.createFile("dir/file", null, null);

    assertTrue(vcs.hasEntry("dir/file"));
  }

  @Test
  public void testAskingForCreatedFileDuringChangeSet() {
    vcs.beginChangeSet();
    vcs.createFile("file", b("content"), null);

    Entry e = vcs.findEntry("file");

    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("file", b("content"), null);
    assertEquals(c("content"), vcs.getEntry("file").getContent());

    vcs.changeFileContent("file", b("new content"), null);
    assertEquals(c("new content"), vcs.getEntry("file").getContent());
  }

  @Test
  public void testRenamingFile() {
    vcs.createFile("file", null, null);
    assertTrue(vcs.hasEntry("file"));

    vcs.rename("file", "new file");

    assertFalse(vcs.hasEntry("file"));
    assertTrue(vcs.hasEntry("new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir1/dir2", null);
    vcs.createFile("dir1/dir2/file", null, null);

    vcs.rename("dir1/dir2", "new dir");

    assertTrue(vcs.hasEntry("dir1/new dir"));
    assertTrue(vcs.hasEntry("dir1/new dir/file"));

    assertFalse(vcs.hasEntry("dir1/dir2"));
  }

  @Test
  public void testTreatingRenamedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file1", null, null);
    vcs.rename("file1", "file2");
    vcs.createFile("file1", null, null);

    Entry one = vcs.getEntry("file1");
    Entry two = vcs.getEntry("file2");

    assertNotSame(one, two);
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir2", null);
    vcs.createFile("dir1/file", null, null);

    vcs.move("dir1/file", "dir2");

    assertTrue(vcs.hasEntry("dir2/file"));
    assertFalse(vcs.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    vcs.createDirectory("root1", null);
    vcs.createDirectory("root2", null);
    vcs.createDirectory("root1/dir", null);
    vcs.createFile("root1/dir/file", null, null);

    vcs.move("root1/dir", "root2");

    assertTrue(vcs.hasEntry("root2/dir"));
    assertTrue(vcs.hasEntry("root2/dir/file"));
    assertFalse(vcs.hasEntry("root1/dir"));
  }

  @Test
  public void testDeletingFile() {
    vcs.createFile("file", b("content"), null);
    assertTrue(vcs.hasEntry("file"));

    vcs.delete("file");
    assertFalse(vcs.hasEntry("file"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    vcs.createDirectory("dir1", null);
    vcs.createDirectory("dir1/dir2", null);
    vcs.createFile("dir1/file1", b("content1"), null);
    vcs.createFile("dir1/dir2/file2", b("content2"), null);

    vcs.delete("dir1");
    assertFalse(vcs.hasEntry("dir1"));
    assertFalse(vcs.hasEntry("dir1/dir2"));
    assertFalse(vcs.hasEntry("dir1/file1"));
    assertFalse(vcs.hasEntry("dir1/dir2/file2"));
  }

  public void testDeletingAndAddingSameFile() {
    vcs.createFile("file", null, null);
    vcs.delete("file");
    vcs.createFile("file", null, null);

    assertTrue(vcs.hasEntry("file"));
  }

  @Test
  public void testTreatingDeletedAndCreatedFilesWithSameNameDifferently() {
    vcs.createFile("file", null, null);

    Entry one = vcs.getEntry("file");

    vcs.delete("file");
    vcs.createFile("file", null, null);

    Entry two = vcs.getEntry("file");

    assertNotSame(one, two);
  }

  @Test
  public void testCreatingRoots() {
    vcs.createDirectory("c:/dir/root", null);

    assertTrue(vcs.hasEntry("c:/dir/root"));
    assertFalse(vcs.hasEntry("c:/dir"));

    assertEquals("c:/dir/root", vcs.getRoots().get(0).getName());
  }

  @Test
  public void testCreatingFilesUnderRoots() {
    vcs.createDirectory("c:/dir/root", null);
    vcs.createFile("c:/dir/root/file", null, null);

    assertTrue(vcs.hasEntry("c:/dir/root/file"));
  }
}