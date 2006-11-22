package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestCase;
import com.intellij.localvcs.TestStorage;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class UpdaterTest extends TestCase {
  private LocalVcs vcs;
  private TestVirtualFile root;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("root", null);
    vcs.apply();

    root = new TestVirtualFile("root", null);
  }

  @Test
  public void testAddingRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    Updater.updateRoots(vcs, new TestVirtualFile("c:/root1", null), new TestVirtualFile("c:/root2", null));

    assertTrue(vcs.hasEntry("c:/root1"));
    assertTrue(vcs.hasEntry("c:/root2"));
  }

  @Test
  public void testDoesNotAddNestedRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    Updater.updateRoots(vcs, new TestVirtualFile("c:/root", null), new TestVirtualFile("c:/root/nested", null));

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/nested"));
  }

  @Test
  public void testSelectingOnlyNotNestedRoots() {
    VirtualFile[] result = Updater.selectRoots(new TestVirtualFile("c:/dir1", null), new TestVirtualFile("c:/dir1/dir2", null), new TestVirtualFile("c:/dir2", null),
                                               new TestVirtualFile("c:/dir2/dir3/dir4", null), new TestVirtualFile("c:/dir3/dir4", null));

    assertEquals(3, result.length);
    assertEquals("c:/dir1", result[0].getPath());
    assertEquals("c:/dir2", result[1].getPath());
    assertEquals("c:/dir3/dir4", result[2].getPath());
  }

  @Test
  public void testDeletingRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("c:/root", null);
    vcs.apply();

    Updater.updateRoots(vcs, new TestVirtualFile("c:/another root", null));

    assertFalse(vcs.hasEntry("c:/root"));
    assertTrue(vcs.hasEntry("c:/another root"));
  }

  @Test
  public void testAddingNewFiles() throws IOException {
    TestVirtualFile dir = new TestVirtualFile("dir", 1L);
    TestVirtualFile file = new TestVirtualFile("file", "content", 2L);

    root.addChild(dir);
    dir.addChild(file);

    Updater.updateRoots(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    assertEquals(1L, vcs.getEntry("root/dir").getTimestamp());

    Entry e = vcs.getEntry("root/dir/file");
    assertEquals("content", e.getContent());
    assertEquals(2L, e.getTimestamp());
  }

  @Test
  public void testDeletingAbsentFiles() throws IOException {
    vcs.createFile("root/file", null, null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file", null, null);
    vcs.apply();

    assertTrue(vcs.hasEntry("root/file"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    Updater.updateRoots(vcs, root);

    assertFalse(vcs.hasEntry("root/file"));
    assertFalse(vcs.hasEntry("root/dir"));
    assertFalse(vcs.hasEntry("root/dir/file"));
  }

  @Test
  public void testDoesNothingWithNonOutdatedEntries() throws IOException {
    vcs.createDirectory("root/dir", 1L);
    vcs.createFile("root/dir/file", "content", 1L);
    vcs.apply();

    Entry e1 = vcs.getEntry("root/dir");
    Entry e2 = vcs.getEntry("root/dir/file");

    TestVirtualFile dir = new TestVirtualFile("dir", 1L);
    TestVirtualFile file = new TestVirtualFile("file", "new content", 1L);

    root.addChild(dir);
    dir.addChild(file);

    Updater.updateRoots(vcs, root);

    assertSame(e1, vcs.getEntry("root/dir"));
    assertSame(e2, vcs.getEntry("root/dir/file"));
    assertEquals("content", e2.getContent());
  }

  @Test
  public void testUpdatingOutdatedFiles() throws IOException {
    vcs.createFile("root/file", "content", 111L);
    vcs.apply();

    root.addChild(new TestVirtualFile("file", "new content", 222L));

    Updater.updateRoots(vcs, root);

    Entry e = vcs.getEntry("root/file");

    assertEquals("new content", e.getContent());
    assertEquals(222L, e.getTimestamp());
  }
}
