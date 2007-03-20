package com.intellij.localvcsintegr;

import com.intellij.localvcs.Clock;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.integration.LocalVcsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

public abstract class IntegrationTestCase extends IdeaTestCase {
  String EXCLUDED_DIR_NAME = "CVS";
  VirtualFile root;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Clock.useRealClock();
    runWriteAction(new Runnable() {
      public void run() {
        root = addContentRoot();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  protected void runWriteAction(Runnable r) {
    ApplicationManager.getApplication().runWriteAction(r);
  }

  protected void addFileListenerDuring(VirtualFileListener l, Callable task) throws Exception {
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      task.call();
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }
  }

  protected class ContentChangesListener extends VirtualFileAdapter {
    private VirtualFile myFile;
    private String[] myContents = new String[2];

    public ContentChangesListener(VirtualFile f) {
      myFile = f;
    }

    public String getContentBefore() {
      return myContents[0];
    }

    public String getContentAfter() {
      return myContents[1];
    }

    @Override
    public void beforeContentsChange(VirtualFileEvent e) {
      logContent(e, 0);
    }

    @Override
    public void contentsChanged(VirtualFileEvent e) {
      logContent(e, 1);
    }

    private void logContent(VirtualFileEvent e, int i) {
      try {
        if (!e.getFile().equals(myFile)) return;
        myContents[i] = new String(myFile.contentsToByteArray());
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

  }

  protected String createFileExternally(String name) throws Exception {
    File f = new File(root.getPath(), name);
    f.createNewFile();
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected String createDirectoryExternally(String name) throws Exception {
    File f = new File(root.getPath(), name);
    f.mkdirs();
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

  protected byte[] getVcsContentOf(VirtualFile f) {
    return getVcs().getEntry(f.getPath()).getContent().getBytes();
  }

  protected boolean hasVcsEntry(VirtualFile f) {
    return hasVcsEntry(f.getPath());
  }

  protected boolean hasVcsEntry(String path) {
    return getVcs().hasEntry(path);
  }

  protected ILocalVcs getVcs() {
    return LocalVcsComponent.getLocalVcsFor(myProject);
  }

  protected LocalVcsComponent getVcsComponent() {
    return (LocalVcsComponent)LocalVcsComponent.getInstance(myProject);
  }

  protected VirtualFile addContentRoot() {
    return addContentRoot(myModule);
  }

  protected VirtualFile addContentRoot(Module m) {
    return addContentRootWithFiles(m);
  }

  protected VirtualFile addContentRootWithFiles(Module module, String... fileNames) {
    try {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      File dir = createTempDirectory();

      for (String n : fileNames) {
        new File(dir, n).createNewFile();
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
