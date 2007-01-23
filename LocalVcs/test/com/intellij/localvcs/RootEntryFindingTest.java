package com.intellij.localvcs;

import org.junit.Test;

public class RootEntryFindingTest extends TestCase {
  private RootEntry root = new RootEntry();

  @Test
  public void testFindingEntry() {
    FileEntry e = new FileEntry(null, "file", null, null);
    root.addChild(e);

    assertSame(e, root.findEntry("file"));
    assertNull(root.findEntry("unknown file"));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry("unknown entry"));
    assertNull(root.findEntry("root/unknown entry"));
  }

  @Test
  public void testFindingEntryUnderDirectory() {
    DirectoryEntry dir = new DirectoryEntry(null, "dir", null);
    FileEntry file = new FileEntry(null, "file", null, null);

    dir.addChild(file);
    root.addChild(dir);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file, root.findEntry("dir/file"));
    assertNull(root.findEntry("dir/another"));
  }

  @Test
  public void testFindingEntriesInTree() {
    root = new RootEntry();
    Entry dir = new DirectoryEntry(null, "dir", null);
    Entry file1 = new FileEntry(null, "file1", null, null);
    Entry file2 = new FileEntry(null, "file2", null, null);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file1, root.findEntry("file1"));
    assertSame(file2, root.findEntry("dir/file2"));
  }

  @Test
  public void testNamesOfDirectoriesBeginningWithTheSameString() {
    Entry d1 = new DirectoryEntry(null, "dir", null);
    Entry d2 = new DirectoryEntry(null, "dir2", null);
    Entry f = new FileEntry(null, "file", null, null);

    root.addChild(d1);
    root.addChild(d2);
    d2.addChild(f);

    assertSame(f, root.findEntry("dir2/file"));
    assertNull(root.findEntry("dir/file"));
  }

  @Test
  public void testNamesOfEntriesBeginningWithSameStringAndLongerOneIsTheFirst() {
    Entry f1 = new FileEntry(null, "file1", null, null);
    Entry f2 = new FileEntry(null, "file", null, null);

    root.addChild(f1);
    root.addChild(f2);

    assertSame(f1, root.findEntry("file1"));
    assertSame(f2, root.findEntry("file"));
  }

  @Test
  public void testCompositeNames() {
    Entry dir = new DirectoryEntry(null, "c:/root/dir", null);
    Entry f = new FileEntry(null, "file", null, null);

    root.addChild(dir);
    dir.addChild(f);

    assertSame(dir, root.findEntry("c:/root/dir"));
    assertSame(f, root.findEntry("c:/root/dir/file"));

    assertNull(root.findEntry("c:"));
    assertNull(root.findEntry("c:/root"));
  }

  @Test
  public void testGettingUnknownEntryThrowsException() {
    try {
      root.getEntry("unknown entry");
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry 'unknown entry' not found", e.getMessage());
    }

    try {
      root.getEntry(42);
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry #42 not found", e.getMessage());
    }
  }

  //@Test
  //public void testFindingByIdPath() {
  //  FileEntry e = new FileEntry(1, null, null, null);
  //  root.addChild(e);
  //
  //  assertSame(e, root.findEntry(idp(1)));
  //  assertNull(root.findEntry(idp(2)));
  //}
}
