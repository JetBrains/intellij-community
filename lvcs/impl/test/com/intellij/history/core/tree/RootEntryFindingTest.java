package com.intellij.history.core.tree;

import com.intellij.history.core.LocalVcsTestCase;
import com.intellij.history.core.Paths;
import org.junit.Test;

public class RootEntryFindingTest extends LocalVcsTestCase {
  private Entry root = new RootEntry();

  @Test
  public void testFindingEntry() {
    FileEntry e = new FileEntry(-1, "file", null, -1);
    root.addChild(e);
    assertSame(e, root.findEntry("file"));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry("unknown entry"));
    assertNull(root.findEntry("root/unknown entry"));
  }

  @Test
  public void testFindingEntryUnderDirectory() {
    DirectoryEntry dir = new DirectoryEntry(-1, "dir");
    FileEntry file = new FileEntry(-1, "file", null, -1);

    dir.addChild(file);
    root.addChild(dir);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file, root.findEntry("dir/file"));
    assertNull(root.findEntry("dir/another"));
  }

  @Test
  public void testFindingEntriesInTree() {
    root = new RootEntry();
    Entry dir = new DirectoryEntry(-1, "dir");
    Entry file1 = new FileEntry(-1, "file1", null, -1);
    Entry file2 = new FileEntry(-1, "file2", null, -1);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(dir, root.findEntry("dir"));
    assertSame(file1, root.findEntry("file1"));
    assertSame(file2, root.findEntry("dir/file2"));
  }

  @Test
  public void testFindingUnderRoots() {
    Entry dir1 = new DirectoryEntry(-1, "c:/dir1");
    Entry dir2 = new DirectoryEntry(-1, "c:/dir2");
    Entry file1 = new FileEntry(-1, "file1", null, -1);
    Entry file2 = new FileEntry(-1, "file2", null, -1);
    dir1.addChild(file1);
    dir2.addChild(file2);
    root.addChild(dir1);
    root.addChild(dir2);

    assertSame(dir1, root.findEntry("c:/dir1"));
    assertSame(dir2, root.findEntry("c:/dir2"));
    assertSame(file1, root.findEntry("c:/dir1/file1"));
    assertSame(file2, root.findEntry("c:/dir2/file2"));
  }

  @Test
  public void testNamesOfDirectoriesBeginningWithTheSameString() {
    Entry d1 = new DirectoryEntry(-1, "dir");
    Entry d2 = new DirectoryEntry(-1, "dir2");
    Entry f = new FileEntry(-1, "file", null, -1);

    root.addChild(d1);
    root.addChild(d2);
    d2.addChild(f);

    assertSame(f, root.findEntry("dir2/file"));
    assertNull(root.findEntry("dir/file"));
  }

  @Test
  public void testNamesOfEntriesBeginningWithSameStringAndLongerOneIsTheFirst() {
    Entry f1 = new FileEntry(-1, "file1", null, -1);
    Entry f2 = new FileEntry(-1, "file", null, -1);

    root.addChild(f1);
    root.addChild(f2);

    assertSame(f1, root.findEntry("file1"));
    assertSame(f2, root.findEntry("file"));
  }

  @Test
  public void testCompositeNames() {
    Entry dir = new DirectoryEntry(-1, "c:/root/dir");
    Entry f = new FileEntry(-1, "file", null, -1);

    root.addChild(dir);
    dir.addChild(f);

    assertSame(dir, root.findEntry("c:/root/dir"));
    assertSame(f, root.findEntry("c:/root/dir/file"));

    assertNull(root.findEntry("c:"));
    assertNull(root.findEntry("c:/root"));
  }

  @Test
  public void testFindingIsRelativeToFileSystemCaseSensivity() {
    root.addChild(new FileEntry(-1, "file", null, -1));

    Paths.setCaseSensitive(true);
    assertNull(root.findEntry("FiLe"));
    assertNotNull(root.findEntry("file"));

    Paths.setCaseSensitive(false);
    assertNotNull(root.findEntry("FiLe"));
    assertNotNull(root.findEntry("file"));
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

  @Test
  public void testFindingByIdPath() {
    Entry e = new FileEntry(1, null, null, -1);
    root.addChild(e);

    assertSame(e, root.findEntry(idp(-1, 1)));
    assertNull(root.findEntry(idp(-1, 2)));
  }

  @Test
  public void testFindingByIdPathUnderDirectory() {
    Entry dir = new DirectoryEntry(1, null);
    Entry file = new FileEntry(2, null, null, -1);

    dir.addChild(file);
    root.addChild(dir);

    assertSame(dir, root.findEntry(idp(-1, 1)));
    assertSame(file, root.findEntry(idp(-1, 1, 2)));
  }

  @Test
  public void testDoesNotFindByShortIdPathUnderDirectory() {
    Entry dir = new DirectoryEntry(1, null);
    Entry file = new FileEntry(2, null, null, -1);

    dir.addChild(file);
    root.addChild(dir);

    assertNull(root.findEntry(idp(2)));
  }

  @Test
  public void testDoesNotFindByWrongShorterIdPath() {
    Entry dir1 = new DirectoryEntry(1, null);
    Entry dir2 = new DirectoryEntry(2, null);
    Entry file = new FileEntry(3, null, null, -1);

    root.addChild(dir1);
    dir1.addChild(dir2);
    dir2.addChild(file);

    assertNull(root.findEntry(idp(-1, 1, 3)));
    assertSame(dir2, root.findEntry(idp(-1, 1, 2)));
  }

  @Test
  public void testDoesNotFindByWrongLongerIdPath() {
    Entry dir = new DirectoryEntry(1, null);
    Entry file = new FileEntry(3, null, null, -1);

    root.addChild(dir);
    dir.addChild(file);

    assertNull(root.findEntry(idp(1, 2, 3)));
  }

  @Test
  public void testFindingByIdPathUnderSecondDirectory() {
    Entry dir1 = new DirectoryEntry(1, "1");
    Entry dir2 = new DirectoryEntry(2, "2");
    Entry file = new FileEntry(3, "3", null, -1);

    root.addChild(dir1);
    root.addChild(dir2);
    dir2.addChild(file);

    assertSame(file, root.findEntry(idp(-1, 2, 3)));
    assertSame(dir1, root.findEntry(idp(-1, 1)));
    assertSame(dir2, root.findEntry(idp(-1, 2)));

    assertNull(root.findEntry(idp(-1, 1, 3)));
  }

  @Test
  public void testTHrowingExceptionOnGettingEntryByWrongIdPath() {
    try {
      root.getEntry(idp(1, 2, 3));
      fail();
    }
    catch (RuntimeException e) {
      assertEquals("entry '1.2.3' not found", e.getMessage());
    }
  }
}
