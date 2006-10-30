package com.intellij.localvcs;

import org.junit.Test;

public class DirectoryRevisionTest extends TestCase {
  @Test
  public void testAddingChildren() {
    Revision dir = new DirectoryRevision(1, fn("dir"));
    Revision file = new FileRevision(2, fn("file"), "");

    dir.addChild(file);

    assertEquals(1, dir.getChildren().size());
    assertSame(file, dir.getChildren().get(0));

    assertSame(dir, file.getParent());
  }

  @Test
  public void testPath() {
    Revision dir = new DirectoryRevision(1, fn("dir"));
    Revision file = new FileRevision(2, fn("file"), "");

    dir.addChild(file);

    assertEquals(fn("dir/file"), file.getPath());
    assertEquals(fn("file"), file.getName());
  }
}
