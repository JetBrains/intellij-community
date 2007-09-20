package com.intellij.historyIntegrTests;

import com.intellij.history.Clock;
import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryComponent;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public abstract class IntegrationTestCase extends IdeaTestCase {
  private Locale myDefaultLocale;

  protected String FILTERED_DIR_NAME = "CVS";
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

  protected File getIprFile() throws IOException {
    return new File(createTempDirectory(), "test.ipr");
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

  protected void addFileListenerDuring(VirtualFileListener l, Runnable r) throws Exception {
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      r.run();
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
    f.setLastModified(f.lastModified() + 2000);
  }

  protected void setDocumentTextFor(VirtualFile f, byte[] bytes) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    String t = new String(bytes);
    d.setText(t);
  }

  protected LocalHistoryComponent getVcsComponent() {
    return LocalHistoryComponent.getComponentInstance(myProject);
  }

  protected LocalVcs getVcs() {
    return LocalHistoryComponent.getLocalVcsImplFor(myProject);
  }

  protected boolean hasVcsEntry(VirtualFile f) {
    return hasVcsEntry(f.getPath());
  }

  protected boolean hasVcsEntry(String path) {
    return getVcs().hasEntry(path);
  }

  protected Entry getVcsEntry(VirtualFile f) {
    return getVcs().getEntry(f.getPath());
  }

  protected byte[] getVcsContentOf(VirtualFile f) {
    return getVcsEntry(f).getContent().getBytes();
  }

  protected List<Revision> getVcsRevisionsFor(VirtualFile f) {
    return getVcs().getRevisionsFor(f.getPath());
  }

  protected VirtualFile addContentRoot() {
    return addContentRootWithFiles();
  }

  protected VirtualFile addContentRootWithFiles(String... fileNames) {
    try {
      File dir = createTempDirectory();

      for (String n : fileNames) {
        new File(dir, n).createNewFile();
      }

      VirtualFile root = getFS().refreshAndFindFileByIoFile(dir);
      PsiTestUtil.addContentRoot(myModule, root);
      return root;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void addExcludedDir(VirtualFile dir) {
    ModuleRootManager rm = ModuleRootManager.getInstance(myModule);
    ModifiableRootModel m = rm.getModifiableModel();
    for (ContentEntry e : m.getContentEntries()) {
      if (e.getFile() != root) continue;
      e.addExcludeFolder(dir);
    }
    m.commit();
  }

  protected LocalFileSystem getFS() {
    return LocalFileSystem.getInstance();
  }
}
