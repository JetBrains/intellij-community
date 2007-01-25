package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class ChangesTest extends TestCase {
  private RootEntry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryIdForCreateFileChange() {
    root.createDirectory(99, "dir", null);
    Change c = new CreateFileChange(1, "dir/name", null, null);
    c.applyTo(root);

    assertEquals(idp(99, 1), c.getAffectedIdPath());

    assertTrue(c.affects(root.getEntry(1)));
    assertTrue(c.affects(root.getEntry(99)));
  }

  @Test
  public void testAffectedEntryForCreateDirectoryChange() {
    Change c = new CreateDirectoryChange(2, "name", null);
    c.applyTo(root);

    assertEquals(idp(2), c.getAffectedIdPath());
  }

  @Test
  public void testAffectedEntryForChangeFileContentChange() {
    root.createFile(16, "file", c("content"), null);

    Change c = new ChangeFileContentChange("file", c("new content"), null);
    c.applyTo(root);

    assertEquals(idp(16), c.getAffectedIdPath());
  }

  @Test
  public void testAffectedEntryForRenameChange() {
    root.createFile(42, "name", null, null);

    Change c = new RenameChange("name", "new name");
    c.applyTo(root);

    assertEquals(idp(42), c.getAffectedIdPath());
  }

  @Test
  public void testAffectedEntryForMoveChange() {
    root.createDirectory(1, "dir1", null);
    root.createDirectory(2, "dir2", null);
    root.createFile(13, "dir1/file", null, null);

    MoveChange c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertEquals(idp(1, 13), c.getFirstAffectedIdPath());
    assertEquals(idp(2, 13), c.getSecondAffectedIdPath());
  }

  @Test
  public void testAffectedEntryForDeleteChange() {
    root.createDirectory(99, "dir", null);
    root.createFile(7, "dir/file", null, null);

    Change c = new DeleteChange("dir/file");
    c.applyTo(root);

    assertEquals(idp(99, 7), c.getAffectedIdPath());
  }
}