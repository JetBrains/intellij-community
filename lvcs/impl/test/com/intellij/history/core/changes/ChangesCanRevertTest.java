package com.intellij.history.core.changes;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class ChangesCanRevertTest extends LocalVcsTestCase {
  Entry r = new RootEntry();

  @Test
  public void testRenameChange() {
    createFile(r, "f", null, -1);
    Change c = new RenameChange("f", "ff");

    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "f", null, -1);
    assertFalse(c.canRevertOn(r));
  }

  @Test
  public void testMoveChange() {
    createDirectory(r, "dir1");
    createDirectory(r, "dir2");
    createFile(r, "dir1/f", null, -1);

    Change c = new MoveChange("dir1/f", "dir2");
    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "dir1/f", null, -1);

    assertFalse(c.canRevertOn(r));
  }

  @Test
  public void testDeleteChange() {
    createFile(r, "f", null, -1);
    Change c = new DeleteChange("f");

    c.applyTo(r);
    assertTrue(c.canRevertOn(r));

    createFile(r, "f", null, -1);
    assertFalse(c.canRevertOn(r));
  }
}
