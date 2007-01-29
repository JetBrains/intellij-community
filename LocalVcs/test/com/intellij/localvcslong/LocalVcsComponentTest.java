package com.intellij.localvcslong;


import com.intellij.idea.Bombed;
import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.FileFilter;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;

public class LocalVcsComponentTest extends IdeaTestCase {
  private VirtualFile root;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        root = addContentRoot();
      }
    });
  }

  public void testComponentInitialisation() {
    assertNotNull(getVcsComponent());
  }

  public void testUpdatingOnRootsChanges() {
    VirtualFile root = addContentRoot();

    Entry e = getVcs().findEntry(root.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testUpdatingFilesOnRootsChanges() throws Exception {
    VirtualFile root = addContentRootWithFile("file.java", myModule);

    assertTrue(getVcs().hasEntry(root.getPath() + "/file.java"));
  }

  public void testRemovingContentRoot() throws Exception {
    VirtualFile newRoot = addContentRootWithFile("file.java", myModule);
    newRoot.delete(null);

    assertTrue(vcsHasEntryFor(root));
    assertFalse(vcsHasEntryFor(newRoot));
  }

  public void testRemovingSourceRootWithFileDoesNotCauseException() throws Exception {
    VirtualFile src = root.createChildDirectory(null, "src");
    VirtualFile f = src.createChildData(null, "file.java");

    PsiTestUtil.addSourceRoot(myModule, src);

    assertTrue(vcsHasEntryFor(f));

    src.delete(null);

    assertFalse(vcsHasEntryFor(f));
  }

  public void testUpdatingOnStartup() throws Exception {
    // todo cant make idea do that i want...

    // create project with some files
    // close project
    // modify some files
    // open project
    // verify that files were updated
  }

  public void testCreatingFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }

  public void testIgnoringFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(vcsHasEntryFor(f));
  }

  public void testRenamingFileContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.rename(null, "file2.java");

    assertFalse(getVcs().hasEntry(Paths.renamed(f.getPath(), "file.java")));
    assertTrue(vcsHasEntryFor(f));
  }

  public void testDeletingFilteredBigFiles() throws Exception {
    File tempDir = createTempDirectory();
    File tempFile = new File(tempDir, "bigFile.java");
    OutputStream s = new FileOutputStream(tempFile);
    s.write(new byte[(int)(FileFilter.MAX_FILE_SIZE + 1)]);
    s.close();

    VirtualFile f = LocalFileSystem.getInstance().findFileByIoFile(tempFile);

    f.move(null, root);
    assertFalse(vcsHasEntryFor(f));

    f.delete(null);
    assertFalse(vcsHasEntryFor(f));
  }

  @Bombed(
    year = 2007,
    month = Calendar.FEBRUARY,
    day = 15,
    time = 15,
    user = "mike",
    description = "State refactoring"
  )
  public void testSaving() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    myProject.save();

    Storage s = new Storage(getVcsComponent().getStorageDir());
    LocalVcs vcs = new LocalVcs(s);
    s.close();
    assertTrue(vcs.hasEntry(f.getPath()));
  }

  public void testProvidingContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent(new byte[]{1});

    assertEquals(1, f.contentsToByteArray()[0]);

    getVcs().changeFileContent(f.getPath(), new byte[]{2}, null);
    getVcs().apply();
    assertEquals(2, f.contentsToByteArray()[0]);
  }

  public void testContentForFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.exe");
    f.setBinaryContent(new byte[]{1});

    assertFalse(vcsHasEntryFor(f));
    assertEquals(1, f.contentsToByteArray()[0]);
  }

  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, getVcsContentOf(f)[0]);

    LocalVcsAction a = getVcsComponent().startAction("label");
    assertEquals(1, getVcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, getVcsContentOf(f)[0]);

    List<Label> l = getVcs().getLabelsFor(f.getPath());
    assertEquals("label", l.get(0).getName());
    assertNull(l.get(1).getName());
  }

  private void setDocumentTextFor(VirtualFile f, byte[] bytes) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    String t = new String(bytes);
    d.setText(t);
  }

  private byte[] getVcsContentOf(VirtualFile f) {
    return getVcs().getEntry(f.getPath()).getContent().getBytes();
  }

  private boolean vcsHasEntryFor(VirtualFile f) {
    return getVcs().hasEntry(f.getPath());
  }

  private ILocalVcs getVcs() {
    return LocalVcsComponent.getLocalVcsFor(getProject());
  }

  private LocalVcsComponent getVcsComponent() {
    return (LocalVcsComponent)LocalVcsComponent.getInstance(getProject());
  }

  private VirtualFile addContentRoot() {
    return addContentRoot(myModule);
  }

  private VirtualFile addContentRoot(Module m) {
    return addContentRootWithFile(null, m);
  }

  private VirtualFile addContentRootWithFile(String fileName, Module module) {
    try {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      File dir = createTempDirectory();

      if (fileName != null) {
        File f = new File(dir, fileName);
        f.createNewFile();
      }

      VirtualFile root = fs.findFileByIoFile(dir);
      PsiTestUtil.addContentRoot(module, root);
      return root;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
