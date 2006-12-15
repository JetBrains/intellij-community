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
  private TestFileFilter filter;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("root", 1L);
    vcs.apply();

    root = new TestVirtualFile("root", 1L);
    filter = new TestFileFilter();
  }

  @Test
  public void testAddingRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());

    update(new TestVirtualFile("c:/root1", 1L), new TestVirtualFile("c:/root2", 2L));

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

    TestVirtualFile parent = new TestVirtualFile("c:/parent", null);
    TestVirtualFile child = new TestVirtualFile("child", null);
    parent.addChild(child);

    update(parent, child);

    assertTrue(vcs.hasEntry("c:/parent"));
    assertFalse(vcs.hasEntry("c:/root/child"));
  }

  @Test
  public void testDeletingRoots() throws IOException {
    vcs = new LocalVcs(new TestStorage());
    vcs.createDirectory("c:/root", null);
    vcs.apply();

    update(new TestVirtualFile("c:/another root", null));

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
    assertEquals(c("content"), e.getContent());
    assertEquals(2L, e.getTimestamp());
  }

  @Test
  public void testFilteringFiles() {
    TestVirtualFile f = new TestVirtualFile("file", "", null);
    root.addChild(f);

    filter.setFilesWithUnallowedTypes(f);
    update();

    assertFalse(vcs.hasEntry("root/file"));
  }

  @Test
  public void testFilteringOnlyUnallowedFiles() {
    TestVirtualFile f1 = new TestVirtualFile("file1", "", null);
    TestVirtualFile f2 = new TestVirtualFile("file2", "", null);
    root.addChild(f1);
    root.addChild(f2);

    filter.setFilesWithUnallowedTypes(f1);
    update();

    assertFalse(vcs.hasEntry("root/file1"));
    assertTrue(vcs.hasEntry("root/file2"));
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
  public void testDoesNothingWithUpToDateFiles() {
    vcs.createDirectory("root/dir", 1L);
    vcs.createFile("root/dir/file", b("content"), 1L);
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
    assertEquals(c("content"), e2.getContent());
  }

  @Test
  public void testUpdatingOutdatedFiles() {
    vcs.createFile("root/file", b("content"), 111L);
    vcs.apply();

    root.addChild(new TestVirtualFile("file", "new content", 222L));

    update();

    Entry e = vcs.getEntry("root/file");

    assertEquals(c("new content"), e.getContent());
    assertEquals(222L, e.getTimestamp());
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
    update(root);
  }

  private void update(VirtualFile... roots) {
    try {
      Updater.update(vcs, filter, roots);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
