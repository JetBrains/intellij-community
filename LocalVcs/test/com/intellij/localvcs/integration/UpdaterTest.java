package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestCase;
import com.intellij.localvcs.TestStorage;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import java.io.IOException;

public class UpdaterTest extends TestCase {
  private LocalVcs vcs;
  private TestVirtualFile root;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createRoot("root");

    root = new TestVirtualFile("root", null);
  }

  @Test
  public void testUpdatingRoot() throws IOException {
    assertFalse(vcs.hasEntry("c:/root"));

    Updater.updateRoots(vcs, new TestVirtualFile("c:/root", null));

    // todo make this assertion a bit more explicit
    assertTrue(vcs.hasEntry("c:/root"));
  }

  @Test
  public void testUpdatingSeveralRoots() throws IOException {
    Updater.updateRoots(vcs,
                        new TestVirtualFile("c:/root1", null),
                        new TestVirtualFile("c:/root2", null));

    assertTrue(vcs.hasEntry("c:/root1"));
    assertTrue(vcs.hasEntry("c:/root2"));
  }

  @Test
  public void testDoesNotAddNestedRoots() throws IOException {
    Updater.updateRoots(vcs,
                        new TestVirtualFile("c:/root", null),
                        new TestVirtualFile("c:/root/nested", null));

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/nested"));
  }

  @Test
  public void testSelectingOnlyNotNestedRoots() {
    VirtualFile[] result = Updater.selectRoots(
      new TestVirtualFile("c:/dir1", null),
      new TestVirtualFile("c:/dir1/dir2", null),
      new TestVirtualFile("c:/dir2", null),
      new TestVirtualFile("c:/dir2/dir3/dir4", null),
      new TestVirtualFile("c:/dir3/dir4", null));

    assertEquals(3, result.length);
    assertEquals("c:/dir1", result[0].getPath());
    assertEquals("c:/dir2", result[1].getPath());
    assertEquals("c:/dir3/dir4", result[2].getPath());
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
  public void testDoesNothingWithUnchangedEntries() throws IOException {
    vcs.createDirectory("root/dir", 1L);
    vcs.createFile("root/dir/file", "content", 1L);
    vcs.apply();

    TestVirtualFile dir = new TestVirtualFile("dir", 1L);
    TestVirtualFile file = new TestVirtualFile("file", "content", 1L);

    root.addChild(dir);
    dir.addChild(file);

    Updater.updateRoots(vcs, root);

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));
    assertEquals("content", vcs.getEntry("root/dir/file").getContent());
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
