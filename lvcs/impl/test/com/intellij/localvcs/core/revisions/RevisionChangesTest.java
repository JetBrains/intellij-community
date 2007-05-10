package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

public class RevisionChangesTest extends LocalVcsTestCase {
  RootEntry root = new RootEntry();
  ChangeList list = new ChangeList();
  Change c1;
  Change c2;
  Change c3;

  @Before
  public void setUp() {
    c1 = new CreateFileChange(1, "f", null, -1);
    c2 = new ChangeFileContentChange("f", null, -1);
    c3 = new RenameChange("f", "ff");

    list = new ChangeList();

    applyAndAdd(c1);
    applyAndAdd(c2);
    applyAndAdd(c3);
  }

  @Test
  public void testChangesForCurrentRevision() {
    Revision r = new CurrentRevision(root.getEntry("ff"), -1);
    assertTrue(r.getSubsequentChanges().isEmpty());
  }

  @Test
  public void testChangesForRevisionAfterChange() {
    Revision r = new RevisionAfterChange(root.getEntry("ff"), root, list, c1);
    assertEquals(list(c3, c2), r.getSubsequentChanges());
  }

  @Test
  public void testChangesForRevisionBeforeChange() {
    Revision r = new RevisionBeforeChange(root.getEntry("ff"), root, list, c2);
    assertEquals(list(c3, c2), r.getSubsequentChanges());
  }

  @Test
  public void testConsideringChangeSets() {
    Change c1 = cs(new CreateFileChange(1, "file", null, -1));
    RenameChange renameChange = new RenameChange("file", "newFile");
    Change c2 = cs(renameChange);

    applyAndAdd(c1);
    applyAndAdd(c2);

    Revision r = new RevisionAfterChange(root.getEntry("newFile"), root, list, c1);
    assertEquals(list(renameChange), r.getSubsequentChanges());
  }

  private void applyAndAdd(Change c) {
    c.applyTo(root);
    list.addChange(c);
  }
}
