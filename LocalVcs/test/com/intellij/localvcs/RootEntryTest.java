package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class RootEntryTest extends TestCase {
  private RootEntry root;
  private Entry child;

  @Before
  public void setUp() {
    root = new RootEntry();
    child = new DirectoryEntry(1, "child");
    root.addChild(child);
  }

  @Test
  public void testPathToChildren() {
    assertSame(root, child.getParent());
    assertEquals(p("child"), child.getPath());
  }

  @Test
  public void testFindingChildren() {
    assertTrue(root.hasEntry(p("child")));
    assertSame(child, root.getEntry(p("child")));
  }

  @Test
  public void testFindingEntriesInTree() {
    root = new RootEntry();
    Entry dir = new DirectoryEntry(null, "dir");
    Entry file1 = new FileEntry(null, "file1", null);
    Entry file2 = new FileEntry(null, "file2", null);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(dir, root.findEntry(p("dir")));
    assertSame(file1, root.findEntry(p("file1")));
    assertSame(file2, root.findEntry(p("dir/file2")));
  }

  @Test
  public void testGettingEntry() {
    Entry e1 = root.getEntry(p("child"));
    Entry e2 = root.getEntry(e1.getObjectId());

    assertSame(child, e1);
    assertSame(child, e2);
  }

  @Test
  public void testGettingEntryUnderDirectory() {
    root = new RootEntry();
    root.doCreateDirectory(p("dir1"), 1);
    root.doCreateDirectory(p("dir1/dir2"), 2);
    root.doCreateFile(p("dir1/file"), "content", 3);

    Entry e1 = root.getEntry(p("dir1/dir2"));
    Entry e2 = root.getEntry(p("dir1/file"));

    assertEquals("dir2", e1.getName());
    assertEquals("file", e2.getName());
    assertEquals("content", e2.getContent());

    assertSame(e1, root.getEntry(e1.getObjectId()));
    assertSame(e2, root.getEntry(e2.getObjectId()));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry(p("unknown entry")));
    assertNull(root.findEntry(p("root/unknown entry")));
  }

  public void testGettingUnknownEntryThrowsException() {
    try {
      root.getEntry(p("unknown entry"));
      fail();
    } catch (LocalVcsException e) {}

    try {
      root.getEntry(42);
      fail();
    } catch (LocalVcsException e) {}
  }

  @Test
  public void testCopying() {
    Entry copy = root.copy();

    assertEquals(RootEntry.class, copy.getClass());
    assertEquals(1, copy.getChildren().size());

    assertNotSame(child, copy.getChildren().get(0));
    assertEquals("child", copy.getChildren().get(0).getName());
  }

  @Test
  public void testApplyingReturnsCopy() {
    RootEntry original = new RootEntry();
    RootEntry result
        = original.apply(cs(new CreateFileChange(p("file"), null, null)));

    assertFalse(original.hasEntry(p("file")));
    assertTrue(result.hasEntry(p("file")));
  }

  @Test
  public void testRevertingReturnsCopy() {
    ChangeSet cs = cs(new CreateFileChange(p("file"), null, null));

    RootEntry original = new RootEntry().apply(cs);
    RootEntry result = original.revert(cs);

    assertTrue(original.hasEntry(p("file")));
    assertFalse(result.hasEntry(p("file")));
  }
}
