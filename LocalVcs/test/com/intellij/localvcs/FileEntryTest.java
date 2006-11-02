package com.intellij.localvcs;

import org.junit.Test;

public class FileEntryTest extends TestCase {
  @Test
  public void testCopying() {
    FileEntry file = new FileEntry(33, "name", "content");

    Entry copy = file.copy();

    assertEquals(33, copy.getObjectId());
    assertEquals("name", copy.getName());
    assertEquals("content", copy.getContent());
  }

  @Test
  public void testDoesNotCopyParent() {
    DirectoryEntry parent = new DirectoryEntry(null, null);
    FileEntry file = new FileEntry(null, null, null);

    parent.addChild(file);

    Entry copy = file.copy();
    assertNull(copy.getParent());
  }

  @Test
  public void testRenaming() {
    DirectoryEntry dir = new DirectoryEntry(null, "dir");
    FileEntry file = new FileEntry(33, "name", "content");

    dir.addChild(file);

    Entry renamed = file.renamed("new name");

    assertEquals(33, renamed.getObjectId());
    assertEquals("new name", renamed.getName());
    assertEquals("content", renamed.getContent());

    assertNull(renamed.getParent());
  }

  @Test
  public void testCanNotWorkWithChildren() {
    FileEntry file = new FileEntry(null, null, null);

    try {
      file.addChild(new FileEntry(null, null, null));
      fail();
    } catch (LocalVcsException e) {}

    try {
      file.removeChild(new FileEntry(null, null, null));
      fail();
    } catch (LocalVcsException e) {}

    try {
      file.getChildren();
      fail();
    } catch (LocalVcsException e) {}
  }
}
