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
import java.util.List;

public class UpdaterTest extends TestCase {
  private LocalVcs vcs;
  private TestVirtualFile root;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("root", 1L);
    vcs.apply();

    root = new TestVirtualFile("root", 1L);
  }

  @Test
  public void testAddingRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());

    Updater.update(vcs, new TestVirtualFile("c:/root1", 1L), new TestVirtualFile("c:/root2", 2L));

    Entry e1 = vcs.findEntry("c:/root1");
    Entry e2 = vcs.findEntry("c:/root2");

    assertNotNull(e1);
    assertEquals(1L, e1.getTimestamp());

    assertNotNull(e2);
    assertEquals(2L, e2.getTimestamp());
  }

  @Test
  public void testDoesNotAddNestedRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    Updater.update(vcs, new TestVirtualFile("c:/root", null), new TestVirtualFile("c:/root/nested", null));

    assertTrue(vcs.hasEntry("c:/root"));
    assertFalse(vcs.hasEntry("c:/root/nested"));
  }

  @Test
  public void testSelectingOnlyNotNestedRoots() {
    VirtualFile[] result = Updater.selectNonNestedRoots(new TestVirtualFile("c:/dir1", null), new TestVirtualFile("c:/dir1/dir2", null), new TestVirtualFile("c:/dir2", null),
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

    Updater.update(vcs, new TestVirtualFile("c:/another root", null));

    assertFalse(vcs.hasEntry("c:/root"));
    assertTrue(vcs.hasEntry("c:/another root"));
  }

  @Test
  public void testAddingNewFiles() {
    TestVirtualFile dir = new TestVirtualFile("dir", 1L);
    TestVirtualFile file = new TestVirtualFile("file", "content", 2L);

    root.addChild(dir);
    dir.addChild(file);

    update();

    assertTrue(vcs.hasEntry("root/dir"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    assertEquals(1L, vcs.getEntry("root/dir").getTimestamp());

    Entry e = vcs.getEntry("root/dir/file");
    assertEquals("content", e.getContent());
    assertEquals(2L, e.getTimestamp());
  }

  @Test
  public void testDeletingAbsentFiles() {
    vcs.createFile("root/file", null, null);
    vcs.createDirectory("root/dir", null);
    vcs.createFile("root/dir/file", null, null);
    vcs.apply();

    assertTrue(vcs.hasEntry("root/file"));
    assertTrue(vcs.hasEntry("root/dir/file"));

    update();

    assertFalse(vcs.hasEntry("root/file"));
    assertFalse(vcs.hasEntry("root/dir"));
    assertFalse(vcs.hasEntry("root/dir/file"));
  }

  @Test
  public void testDoesNothingWithUpToDateEntries() {
    vcs.createDirectory("root/dir", 1L);
    vcs.createFile("root/dir/file", "content", 1L);
    vcs.apply();

    Entry e1 = vcs.getEntry("root/dir");
    Entry e2 = vcs.getEntry("root/dir/file");

    TestVirtualFile dir = new TestVirtualFile("dir", 1L);
    TestVirtualFile file = new TestVirtualFile("file", "new content", 1L);

    root.addChild(dir);
    dir.addChild(file);

    update();

    assertSame(e1, vcs.getEntry("root/dir"));
    assertSame(e2, vcs.getEntry("root/dir/file"));
    assertEquals("content", e2.getContent());
  }

  @Test
  public void testUpdatingOutdatedFiles() {
    vcs.createFile("root/file", "content", 111L);
    vcs.apply();

    root.addChild(new TestVirtualFile("file", "new content", 222L));

    update();

    Entry e = vcs.getEntry("root/file");

    assertEquals("new content", e.getContent());
    assertEquals(222L, e.getTimestamp());
  }

  @Test
  @Ignore
  public void testUpdatingOutdatedDirectories() {
    vcs.createDirectory("root/dir", 111L);
    vcs.apply();

    root.addChild(new TestVirtualFile("dir", 222L));
    update();

    assertEquals(222L, vcs.getEntry("root/dir").getTimestamp());
  }

  @Test
  @Ignore
  public void testUpdatingOutdatedRoots() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("c:/root", 111L);
    vcs.apply();

    root = new TestVirtualFile("c:/root", 222L);
    update();

    List<Entry> roots = vcs.getRoots();

    assertEquals(1, roots.size());
    assertEquals(222L, roots.get(0).getTimestamp());
  }

  @Test
  public void testDeletingFileAndCreatingDirectoryWithSameName() {
    vcs.createFile("root/name1", null, 1L);
    vcs.createDirectory("root/name2", 1L);
    vcs.apply();

    root.addChild(new TestVirtualFile("name1", 1L));
    root.addChild(new TestVirtualFile("name2", "", 1L));
    update();

    assertTrue(vcs.getEntry("root/name1").isDirectory());
    assertFalse(vcs.getEntry("root/name2").isDirectory());
  }

  private void update() {
    try {
      Updater.update(vcs, root);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
