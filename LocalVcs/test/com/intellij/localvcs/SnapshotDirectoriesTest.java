package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends TestCase {
  @Test
  public void testCeatingDirectory() {
    Snapshot s = new Snapshot();
    assertFalse(s.hasRevision(fn("dir")));

    s.createDirectory(fn("dir"));
    assertTrue(s.hasRevision(fn("dir")));
    assertEquals(DirectoryRevision.class, s.getRevision(fn("dir")).getClass());
    assertTrue(s.getRevision(fn("dir")).getChildren().isEmpty());
  }

  @Test
  public void testCreatingFileUnderNonExistingDirectory() {
    //Snapshot s = new Snapshot();
    //s.createFile("dir/file", "");
    //
    //assertTrue(s.hasRevision("dir/file"));
    //assertTrue(s.hasRevision("dir"));
    //
    //List<Revision> children = s.getRevision("dir").getChildren();
    //assertEquals(1, children.size());
    //assertEquals("dir/file", children.get(0).getName());
  }
}
