package com.intellij.history.core.changes;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class ChangesApplyAndRevertTest extends LocalVcsTestCase {
  private Entry root = new RootEntry();

  @Test
  public void testCreatingFile() {
    Change c = new CreateFileChange(1, "file", c("content"), 123L);
    c.applyTo(root);

    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());

    c.revertOn(root);
    assertFalse(root.hasEntry("file"));
  }

  @Test
  public void testCreatingDirectory() {
    Change c = new CreateDirectoryChange(2, "dir");
    c.applyTo(root);

    assertTrue(root.hasEntry("dir"));

    c.revertOn(root);
    assertFalse(root.hasEntry("dir"));
  }

  @Test
  public void testCreatingFileUnderDirectory() {
    createDirectory(root, 1, "dir");

    Change c = new CreateFileChange(2, "dir/file", null, -1);
    c.applyTo(root);

    assertTrue(root.hasEntry("dir/file"));

    c.revertOn(root);
    assertFalse(root.hasEntry("dir/file"));
    assertTrue(root.hasEntry("dir"));
  }

  @Test
  public void testChangingFileContent() {
    createFile(root, 1, "file", c("old content"), 11L);

    Change c = new ChangeFileContentChange("file", c("new content"), 22L);
    c.applyTo(root);

    Entry e = root.getEntry("file");
    assertEquals(c("new content"), e.getContent());
    assertEquals(22L, e.getTimestamp());

    c.revertOn(root);

    e = root.getEntry("file");
    assertEquals(c("old content"), e.getContent());
    assertEquals(11L, e.getTimestamp());
  }

  @Test
  public void testRenamingFile() {
    createFile(root, 1, "file", null, -1);

    Change c = new RenameChange("file", "new file");
    c.applyTo(root);

    assertFalse(root.hasEntry("file"));
    assertTrue(root.hasEntry("new file"));

    c.revertOn(root);

    assertTrue(root.hasEntry("file"));
    assertFalse(root.hasEntry("new file"));
  }

  @Test
  public void testRenamingDirectoryWithContent() {
    createDirectory(root, 1, "dir1");
    createDirectory(root, 2, "dir1/dir2");
    createFile(root, 3, "dir1/dir2/file", null, -1);

    Change c = new RenameChange("dir1/dir2", "new dir");
    c.applyTo(root);

    assertTrue(root.hasEntry("dir1/new dir"));
    assertTrue(root.hasEntry("dir1/new dir/file"));

    assertFalse(root.hasEntry("dir1/dir2"));

    c.revertOn(root);

    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));

    assertFalse(root.hasEntry("dir1/new dir"));
  }

  @Test
  public void testRenamingRoot() {
    createDirectory(root, 1, "c:/dir/root");

    Change c = new RenameChange("c:/dir/root", "newRoot");
    c.applyTo(root);

    assertTrue(root.hasEntry("c:/dir/newRoot"));
    assertFalse(root.hasEntry("c:/dir/root"));

    c.revertOn(root);

    assertTrue(root.hasEntry("c:/dir/root"));
    assertFalse(root.hasEntry("c:/dir/newRoot"));
  }

  @Test
  public void testMovingFileFromOneDirectoryToAnother() {
    createDirectory(root, 1, "dir1");
    createDirectory(root, 2, "dir2");
    createFile(root, 3, "dir1/file", null, -1);

    Change c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertTrue(root.hasEntry("dir2/file"));
    assertFalse(root.hasEntry("dir1/file"));

    c.revertOn(root);

    assertFalse(root.hasEntry("dir2/file"));
    assertTrue(root.hasEntry("dir1/file"));
  }

  @Test
  public void testMovingDirectory() {
    createDirectory(root, 1, "root1");
    createDirectory(root, 2, "root2");
    createDirectory(root, 3, "root1/dir");
    createFile(root, 4, "root1/dir/file", null, -1);

    Change c = new MoveChange("root1/dir", "root2");
    c.applyTo(root);

    assertTrue(root.hasEntry("root2/dir"));
    assertTrue(root.hasEntry("root2/dir/file"));
    assertFalse(root.hasEntry("root1/dir"));

    c.revertOn(root);

    assertTrue(root.hasEntry("root1/dir"));
    assertTrue(root.hasEntry("root1/dir/file"));
    assertFalse(root.hasEntry("root2/dir"));
  }

  @Test
  public void testDeletingFile() {
    createFile(root, 1, "file", c("content"), 18L);

    Change c = new DeleteChange("file");
    c.applyTo(root);

    assertFalse(root.hasEntry("file"));

    c.revertOn(root);
    assertTrue(root.hasEntry("file"));

    Entry e = root.getEntry("file");

    assertEquals(c("content"), e.getContent());
    assertEquals(18L, e.getTimestamp());
  }

  @Test
  public void testDeletingDirectory() {
    createDirectory(root, 1, "dir");

    Change c = new DeleteChange("dir");
    c.applyTo(root);

    assertFalse(root.hasEntry("dir"));

    c.revertOn(root);

    Entry e = root.findEntry("dir");
    assertNotNull(e);
  }

  @Test
  public void testDeletingDirectoryWithContent() {
    createDirectory(root, 1, "dir1");
    createDirectory(root, 2, "dir1/dir2");
    createFile(root, 3, "dir1/dir2/file", c("content"), -1);

    Change c = new DeleteChange("dir1");
    c.applyTo(root);

    assertFalse(root.hasEntry("dir1"));

    c.revertOn(root);

    assertTrue(root.hasEntry("dir1"));
    assertTrue(root.hasEntry("dir1/dir2"));
    assertTrue(root.hasEntry("dir1/dir2/file"));

    assertEquals(c("content"), root.getEntry("dir1/dir2/file").getContent());
  }

  @Test
  public void testDeletionRevertionCopiesRestoredEntry() {
    createFile(root, 1, "f", null, -1);

    DeleteChange c = new DeleteChange("f");
    c.applyTo(root);

    Entry copy = root.copy();

    c.revertOn(root);
    assertNotSame(root.findEntry("f"), c.getAffectedEntry());

    rename(root, "f", "ff");

    c.revertOn(copy);
    assertTrue(copy.hasEntry("f"));
    assertFalse(copy.hasEntry("ff"));
  }

  @Test
  public void testDeletionOfDirectoryWithSeveralSubdirectoriesWithContent() {
    createDirectory(root, 1, "dir");
    createDirectory(root, 2, "dir/sub1");
    createDirectory(root, 3, "dir/sub2");
    createFile(root, 4, "dir/sub1/file", c(""), -1);
    createFile(root, 5, "dir/sub2/file", c(""), -1);

    Change c = new DeleteChange("dir");
    c.applyTo(root);
    c.revertOn(root);

    assertTrue(root.hasEntry("dir"));
    assertTrue(root.hasEntry("dir/sub1"));
    assertTrue(root.hasEntry("dir/sub1/file"));
    assertTrue(root.hasEntry("dir/sub2"));
    assertTrue(root.hasEntry("dir/sub2/file"));
  }

  @Test
  public void testDeletionOfRoots() {
    createDirectory(root, 1, "root/dir");
    createFile(root, 2, "root/dir/file", null, -1);

    Change c = new DeleteChange("root/dir");
    c.applyTo(root);

    assertFalse(root.hasEntry("root/dir"));

    c.revertOn(root);

    assertFalse(root.hasEntry("root"));

    assertTrue(root.hasEntry("root/dir"));
    assertTrue(root.hasEntry("root/dir/file"));
  }

  @Test
  public void testKeepingIdOnChangingFileContent() {
    createFile(root, 14, "file", null, -1);

    Change c = new ChangeFileContentChange("file", null, -1);
    c.applyTo(root);
    c.revertOn(root);

    assertEquals(14, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnRenaming() {
    createFile(root, 13, "file", null, -1);

    Change c = new RenameChange("file", "new name");
    c.applyTo(root);
    c.revertOn(root);

    assertEquals(13, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnMoving() {
    createDirectory(root, 1, "dir1");
    createDirectory(root, 2, "dir2");
    createFile(root, 3, "dir1/file", null, -1);

    Change c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);
    c.revertOn(root);

    assertEquals(3, root.getEntry("dir1/file").getId());
  }

  @Test
  public void testKeepingIdOnRestoringDeletedFile() {
    createFile(root, 1, "file", null, -1);

    Change c = new DeleteChange("file");
    c.applyTo(root);
    c.revertOn(root);

    assertEquals(1, root.getEntry("file").getId());
  }

  @Test
  public void testKeepingIdOnOnRestoringDeletedDirectoryWithContent() {
    createDirectory(root, 1, "dir");
    createFile(root, 2, "dir/file", null, -1);

    Change c = new DeleteChange("dir");
    c.applyTo(root);
    c.revertOn(root);

    assertEquals(1, root.getEntry("dir").getId());
    assertEquals(2, root.getEntry("dir/file").getId());
  }
}
