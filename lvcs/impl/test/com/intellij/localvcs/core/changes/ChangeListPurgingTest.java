package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.LocalVcsTestCase;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.tree.RootEntry;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ChangeListPurgingTest extends LocalVcsTestCase {
  int myIntervalBetweenActivities = 0;

  private RootEntry r = new RootEntry();
  private ChangeList cl = new ChangeList() {
    @Override
    protected long getIntervalBetweenActivities() {
      return myIntervalBetweenActivities;
    }
  };

  @Before
  public void setUp() {
    myIntervalBetweenActivities = 2;
  }

  @Test
  public void testPurgeWithoutGapsBetweenChanges() {
    createChangesWithTimestamps(1, 2, 3);
    cl.purgeObsolete(2);
    assertRemainedChangesTimestamps(3, 2);
  }

  @Test
  public void testPurgeNothing() {
    createChangesWithTimestamps(1, 2, 3);
    cl.purgeObsolete(10);
    assertRemainedChangesTimestamps(3, 2, 1);
  }

  @Test
  public void testDoesNotPurgeTheOnlyChange() {
    createChangesWithTimestamps(1);
    cl.purgeObsolete(1);
    assertRemainedChangesTimestamps(1);
  }

  @Test
  public void testPurgeWithOneGap() {
    createChangesWithTimestamps(1, 2, 4);
    cl.purgeObsolete(2);
    assertRemainedChangesTimestamps(4, 2);
  }

  @Test
  public void testPurgeWithSeveralGaps() {
    createChangesWithTimestamps(1, 2, 4, 5, 7, 8);
    cl.purgeObsolete(5);
    assertRemainedChangesTimestamps(8, 7, 5, 4, 2);
  }

  @Test
  public void testPurgeWithLongGaps() {
    createChangesWithTimestamps(10, 20, 30, 40);
    cl.purgeObsolete(3);
    assertRemainedChangesTimestamps(40, 30, 20);
  }

  @Test
  public void testPurgeWithBifIntervalBetweenChanges() {
    myIntervalBetweenActivities = 100;

    createChangesWithTimestamps(110, 120, 130, 250, 260, 270);
    cl.purgeObsolete(40);
    assertRemainedChangesTimestamps(270, 260, 250, 130, 120);
  }

  @Test
  public void testPurgingEmptyListDoesNotThrowException() {
    cl.purgeObsolete(50);
  }

  @Test
  public void testChangesAfterPurge() {
    applyAndAddChange(cs(1, new CreateFileChange(1, "file", null, -1)));
    applyAndAddChange(cs(2, new ChangeFileContentChange("file", null, -1)));
    applyAndAddChange(cs(3, new ChangeFileContentChange("file", null, -1)));

    assertEquals(3, cl.getChangesFor(r, "file").size());

    cl.purgeObsolete(2);

    assertEquals(2, cl.getChangesFor(r, "file").size());
  }

  @Test
  public void testReturningContentsToPurge() {
    r.createFile(1, "f", c("one"), -1);

    applyAndAddChange(cs(1, new ChangeFileContentChange("f", c("two"), -1)));
    applyAndAddChange(cs(2, new ChangeFileContentChange("f", c("three"), -1)));

    ChangeFileContentChange c1 = new ChangeFileContentChange("f", c("four"), -1);
    ChangeFileContentChange c2 = new ChangeFileContentChange("f", c("five"), -1);
    applyAndAddChange(cs(3, c1, c2));

    applyAndAddChange(cs(4, new ChangeFileContentChange("f", c("six"), -1)));

    List<Content> contents = cl.purgeObsolete(1);

    assertEquals(1, cl.getChanges().size());

    assertEquals(4, contents.size());
    assertEquals(c("one"), contents.get(0));
    assertEquals(c("two"), contents.get(1));
    assertEquals(c("three"), contents.get(2));
    assertEquals(c("four"), contents.get(3));
  }

  private void createChangesWithTimestamps(long... tt) {
    for (long t : tt) cl.addChange(new PutLabelChange(t, null, false));
  }

  private void assertRemainedChangesTimestamps(long... tt) {
    assertEquals(tt.length, cl.getChanges().size());
    for (int i = 0; i < tt.length; i++) {
      long t = tt[i];
      Change c = cl.getChanges().get(i);
      assertEquals(t, c.getTimestamp());
    }
  }

  private void applyAndAddChange(Change c) {
    c.applyTo(r);
    cl.addChange(c);
  }
}