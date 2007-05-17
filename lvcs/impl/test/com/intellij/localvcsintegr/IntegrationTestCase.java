package com.intellij.localvcsintegr;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.Clock;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalHistoryComponent;
import com.intellij.localvcs.utils.RunnableAdapter;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

public abstract class IntegrationTestCase extends IdeaTestCase {
  private Locale myDefaultLocale;

  protected String EXCLUDED_DIR_NAME = "CVS";
  protected VirtualFile root;
  protected IdeaGateway gateway;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Clock.useRealClock();
    Paths.useSystemCaseSensitivity();

    myDefaultLocale = Locale.getDefault();
    Locale.setDefault(new Locale("ru", "RU"));

    gateway = new IdeaGateway(myProject);

    runWriteAction(new RunnableAdapter() {
      @Override
      public void doRun() throws Exception {
        setUpInWriteAction();
      }
    });
  }

  protected void setUpInWriteAction() throws Exception {
    root = addContentRoot();
  }

  @Override
  protected void tearDown() throws Exception {
    Locale.setDefault(myDefaultLocale);
    Clock.useRealClock();
    Paths.useSystemCaseSensitivity();
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

  protected void changeFileExternally(String path, String content) throws IOException {
    File f = new File(path);
    FileWriter w = new FileWriter(f);
    w.write(content);
    w.close();
    f.setLastModified(f.lastModified() + 100);
  }

  protected void setDocumentTextFor(VirtualFile f, byte[] bytes) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    String t = new String(bytes);
    d.setText(t);
  }

  protected LocalHistoryComponent getVcsComponent() {
    return (LocalHistoryComponent)LocalHistoryComponent.getInstance(myProject);
  }

  protected ILocalVcs getVcs() {
    return LocalHistoryComponent.getLocalVcsFor(myProject);
  }

  protected boolean hasVcsEntry(VirtualFile f) {
    return hasVcsEntry(f.getPath());
  }

  protected boolean hasVcsEntry(String path) {
    return getVcs().hasEntry(path);
  }

  protected byte[] getVcsContentOf(VirtualFile f) {
    return getVcs().getEntry(f.getPath()).getContent().getBytes();
  }

  protected List<Revision> getVcsRevisionsFor(VirtualFile f) {
    return getVcs().getRevisionsFor(f.getPath());
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
