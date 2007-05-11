package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

public class StructuralChangesTest extends LocalVcsTestCase {
  private RootEntry root;

  @Before
  public void setUp() {
    root = new RootEntry();
  }

  @Test
  public void testAffectedEntryForCreateFileChange() {
    root.createDirectory(99, "dir");
    StructuralChange c = new CreateFileChange(1, "dir/name", null, -1);
    c.applyTo(root);

    assertEquals(list(idp(99, 1)), c.getAffectedIdPaths());

  }

  @Test
  public void testAffectedEntryForCreateDirectoryChange() {
    StructuralChange c = new CreateDirectoryChange(2, "name");
    c.applyTo(root);

    assertEquals(list(idp(2)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForChangeFileContentChange() {
    root.createFile(16, "file", c("content"), -1);

    StructuralChange c = new ChangeFileContentChange("file", c("new content"), -1);
    c.applyTo(root);

    assertEquals(list(idp(16)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForRenameChange() {
    root.createFile(42, "name", null, -1);

    StructuralChange c = new RenameChange("name", "new name");
    c.applyTo(root);

    assertEquals(list(idp(42)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForMoveChange() {
    root.createDirectory(1, "dir1");
    root.createDirectory(2, "dir2");
    root.createFile(13, "dir1/file", null, -1);

    StructuralChange c = new MoveChange("dir1/file", "dir2");
    c.applyTo(root);

    assertEquals(list(idp(1, 13), idp(2, 13)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectedEntryForDeleteChange() {
    root.createDirectory(99, "dir");
    root.createFile(7, "dir/file", null, -1);

    StructuralChange c = new DeleteChange("dir/file");
    c.applyTo(root);

    assertEquals(list(idp(99, 7)), c.getAffectedIdPaths());
  }

  @Test
  public void testAffectsOnlyDoesNotTakeIntoAccountParentChanges() {
    root.createDirectory(1, "dir");
    root.createDirectory(2, "dir/dir2");
    root.createFile(3, "dir/dir2/file", null, -1);

    Change c = new RenameChange("dir", "newDir");
    c.applyTo(root);

    assertFalse(c.affectsOnly(root.getEntry("newDir/dir2")));
    assertTrue(c.affectsOnly(root.getEntry("newDir")));
  }

  @Test
  public void testAffectsOnlyMove() {
    root.createDirectory(1, "root");
    root.createDirectory(2, "root/dir1");
    root.createDirectory(3, "root/dir2");
    root.createFile(4, "root/dir1/file", null, -1);

    Change c = new MoveChange("root/dir1/file", "root/dir2");
    c.applyTo(root);
    assertTrue(c.affectsOnly(root.getEntry("root")));
    assertFalse(c.affectsOnly(root.getEntry("root/dir1")));
    assertFalse(c.affectsOnly(root.getEntry("root/dir2")));
  }

  @Test
  public void testAffectsOnlyMoveParent() {
    root.createDirectory(1, "root");
    root.createDirectory(2, "root/dir");
    root.createDirectory(3, "root/dir/dir2");

    Change c = new MoveChange("root/dir/dir2", "root");
    c.applyTo(root);

    assertFalse(c.affectsOnly(root.getEntry("root/dir2")));
    assertTrue(c.affectsOnly(root.getEntry("root")));
  }

}