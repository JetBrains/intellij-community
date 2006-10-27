package com.intellij.localvcs;

import java.util.List;

import org.junit.Test;

public class SnapshotDirectoriesTest extends SnapshotTestCase {
  @Test
  public void testCeatingDirectory() {
    Snapshot s = new Snapshot();
    assertFalse(s.hasRevision("dir"));

    s.createDirectory("dir");
    assertTrue(s.hasRevision("dir"));
    assertEquals(DirectoryRevision.class, s.getRevision("dir").getClass());
    assertTrue(s.getRevision("dir").getChildren().isEmpty());
  }

  @Test
  public void testCreatingDirectoryWithFiles() {
    Snapshot s = new Snapshot();

    s.createDirectory("dir");
    s.createFile("dir/file", "content");

    assertTrue(s.hasRevision("dir/file"));

    Revision rev = s.getRevision("dir/file");

    assertEquals("dir/file", rev.getName());
    assertEquals("content", rev.getContent());

    List<Revision> children = s.getRevision("dir").getChildren();
    assertEquals(1, children.size());
    assertSame(rev, children.get(0));
  }
}
