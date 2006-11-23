package com.intellij.localvcs;

import org.junit.Test;

public class ChangesApplyAndRevertTest extends TestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testCreatingFile() {
    Change c = new CreateFileChange(1, "file", "content", 123L);
    c.applyTo(root);

    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");
    assertEquals("content", e.getContent());
    assertEquals(123L, e.getTimestamp());

    c._revertOn(root);
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testCreatingDirectory() {
    Change c = new CreateDirectoryChange(2, "dir", 777L);
    c.applyTo(root);

    assertTrue(root.hasEntry("dir"));
    assertEquals(777L, root.getEntry("dir").getTimestamp());

    c._revertOn(root);
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    root.createDirectory(1, "dir", null);

    Change c = new CreateFileChange(2, "dir/file", null, null);
    c.applyTo(root);

    assertTrue(root.hasEntry("dir/file"));

    c._revertOn(root);
    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir"));
  }

  @Test
  public void testChangingFileContent() {
    root.createFile(1, "file", "old content", 11L);

    Change c = new ChangeFileContentChange("file", "new content", 22L);
    c.applyTo(root);

    Entry e = root.getEntry("file");
    assertEquals("new content", e.getContent());
    assertEquals(22L, e.getTimestamp());

    c._revertOn(root);

    e = root.getEntry("file");
    assertEquals("old content", e.getContent());
    assertEquals(11L, e.getTimestamp());
  }

  @Test
  public void testRenamingFile() {
    root.createFile(1, "file", null, 111L);

    Change c = new RenameChange("file", "new file", 666L);
    c.applyTo(root);

    assertFalse(root.hasEntry("file"));
    assertTrue(root.hasEntry("new file"));

    assertEquals(666L, root.getEntry("new file").getTimestamp());

    c._revertOn(root);

    assertTrue(root.hasEntry("file"));
    assertFalse(root.hasEntry("new file"));

    assertEquals(111L, root.getEntry("file").getTimestamp());
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir1/dir2", null);
    root.createFile(3, "dir1/dir2/file", null, null);

    Change c = new RenameChange("dir1/dir2", "new dir", null);
    c.applyTo(root);

    assertTrue(root.hasEntry("dir1/new dir"));
    assertTrue(root.hasEntry("dir1/new dir/file"));

    assertFalse(root.hasEntry("dir1/dir2"));

    c._revertOn(root);

    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));

    assertFalse(root.hasEntry("dir1/new dir"));
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir2", null);
    root.createFile(3, "dir1/file", null, 111L);

    Change c = new MoveChange("dir1/file", "dir2", 222L);
    c.applyTo(root);

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    assertEquals(222L, root.getEntry("dir2/file").getTimestamp());

    c._revertOn(root);

    assertFalse(root.hasEntry("dir2/file"));
    assertTrue(root.hasEntry("dir1/file"));

    assertEquals(111L, root.getEntry("dir1/file").getTimestamp());
  }

  @Test
  public void testMovingDirectory() {
    root.createDirectory(1, "root1", null);
    root.createDirectory(2, "root2", null);
    root.createDirectory(3, "root1/dir", null);
    root.createFile(4, "root1/dir/file", null, null);

    Change c = new MoveChange("root1/dir", "root2", null);
    c.applyTo(root);

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));
    assertFalse(root.hasEntry("root1/dir"));

    c._revertOn(root);

    assertTrue(root.hasEntry("root1/dir"));
    assertTrue(root.hasEntry("root1/dir/file"));
    assertFalse(root.hasEntry("root2/dir"));
  }

  @Test
  public void testDeletingFile() {
    root.createFile(1, "file", "content", 18L);

    Change c = new DeleteChange("file");
    c.applyTo(root);

    assertFalse(root.hasEntry("file"));

    c._revertOn(root);
    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");

    assertEquals("content", e.getContent());
    assertEquals(18L, e.getTimestamp());
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    // todo i dont trust to deletion reverting yet... i need some more tests
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir1/dir2", null);
    root.createFile(3, "dir1/dir2/file", "content", null);

    Change c = new DeleteChange("dir1");
    c.applyTo(root);

    assertFalse(root.hasEntry("dir1"));
    c._revertOn(root);

    assertTrue(root.hasEntry("dir1"));
    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));

    assertEquals("content", root.getEntry("dir1/dir2/file").getContent());
  }

  @Test
  public void testKeepingIdOnChangingFileContent() {
    root.createFile(14, "file", null, null);

    Change c = new ChangeFileContentChange("file", null, null);
    c.applyTo(root);
    c._revertOn(root);

    assertEquals(14, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnRenaming() {
    root.createFile(13, "file", null, null);

    Change c = new RenameChange("file", "new name", null);
    c.applyTo(root);
    c._revertOn(root);

    assertEquals(13, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnMoving() {
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir2", null);
    root.createFile(3, "dir1/file", null, null);

    Change c = new MoveChange("dir1/file", "dir2", null);
    c.applyTo(root);
    c._revertOn(root);

    assertEquals(3, root.getEntry("dir1/file").getId());
  }

  @Test
  public void testKeepingIdOnRestoringDeletedFile() {
    root.createFile(1, "file", null, null);

    Change c = new DeleteChange("file");
    c.applyTo(root);
    c._revertOn(root);

    assertEquals(1, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnOnRestoringDeletedDirectoryWithContent() {
    root.createDirectory(1, "dir", null);
    root.createFile(2, "dir/file", null, null);

    Change c = new DeleteChange("dir");
    c.applyTo(root);
    c._revertOn(root);

    assertEquals(1, root.getEntry("dir").getId());
    assertEquals(2, root.getEntry("dir/file").getId());
  }
}
