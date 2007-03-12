package com.intellij.localvcsintegr;


import com.intellij.localvcs.Entry;
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
    VirtualFile root = addContentRootWithFile("file.java", myModule);

    assertTrue(vcsHasEntry(root.getPath() + "/file.java"));
  }

  public void testDeletingContentRoot() throws Exception {
    VirtualFile newRoot = addContentRootWithFile("file.java", myModule);
    String path = newRoot.getPath();

    newRoot.delete(null);

    assertTrue(vcsHasEntryFor(root));
    assertFalse(vcsHasEntry(path));
  }

  public void testDeletingContentRootWithFileDoesNotCauseException() throws Exception {
    VirtualFile newRoot = addContentRoot();
    VirtualFile f = newRoot.createChildData(null, "file.java");

    String p = f.getPath();
    assertTrue(vcsHasEntry(p));

    newRoot.delete(null);

    assertFalse(vcsHasEntry(p));
  }

  public void testDeletingSourceRootWithFileDoesNotCauseException() throws Exception {
    VirtualFile src = root.createChildDirectory(null, "src");
    VirtualFile f = src.createChildData(null, "file.java");

    PsiTestUtil.addSourceRoot(myModule, src);

    String p = f.getPath();
    assertTrue(vcsHasEntry(p));

    src.delete(null);

    assertFalse(vcsHasEntry(p));
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