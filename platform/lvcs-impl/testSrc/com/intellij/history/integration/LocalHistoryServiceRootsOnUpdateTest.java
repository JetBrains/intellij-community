package com.intellij.history.integration;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

public class LocalHistoryServiceRootsOnUpdateTest extends LocalHistoryServiceTestCase {
  TestVirtualFile root;

  @Test
  public void testAddingRootsWithFiles() {
    root = new TestVirtualFile("root");
    root.addChild(new TestVirtualFile("file", "", -1));
    roots.add(root);

    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root"));
    assertTrue(vcs.hasEntry("root/file"));
  }

  @Test
  public void testAddingRootsWithFiltering() {
    TestVirtualFile f = new TestVirtualFile("file", "", -1);
    root = new TestVirtualFile("root");
    root.addChild(f);
    roots.add(root);

    fileFilter.setNotAllowedFiles(f);
    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root"));
    assertFalse(vcs.hasEntry("root/file"));
  }

  @Test
  public void testRemovingRoots() {
    vcs.createDirectory("root");

    roots.clear();
    fireUpdateRoots();

    assertTrue(vcs.getRoots().isEmpty());
  }

  @Test
  public void testRenamingContentRoot() {
    vcs.createDirectory("c:/dir/root");
    long timestamp = -1;
    vcs.createFile("c:/dir/root/file", null, timestamp, false);

    TestVirtualFile dir = new TestVirtualFile("c:/dir");
    root = new TestVirtualFile("newName");
    dir.addChild(root);

    fileManager.firePropertyChanged(root, VirtualFile.PROP_NAME, "root");

    assertFalse(vcs.hasEntry("c:/dir/root"));
    assertTrue(vcs.hasEntry("c:/dir/newName"));
    assertTrue(vcs.hasEntry("c:/dir/newName/file"));
  }

  @Test
  public void testDeletingContentRootExternally() {
    vcs.createDirectory("root");

    root = new TestVirtualFile("root");

    fileManager.fireFileDeletion(root);
    assertFalse(vcs.hasEntry("root"));
  }

  private void fireUpdateRoots() {
    rootManager.updateRoots();
  }
}