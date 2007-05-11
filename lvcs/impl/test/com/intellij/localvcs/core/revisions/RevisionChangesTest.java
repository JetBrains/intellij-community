package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

public class RevisionChangesTest extends LocalVcsTestCase {
  RootEntry root = new RootEntry();
  ChangeList list = new ChangeList();
  ChangeSet cs1;
  ChangeSet cs2;
  ChangeSet cs3;

  @Before
  public void setUp() {
    cs1 = cs(new CreateFileChange(1, "f", null, -1));
    cs2 = cs(new ChangeFileContentChange("f", null, -1));
    cs3 = cs(new RenameChange("f", "ff"));

    list = new ChangeList();

    applyAndAdd(cs1);
    applyAndAdd(cs2);
    applyAndAdd(cs3);
  }

  @Test
  public void testCurrentRevisionIsBefore() {
    Revision r = new CurrentRevision(root.getEntry("ff"), -1);
    assertFalse(r.isBefore(cs2));
  }

  @Test
  public void testRevisionBeforeChangeIsBefore() {
    Revision r = new RevisionBeforeChange(root.getEntry("ff"), root, list, cs2);
    assertTrue(r.isBefore(cs2));
    assertTrue(r.isBefore(cs3));
    assertFalse(r.isBefore(cs1));
  }

  @Test
  public void testRevisionAfterChangeIsBefore() {
    Revision r = new RevisionAfterChange(root.getEntry("ff"), root, list, cs1);
    assertTrue(r.isBefore(cs2));
    assertTrue(r.isBefore(cs3));
    assertFalse(r.isBefore(cs1));
  }

  private void applyAndAdd(Change c) {
    c.applyTo(root);
    list.addChange(c);
  }
}
