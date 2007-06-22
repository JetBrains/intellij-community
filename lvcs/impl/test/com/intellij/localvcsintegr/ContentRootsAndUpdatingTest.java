package com.intellij.localvcsintegr;


import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;

public class ContentRootsAndUpdatingTest extends IntegrationTestCase {
  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testUpdatingFilesOnRootsChanges() throws Exception {
    VirtualFile root = addContentRootWithFiles(myModule, "file1.java", "file2.java");

    assertTrue(hasVcsEntry(root.getPath() + "/file1.java"));
    assertTrue(hasVcsEntry(root.getPath() + "/file2.java"));
  }

  public void testTreatingAllChangesDuringUpdateAsOne() {
    VirtualFile root = addContentRootWithFiles(myModule, "file1.java", "file2.java");
    assertEquals(1, getVcsRevisionsFor(root).size());
  }

  public void testDeletingContentRoot() throws Exception {
    VirtualFile newRoot = addContentRootWithFiles(myModule, "file.java");
    String path = newRoot.getPath();

    newRoot.delete(null);

    assertTrue(hasVcsEntry(root));
    assertFalse(hasVcsEntry(path));
  }

  public void testDeletingContentRootWithFileDoesNotCauseException() throws Exception {
    VirtualFile newRoot = addContentRoot();
    VirtualFile f = newRoot.createChildData(null, "file.java");

    String p = f.getPath();
    assertTrue(hasVcsEntry(p));

    newRoot.delete(null);

    assertFalse(hasVcsEntry(p));
  }

  public void testDeletingSourceRootWithFileDoesNotCauseException() throws Exception {
    VirtualFile src = root.createChildDirectory(null, "src");
    VirtualFile f = src.createChildData(null, "file.java");

    PsiTestUtil.addSourceRoot(myModule, src);

    String p = f.getPath();
    assertTrue(hasVcsEntry(p));

    src.delete(null);

    assertFalse(hasVcsEntry(p));
  }

  public void testUpdatingOnStartup() throws Exception {
    // todo i don't know how to write such test...

    // create project with some files
    // close project
    // modify some files
    // open project
    // verify that files were updated
  }
}