package com.intellij.localvcs;

import org.junit.Test;

import java.util.List;

public class ChangeListPurgingTest extends LocalVcsTestCase {
  private ChangeList cl = new ChangeList();

  @Test
  public void testPurge() {
    ChangeSet cs1 = cs(1, new CreateFileChange(1, "f1", null, -1));
    ChangeSet cs2 = cs(2, new CreateFileChange(2, "f2", null, -1));
    ChangeSet cs3 = cs(3, new CreateFileChange(3, "f3", null, -1));
    ChangeSet cs4 = cs(4, new CreateFileChange(4, "f4", null, -1));
    cl.addChangeSet(cs1);
    cl.addChangeSet(cs2);
    cl.addChangeSet(cs3);
    cl.addChangeSet(cs4);

    cl.purgeUpTo(3);

    assertEquals(2, cl.getChangeSets().size());
    assertSame(cs4, cl.getChangeSets().get(0));
    assertSame(cs3, cl.getChangeSets().get(1));
  }

  @Test
  public void testPurgeUpToNearestUpperChangeSet() {
    ChangeSet cs1 = cs(1, new CreateFileChange(1, "f1", null, -1));
    ChangeSet cs2 = cs(5, new CreateFileChange(2, "f2", null, -1));
    cl.addChangeSet(cs1);
    cl.addChangeSet(cs2);

    cl.purgeUpTo(3);

    assertEquals(1, cl.getChangeSets().size());
    assertSame(cs2, cl.getChangeSets().get(0));
  }

  @Test
  public void testPurgingToEmpty() {
    cl.addChangeSet(cs(1, new CreateFileChange(1, "f", null, -1)));

    cl.purgeUpTo(10);
    assertTrue(cl.getChangeSets().isEmpty());
  }

  @Test
  public void testReturningContentsToPurge() {
    RootEntry r = new RootEntry();
    r.createFile(1, "f", c("one"), -1);

    ChangeSet cs1 = cs(1, new ChangeFileContentChange("f", c("two"), -1));
    ChangeSet cs2 = cs(2, new ChangeFileContentChange("f", c("three"), -1));

    ChangeFileContentChange c1 = new ChangeFileContentChange("f", c("four"), -1);
    ChangeFileContentChange c2 = new ChangeFileContentChange("f", c("five"), -1);
    ChangeSet cs3 = cs(3, c1, c2);

    cs1.applyTo(r);
    cs2.applyTo(r);
    cs3.applyTo(r);

    cl.addChangeSet(cs1);
    cl.addChangeSet(cs2);
    cl.addChangeSet(cs3);

    List<Content> contents = cl.purgeUpTo(10);

    assertEquals(4, contents.size());
    assertEquals(c("one"), contents.get(0));
    assertEquals(c("two"), contents.get(1));
    assertEquals(c("three"), contents.get(2));
    assertEquals(c("four"), contents.get(3));
  }
}