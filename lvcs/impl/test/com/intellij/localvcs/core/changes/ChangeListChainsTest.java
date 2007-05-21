package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Test;

public class ChangeListChainsTest extends LocalVcsTestCase {
  RootEntry r = new RootEntry();
  ChangeList l = new ChangeList();

  @Test
  public void testBasicChain() {
    Change c1 = new CreateFileChange(1, "f", null, -1);
    Change c2 = new ChangeFileContentChange("f", null, -1);

    applyAndAdd(c1, c2);

    assertTrue(l.isInTheChain(c1, c2));
  }

  @Test
  public void testChangeNotInChain() {
    Change c1 = new CreateFileChange(1, "f1", null, -1);
    Change c2 = new CreateFileChange(2, "f2", null, -1);

    applyAndAdd(c1, c2);

    assertFalse(l.isInTheChain(c1, c2));
  }

  @Test
  public void testMovementChain() {
    Change c1 = new CreateDirectoryChange(1, "dir1");
    Change c2 = new CreateDirectoryChange(2, "dir2");
    Change c3 = new CreateFileChange(3, "dir1/f", null, -1);

    applyAndAdd(c1, c2, c3);

    assertTrue(l.isInTheChain(c1, c3));
    assertFalse(l.isInTheChain(c2, c3));

    Change c4 = new MoveChange("dir1/f", "dir2");
    applyAndAdd(c4);

    assertTrue(l.isInTheChain(c1, c4));
    assertTrue(l.isInTheChain(c2, c3));
    assertTrue(l.isInTheChain(c2, c4));

    Change c5 = new MoveChange("dir2", "dir1");
    applyAndAdd(c5);
  }

  private void applyAndAdd(Change... cc) {
    for (Change c : cc) {
      c.applyTo(r);
      l.addChange(c);
    }
  }
}
