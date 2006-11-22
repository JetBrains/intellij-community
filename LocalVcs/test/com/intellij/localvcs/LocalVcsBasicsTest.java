package com.intellij.localvcs;

import org.junit.Test;
import org.junit.Ignore;

public class LocalVcsBasicsTest extends TestCase {
  // todo clean up LocalVcs tests
  private LocalVcs vcs = new LocalVcs(new TestStorage());

  @Test
  public void testOnlyApplyThrowsException() {
    vcs.createFile("/file", "", null);
    vcs.createFile("/file", "", null);

    try {
      vcs.apply();
      fail();
    } catch (LocalVcsException e) { }
  }

  @Test
  public void testClearingChangesOnApply() {
    vcs.createFile("/file", "content", null);
    vcs.changeFileContent("/file", "new content", null);
    vcs.rename("/file", "new file", null);
    vcs.delete("/new file");

    assertFalse(vcs.isClean());

    vcs.apply();
    assertTrue(vcs.isClean());
  }

  @Test
  public void testDoesNotMakeAnyChangesBeforeApply() {
    vcs.createFile("/file", "content", null);
    vcs.apply();

    vcs.changeFileContent("/file", "new content", null);

    assertEquals("content", vcs.getEntry("/file").getContent());
  }

  @Test
  public void testIncrementingIdOnEntryCreation() {
    vcs.createDirectory("/dir", null);
    vcs.createFile("/file", null, null);
    vcs.apply();

    Integer id1 = vcs.getEntry("/dir").getId();
    Integer id2 = vcs.getEntry("/file").getId();

    assertFalse(id1.equals(id2));
  }

  @Test
  public void testCreatingAndDeletingSameFileBeforeApply() {
    vcs.createFile("/file", "", null);
    vcs.delete("/file");
    vcs.apply();

    assertFalse(vcs.hasEntry("/file"));
  }

  @Test
  public void testDeletingAndAddingSameFileBeforeApply() {
    vcs.createFile("/file", "", null);
    vcs.apply();

    vcs.delete("/file");
    vcs.createFile("/file", "", null);
    vcs.apply();

    assertTrue(vcs.hasEntry("/file"));
  }

  @Test
  public void testAddingAndChangingSameFileBeforeApply() {
    vcs.createFile("/file", "content", null);
    vcs.changeFileContent("/file", "new content", null);
    vcs.apply();

    assertEquals("new content", vcs.getEntry("/file").getContent());
  }

  @Test
  public void testRevertingClearsAllPendingChanges() {
    // todo reimplement this test
  }

  @Test
  public void testCreatingFile() {
    vcs.createFile("/file", "content", 123L);
    vcs.apply();

    assertTrue(vcs.hasEntry("/file"));
    Entry e = vcs.getEntry("/file");

    assertEquals("content", e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testCreatingDirectory() {
    vcs.createDirectory("/dir", 456L);
    vcs.apply();

    assertTrue(vcs.hasEntry("/dir"));
    assertEquals(456L, vcs.getEntry("/dir").getTimestamp());
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    vcs.createDirectory("/dir", null);
    vcs.apply();

    vcs.createFile("/dir/file", null, null);
    vcs.apply();
    assertTrue(vcs.hasEntry("/dir/file"));
  }

  @Test
  public void testChangingFileContent() {
    vcs.createFile("/file", "content", null);
    vcs.apply();
    assertEquals("content", vcs.getEntry("/file").getContent());

    vcs.changeFileContent("/file", "new content", null);
    vcs.apply();

    assertEquals("new content", vcs.getEntry("/file").getContent());
  }

  @Test
  public void testRenamingFile() {
    vcs.createFile("/file", null, null);
    vcs.apply();
    assertTrue(vcs.hasEntry("/file"));

    vcs.rename("/file", "new file", null);
    vcs.apply();
    assertFalse(vcs.hasEntry("/file"));
    assertTrue(vcs.hasEntry("/new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    vcs.createDirectory("/dir1", null);
    vcs.createDirectory("/dir1/dir2", null);
    vcs.createFile("/dir1/dir2/file", null, null);
    vcs.apply();

    vcs.rename("/dir1/dir2", "new dir", null);
    vcs.apply();

    assertTrue(vcs.hasEntry("/dir1/new dir"));
    assertTrue(vcs.hasEntry("/dir1/new dir/file"));

    assertFalse(vcs.hasEntry("/dir1/dir2"));
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    vcs.createDirectory("/dir1", null);
    vcs.createDirectory("/dir2", null);
    vcs.createFile("/dir1/file", null, null);
    vcs.apply();

    vcs.move("/dir1/file", "/dir2", null);
    vcs.apply();

    assertTrue(vcs.hasEntry("/dir2/file"));
    assertFalse(vcs.hasEntry("/dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    vcs.createDirectory("/root1", null);
    vcs.createDirectory("/root2", null);
    vcs.createDirectory("/root1/dir", null);
    vcs.createFile("/root1/dir/file", null, null);
    vcs.apply();

    vcs.move("/root1/dir", "/root2", null);
    vcs.apply();

    assertTrue(vcs.hasEntry("/root2/dir"));
    assertTrue(vcs.hasEntry("/root2/dir/file"));
    assertFalse(vcs.hasEntry("/root1/dir"));
  }

  @Test
  public void testDeletingFile() {
    vcs.createFile("/file", "content", null);
    vcs.apply();

    vcs.delete("/file");
    vcs.apply();
    assertFalse(vcs.hasEntry("/file"));
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    vcs.createDirectory("/dir1", null);
    vcs.createDirectory("/dir1/dir2", null);
    vcs.createFile("/dir1/file1", "content1", null);
    vcs.createFile("/dir1/dir2/file2", "content2", null);
    vcs.apply();

    vcs.delete("/dir1");
    vcs.apply();
    assertFalse(vcs.hasEntry("/dir1"));
  }

  @Ignore
  @Test
  public void testCreatingRoots() {
    vcs.createRoot("c:/root");
    assertFalse(vcs.hasEntry("c:/root"));

    vcs.apply();
    assertTrue(vcs.hasEntry("c:/root"));
  }
}
