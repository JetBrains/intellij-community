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
  public void testIdPathToChildren() {
    assertEquals(idp(1), child.getIdPath());
  }

  @Test
  public void testPathToChildren() {
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
    Entry e2 = root.getEntry(e1.getId());

    assertSame(child, e1);
    assertSame(child, e2);
  }

  @Test
  public void testGettingEntryUnderDirectory() {
    root = new RootEntry();
    root.doCreateDirectory(1, p("dir1"));
    root.doCreateDirectory(2, p("dir1/dir2"));
    root.doCreateFile(3, p("dir1/file"), "content");

    Entry e1 = root.getEntry(p("dir1/dir2"));
    Entry e2 = root.getEntry(p("dir1/file"));

    assertEquals("dir2", e1.getName());
    assertEquals("file", e2.getName());
    assertEquals("content", e2.getContent());

    assertSame(e1, root.getEntry(e1.getId()));
    assertSame(e2, root.getEntry(e2.getId()));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry(p("unknown entry")));
    assertNull(root.findEntry(p("root/unknown entry")));
  }

  @Test
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
  public void testRevertingReturnsCopy() {
    ChangeSet cs = cs(new CreateFileChange(null, p("file"), null));

    RootEntry original = new RootEntry();
    original.apply(cs);

    RootEntry result = original.revert(cs);

    assertTrue(original.hasEntry(p("file")));
    assertFalse(result.hasEntry(p("file")));
  }

  @Test
  public void testRevertingSeveralTimesOnSameSnapshot() {
    root.apply(cs(new CreateFileChange(null, p("file"), "content")));

    ChangeSet cs = cs(new ChangeFileContentChange(p("file"), "new content"));
    root.apply(cs);

    RootEntry result1 = root.revert(cs);
    RootEntry result2 = root.revert(cs);

    assertEquals("new content", root.getEntry(p("file")).getContent());
    assertEquals("content", result1.getEntry(p("file")).getContent());
    assertEquals("content", result2.getEntry(p("file")).getContent());
  }
}
