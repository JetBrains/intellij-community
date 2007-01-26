package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
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
    vcs.apply();

    roots.clear();
    fireUpdateRoots();

    assertTrue(vcs.getRoots().isEmpty());
  }

  @Test
  public void testDoesNotAddNewFiles() {
    vcs.createDirectory("root", null);
    vcs.apply();

    root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("file", "", null));
    roots.add(root);
    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root"));
    assertFalse(vcs.hasEntry("root/file"));
  }

  @Test
  public void testDoesNotDeleteObsoleteFiles() {
    vcs.createDirectory("root", null);
    vcs.createFile("root/file", null, null);
    vcs.apply();

    roots.add(new TestVirtualFile("root", null));
    fireUpdateRoots();

    assertTrue(vcs.hasEntry("root/file"));
  }

  @Test
  public void testDoesNotUpdateOutdatedFiles() {
    vcs.createDirectory("root", null);
    vcs.createFile("root/file", b("old"), 123L);
    vcs.apply();

    root = new TestVirtualFile("root", null);
    root.addChild(new TestVirtualFile("file", "new", 456L));
    roots.add(root);
    fireUpdateRoots();

    assertEquals(c("old"), vcs.getEntry("root/file").getContent());
  }

  @Test
  public void testRenamingContentRoot() {
    vcs.createDirectory("c:/dir/root", null);
    vcs.createFile("c:/dir/root/file", null, null);
    vcs.apply();

    root = new TestVirtualFile("c:/dir/root", null);

    fileManager.fireBeforePropertyChange(new VirtualFilePropertyEvent(null, root, VirtualFile.PROP_NAME, null, "newName"));

    assertFalse(vcs.hasEntry("c:/dir/root"));
    assertTrue(vcs.hasEntry("c:/dir/newName"));
    assertTrue(vcs.hasEntry("c:/dir/newName/file"));
  }

  @Test
  public void testDeletingContentRootExternally() {
    vcs.createDirectory("root", null);
    vcs.apply();

    root = new TestVirtualFile("root", null);

    fileManager.fireBeforeFileDeletion(new VirtualFileEvent(null, root, null, null));
    assertFalse(vcs.hasEntry("root"));
  }

  private void fireUpdateRoots() {
    rootManager.updateRoots();
  }
}