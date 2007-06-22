package com.intellij.history.core.revisions;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.CreateFileChange;
import org.junit.Test;

public class RevisionsCauseChangesTest extends LocalVcsTestCase {
  ChangeSet cs = cs("Action", new CreateFileChange(1, "f", null, -1));

  @Test
  public void testCurrentRevisionIsBefore() {
    Revision r = new CurrentRevision(null, -1);
    assertNull(r.getCauseChangeName());
    assertNull(r.getCauseChange());
  }

  @Test
  public void testRevisionBeforeChangeIsBefore() {
    Revision r = new RevisionBeforeChange(null, null, null, cs);
    assertNull(r.getCauseChangeName());
    assertNull(r.getCauseChange());
  }

  @Test
  public void testRevisionAfterChangeIsBefore() {
    Revision r = new RevisionAfterChange(null, null, null, cs);
    assertEquals("Action", r.getCauseChangeName());
    assertEquals(cs, r.getCauseChange());
  }
}
