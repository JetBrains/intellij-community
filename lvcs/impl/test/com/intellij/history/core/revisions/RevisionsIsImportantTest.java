package com.intellij.history.core.revisions;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.PutLabelChange;
import com.intellij.history.core.changes.PutSystemLabelChange;
import org.junit.Test;

public class RevisionsIsImportantTest extends LocalVcsTestCase {
  @Test
  public void testCurrentRevision() {
    Revision r = new CurrentRevision(null, -1);
    assertTrue(r.isImportant());
  }

  @Test
  public void testLabeledRevision() {
    Revision r1 = createLabeledRevision(new PutLabelChange(null, -1));
    Revision r2 = createLabeledRevision(new PutSystemLabelChange(null, -1));
    assertTrue(r1.isImportant());
    assertFalse(r2.isImportant());
  }

  private Revision createLabeledRevision(Change c) {
    return new LabeledRevision(null, null, null, c);
  }

  @Test
  public void testRevisionBeforeChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.isImportant());
  }

  @Test
  public void testRevisionAfterChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.isImportant());
  }
}
