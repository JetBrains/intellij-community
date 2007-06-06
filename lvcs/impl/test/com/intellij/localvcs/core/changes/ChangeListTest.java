package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.tree.Entry;
import org.junit.Test;

public class ChangeListTest extends ChangeListTestCase {
  @Test
  public void testRevertionUpToInclusively() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file1", null, -1)));
    applyAndAdd(cs("2", new CreateFileChange(2, "file2", null, -1)));

    Entry copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(0), true);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));

    copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(1), true);
    assertFalse(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }

  @Test
  public void testRevertionUpToExclusively() {
    applyAndAdd(cs("1", new CreateFileChange(1, "file1", null, -1)));
    applyAndAdd(cs("2", new CreateFileChange(2, "file2", null, -1)));

    Entry copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(0), false);
    assertTrue(copy.hasEntry("file1"));
    assertTrue(copy.hasEntry("file2"));

    copy = r.copy();
    cl.revertUpTo(copy, cl.getChanges().get(1), false);
    assertTrue(copy.hasEntry("file1"));
    assertFalse(copy.hasEntry("file2"));
  }
}
