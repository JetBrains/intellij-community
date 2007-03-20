package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

public class LocalVcsServiceRootsOnUpdateTest extends LocalVcsServiceTestCase {
  TestVirtualFile root;

  @Test
  public void testAddingRootsWithFiles() {
    root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);

    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root"));
    assertTrue(vcs.hasEntry("root/file"));
  }

  @Test
  public void testAddingRootsWithFiltering() {
    TestVirtualFile f = new TestVirtualFile("file", "", null);
    root = new TestVirtualFile("root", null);
    root.addChild(f);
    roots.add(root);

    fileFilter.setNotAllowedFiles(f);
    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root"));
    assertFalse(vcs.hasEntry("root/file"));
  }

  @Test
  public void testRemovingRoots() {
    vcs.createDirectory("root", null);

    roots.clear();
    fireUpdateRoots();

    assertTrue(vcs.getRoots().isEmpty());
  }

  @Test
  public void testRenamingContentRoot() {
    vcs.createDirectory("c:/dir/root", null);
    vcs.createFile("c:/dir/root/file", null, null);

    TestVirtualFile dir = new TestVirtualFile("c:/dir", null);
    root = new TestVirtualFile("newName", null);
    dir.addChild(root);

    fileManager.firePropertyChanged(root, VirtualFile.PROP_NAME, "root");

    assertFalse(vcs.hasEntry("c:/dir/root"));
    assertTrue(vcs.hasEntry("c:/dir/newName"));
    assertTrue(vcs.hasEntry("c:/dir/newName/file"));
  }

  @Test
  public void testDeletingContentRootExternally() {
    vcs.createDirectory("root", null);

    root = new TestVirtualFile("root", null);

    fileManager.fireFileDeletion(root);
    assertFalse(vcs.hasEntry("root"));
  }

  private void fireUpdateRoots() {
    rootManager.updateRoots();
  }
}