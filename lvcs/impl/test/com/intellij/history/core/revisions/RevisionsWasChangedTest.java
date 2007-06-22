package com.intellij.history.core.revisions;

import com.intellij.history.core.LocalVcsTestCase;
import org.junit.Test;

public class RevisionsWasChangedTest extends LocalVcsTestCase {
  @Test
  public void testCurrentRevision() {
    Revision r = new CurrentRevision(null, -1);
    assertTrue(r.wasChanged());
  }

  @Test
  public void testLabeledRevision() {
    Revision r = new LabeledRevision(null, null, null, null);
    assertFalse(r.wasChanged());
  }

  @Test
  public void testRevisionBeforeChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.wasChanged());
  }

  @Test
  public void testRevisionAfterChange() {
    Revision r = new RevisionBeforeChange(null, null, null, null);
    assertTrue(r.wasChanged());
  }
}
