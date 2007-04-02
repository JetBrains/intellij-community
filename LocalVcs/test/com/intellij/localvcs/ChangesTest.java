package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class ChangesTest extends LocalVcsTestCase {
  private RootEntry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryForCreateFileChange() {
    root.createDirectory(99, "dir");
    Change c = new CreateFileChange(1, "dir/name", null, -1);
    c.applyTo(root);

    assertEquals(a(idp(99, 1)), c.getAffectedIdPaths());

  }

  @Test
  public void testAffectedEntryForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(2, "name");
    c.applyTo(root);

    assertEquals(a(idp(2)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForChangeFileContentChange() {
    root.createFile(16, "file", c("content"), -1);

    Change c = new ChangeFileContentChange("file", c("new content"), -1);
    c.applyTo(root);

    assertEquals(a(idp(16)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForRenameChange() {
    root.createFile(42, "name", null, -1);

    Change c = new RenameChange("name", "new name");
    c.applyTo(root);

    assertEquals(a(idp(42)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForMoveChange() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir2");
    root.createFile(13, "dir1/file", null, -1);

    MoveChange c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertEquals(a(idp(1, 13), idp(2, 13)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForDeleteChange() {
    root.createDirectory(99, "dir");
    root.createFile(7, "dir/file", null, -1);

    Change c = new DeleteChange("dir/file");
    c.applyTo(root);

    assertEquals(a(idp(99, 7)), c.getAffectedIdPaths());
  }
}