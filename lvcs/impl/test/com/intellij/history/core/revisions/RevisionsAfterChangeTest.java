package com.intellij.history.core.revisions;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.changes.CreateFileChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import org.junit.Test;

public class RevisionsAfterChangeTest extends LocalVcsTestCase {
  @Test
  public void testForRootEntry() {
    Entry root = new RootEntry();
    ChangeList list = new ChangeList();
    Change c1 = new CreateFileChange(1, "f1", null, -1);
    Change c2 = new CreateFileChange(2, "f2", null, -1);

    c1.applyTo(root);
    c2.applyTo(root);
    list.addChange(c1);
    list.addChange(c2);

    Revision r = new RevisionAfterChange(root, root, list, c1);
    Entry e = r.getEntry();

    assertEquals(e.getClass(), RootEntry.class);
    assertNotNull(e.findEntry("f1"));
    assertNull(e.findEntry("f2"));
  }
}
