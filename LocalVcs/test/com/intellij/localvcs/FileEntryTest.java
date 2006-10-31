package com.intellij.localvcs;

import org.junit.Test;

public class FileEntryTest extends TestCase {
  @Test
  public void testEquality() {
    FileEntry e = new FileEntry(1, "name", "content");

    assertTrue(e.equals(new FileEntry(1, "name", "content")));

    assertFalse(e.equals(new FileEntry(2, "name", "content")));
    assertFalse(e.equals(new FileEntry(1, "another name", "content")));
    assertFalse(e.equals(new FileEntry(1, "name", "another content")));

    assertFalse(e.equals(null));
    assertFalse(e.equals(new Object()));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testHashCodeThrowsException() {
    new FileEntry(1, "name", "content").hashCode();
  }

  @Test
  public void testCopying() {
    FileEntry file = new FileEntry(33, "name", "content");

    Entry copy = file.copy();

    assertEquals(33, copy.getObjectId());
    assertEquals(p("name"), copy.getPath());
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
    file.setParent(dir);

    Entry renamed = file.renamed("new name");

    assertEquals(33, renamed.getObjectId());
    assertEquals(p("new name"), renamed.getPath());
    assertEquals("content", renamed.getContent());

    assertNull(renamed.getParent());
  }
}
