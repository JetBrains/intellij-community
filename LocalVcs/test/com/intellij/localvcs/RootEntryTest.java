package com.intellij.localvcs;

import org.junit.Before;
import org.junit.Test;

public class RootEntryTest extends TestCase {
  private RootEntry root;
  private Entry child;

  @Before
  public void setUp() {
    root = new RootEntry("");
    child = new DirectoryEntry(1, "child", null);
    root.addChild(child);
  }

  @Test
  public void testRenaming() {
    root.setPath("c:/root");
    assertEquals("c:/root", root.getName());
    assertEquals(p("c:/root"), root.getPath());
  }

  @Test
  public void testIdPathToChildren() {
    assertEquals(idp(1), child.getIdPath());
  }

  @Test
  public void testPathToChildren() {
    assertEquals(p("/child"), child.getPath());
  }

  @Test
  public void testPathToChildrenWithDriveLetter() {
    root = new RootEntry("c:/root");
    child = new DirectoryEntry(1, "child", null);
    root.addChild(child);

    assertEquals(p("c:/root/child"), child.getPath());
  }

  @Test
  public void testFindingChildren() {
    assertTrue(root.hasEntry(p("/child")));
    assertSame(child, root.getEntry(p("/child")));
  }

  @Test
  public void testFindingEntriesInTree() {
    root = new RootEntry("");
    Entry dir = new DirectoryEntry(null, "dir", null);
    Entry file1 = new FileEntry(null, "file1", null, null);
    Entry file2 = new FileEntry(null, "file2", null, null);

    root.addChild(dir);
    root.addChild(file1);
    dir.addChild(file2);

    assertSame(dir, root.findEntry(p("/dir")));
    assertSame(file1, root.findEntry(p("/file1")));
    assertSame(file2, root.findEntry(p("/dir/file2")));
  }

  @Test
  public void testGettingEntry() {
    Entry e1 = root.getEntry(p("/child"));
    Entry e2 = root.getEntry(e1.getId());

    assertSame(child, e1);
    assertSame(child, e2);
  }

  @Test
  public void testGettingEntryUnderDirectory() {
    root = new RootEntry("");
    root.doCreateDirectory(1, p("/dir1"), null);
    root.doCreateDirectory(2, p("/dir1/dir2"), null);
    root.doCreateFile(3, p("/dir1/file"), "content", null);

    Entry e1 = root.getEntry(p("/dir1/dir2"));
    Entry e2 = root.getEntry(p("/dir1/file"));

    assertEquals("dir2", e1.getName());
    assertEquals("file", e2.getName());
    assertEquals("content", e2.getContent());

    assertSame(e1, root.getEntry(e1.getId()));
    assertSame(e2, root.getEntry(e2.getId()));
  }

  @Test
  public void testDoesNotFindUnknownEntry() {
    assertNull(root.findEntry(p("/unknown entry")));
    assertNull(root.findEntry(p("/root/unknown entry")));
  }

  @Test
  public void testGettingUnknownEntryThrowsException() {
    try {
      root.getEntry(p("/unknown entry"));
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
    ChangeSet cs = cs(new CreateFileChange(1, "/file", null, null));

    RootEntry original = new RootEntry("");
    original.apply_old(cs);

    RootEntry result = original.revert_old(cs);

    assertTrue(original.hasEntry(p("/file")));
    assertFalse(result.hasEntry(p("/file")));
  }

  @Test
  public void testRevertingSeveralTimesOnSameSnapshot() {
    root.apply_old(cs(new CreateFileChange(2, "/file", "content", null)));

    ChangeSet cs = cs(new ChangeFileContentChange("/file", "new content", null));
    root.apply_old(cs);

    RootEntry result1 = root.revert_old(cs);
    RootEntry result2 = root.revert_old(cs);

    assertEquals("new content", root.getEntry(p("/file")).getContent());
    assertEquals("content", result1.getEntry(p("/file")).getContent());
    assertEquals("content", result2.getEntry(p("/file")).getContent());
  }
}
