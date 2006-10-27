package com.intellij.localvcs;

import org.junit.Test;

public class SnapshotDirectoriesTest extends SnapshotTestCase {
  @Test
  public void testFileRevision() {
    Snapshot s = new Snapshot();
    s.createFile("file", "content");

    Revision r1 = s.getRevision("file");
    Revision r2 = s.getRevision(r1.getObjectId());

    assertEquals("file", r1.getName());
    assertEquals("file", r2.getName());

    assertEquals("content", r1.getContent());
    assertEquals("content", r2.getContent());
  }
}
