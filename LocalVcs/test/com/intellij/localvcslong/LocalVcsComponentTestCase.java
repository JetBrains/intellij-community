package com.intellij.localvcslong;

import com.intellij.localvcs.Clock;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public abstract class LocalVcsComponentTestCase extends IdeaTestCase {
  protected VirtualFile root;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Clock.useRealClock();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        root = addContentRoot();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  protected String createFileExternally(String name) throws Exception {
    File f = new File(root.getPath(), name);
    f.createNewFile();
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected void changeContentExternally(String path, String content) throws IOException {
    File iof = new File(path);
    FileWriter w = new FileWriter(iof);
    w.write(content);
    w.close();
  }

  protected void setDocumentTextFor(VirtualFile f, byte[] bytes) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    String t = new String(bytes);
    d.setText(t);
  }

  protected byte[] vcsContentOf(VirtualFile f) {
    return getVcs().getEntry(f.getPath()).getContent().getBytes();
  }

  protected boolean vcsHasEntryFor(VirtualFile f) {
    return vcsHasEntry(f.getPath());
  }

  protected boolean vcsHasEntry(String path) {
    return getVcs().hasEntry(path);
  }

  protected ILocalVcs getVcs() {
    return LocalVcsComponent.getLocalVcsFor(getProject());
  }

  protected LocalVcsComponent getVcsComponent() {
    return (LocalVcsComponent)LocalVcsComponent.getInstance(getProject());
  }

  protected VirtualFile addContentRoot() {
    return addContentRoot(myModule);
  }

  private VirtualFile addContentRoot(Module m) {
    return addContentRootWithFile(null, m);
  }

  protected VirtualFile addContentRootWithFile(String fileName, Module module) {
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
