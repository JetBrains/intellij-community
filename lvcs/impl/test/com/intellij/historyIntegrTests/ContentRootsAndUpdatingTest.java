package com.intellij.historyIntegrTests;


import com.intellij.history.LocalHistory;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ContentRootsAndUpdatingTest extends IntegrationTestCase {
  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testUpdatingFilesOnRootsChanges() throws Exception {
    VirtualFile root = addContentRootWithFiles("file1.java", "file2.java");

    assertTrue(hasVcsEntry(root.getPath() + "/file1.java"));
    assertTrue(hasVcsEntry(root.getPath() + "/file2.java"));
  }

  public void testTreatingAllChangesDuringUpdateAsOne() {
    VirtualFile root = addContentRootWithFiles("file1.java", "file2.java");
    assertEquals(1, getVcsRevisionsFor(root).size());
  }

  public void testDeletingContentRoot() throws Exception {
    VirtualFile newRoot = addContentRootWithFiles("file.java");
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

  public void testDoesNotIncludeChangesMadeBeforeRootCreation() throws Exception {
    // this test means for ensuring that change list contains
    // root creation change which could be missed due to
    // vfs architecture
    LocalHistory.putSystemLabel(myProject, "a");
    LocalHistory.putSystemLabel(myProject, "b");

    VirtualFile newRoot = addContentRootWithFiles("f.java");
    assertEquals(1, getVcsRevisionsFor(newRoot).size());
    assertEquals(1, getVcsRevisionsFor(newRoot.findChild("f.java")).size());
  }

  public void testDeletionOfOuterContentRootWhichIncludesIprFileSoIsImplicit() throws Exception {
    LocalFileSystem fs = LocalFileSystem.getInstance();
    File outer = getIprFile().getParentFile();
    File inner = new File(outer, "inner");
    inner.mkdirs();

    VirtualFile outerRoot = fs.refreshAndFindFileByIoFile(outer);
    ContentEntry outerEntry = PsiTestUtil.addContentRoot(myModule, outerRoot);

    VirtualFile innerRoot = fs.refreshAndFindFileByIoFile(inner);
    PsiTestUtil.addContentRoot(myModule, innerRoot);

    PsiTestUtil.removeContentEntry(myModule, outerEntry);
  }

  public void testRecreationOfContentRootDoesNotThrowException() throws Exception {
    VirtualFile newRoot = addContentRoot();
    String path = newRoot.getPath();
    String name = newRoot.getName();
    String parentPath = newRoot.getParent().getPath();

    newRoot.delete(null);
    assertFalse(hasVcsEntry(path));

    VirtualFile parent = findFile(parentPath);
    parent.createChildDirectory(null, name); // shouldn't throw

    assertTrue(hasVcsEntry(path));
  }

  public void testUpdateAndRefreshAtTheSameTime() throws Exception {
    // this test reproduces situation when roots changes and refreshes
    // work simultaneously and lead to 'entry already exists' exception.
    VirtualFile dir = root.createChildDirectory(this, "dir");
    addJarDirectory(dir);

    File jarExternal = new File(createJarWithSomeFiles());
    File txtExternal = new File(createFileExternally("f.txt"));

    File jar = new File(dir.getPath(), "f.jar");
    File txt = new File(dir.getPath(), "f.txt");

    FileUtil.copy(jarExternal, jar);
    FileUtil.copy(txtExternal, txt);

    VirtualFileManager.getInstance().refresh(false); // shouldn't throw
  }

  private void addJarDirectory(VirtualFile dir) {
    ModifiableRootModel rm = ModuleRootManager.getInstance(myModule).getModifiableModel();

    Library l = rm.getModuleLibraryTable().createLibrary();
    Library.ModifiableModel lm = l.getModifiableModel();
    lm.addJarDirectory(dir, true);
    lm.commit();

    rm.commit();
  }

  public void testIgnoreContentRootsNotFromLocalFileSystem() throws IOException {
    String path = createJarWithSomeFiles();
    VirtualFile file = getJFS().refreshAndFindFileByPath(path + "!/");

    int before = getVcs().getRoots().size();
    PsiTestUtil.addContentRoot(myModule, file);

    assertEquals(before, getVcs().getRoots().size());
  }

  private String createJarWithSomeFiles() throws IOException {
    File dir = createTempDirectory();
    File f = new File(dir, "root.jar");
    OutputStream fs = new FileOutputStream(f);
    JarOutputStream s = new JarOutputStream(fs);

    s.putNextEntry(new ZipEntry("file.java"));
    s.write(new byte[]{2});
    s.close();

    return f.getPath();
  }

  private JarFileSystem getJFS() {
    return JarFileSystem.getInstance();
  }

  private VirtualFile findFile(String parentPath) {
    return getFS().findFileByPath(parentPath);
  }
}