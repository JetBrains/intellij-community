package com.intellij.localvcsintegr;


import com.intellij.localvcs.*;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalVcsAction;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Callable;

public class LocalVcsComponentTest extends IntegrationalTestCase {
  public void testComponentInitialization() {
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

  public void testCreatingFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "dir");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(vcsHasEntryFor(f));
  }

  public void testIgnoringDirectories() throws Exception {
    assertTrue(FileTypeManager.getInstance().isFileIgnored("CVS"));

    VirtualFile f = root.createChildDirectory(null, "CVS");
    assertFalse(vcsHasEntryFor(f));
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{1});
    assertEquals(1, vcsContentOf(f)[0]);

    f.setBinaryContent(new byte[]{2});
    assertEquals(2, vcsContentOf(f)[0]);
  }

  public void testChangingFileContentOnlyAfterContentChangedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent("before".getBytes());

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        f.setBinaryContent("after".getBytes());
        return null;
      }
    });

    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.rename(null, "file2.java");

    assertFalse(vcsHasEntry(Paths.renamed(f.getPath(), "file.java")));
    assertTrue(vcsHasEntryFor(f));
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "old.java");
    final boolean[] log = new boolean[4];
    final String oldPath = Paths.appended(root.getPath(), "old.java");
    final String newPath = Paths.appended(root.getPath(), "new.java");

    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = vcsHasEntry(oldPath);
        log[1] = vcsHasEntry(newPath);
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[2] = vcsHasEntry(oldPath);
        log[3] = vcsHasEntry(newPath);
      }
    };

    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        f.rename(null, "new.java");
        return null;
      }
    });

    assertEquals(true, log[0]);
    assertEquals(false, log[1]);
    assertEquals(false, log[2]);
    assertEquals(true, log[3]);
  }

  public void testRenamingFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(vcsHasEntryFor(f));
    f.rename(null, "file.java");
    assertTrue(vcsHasEntryFor(f));
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    assertTrue(FileTypeManager.getInstance().isFileIgnored("CVS"));
    VirtualFile f = root.createChildDirectory(null, "CVS");

    String filtered = Paths.appended(root.getPath(), "CVS");
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertFalse(vcsHasEntry(filtered));
    f.rename(null, "not_filtered");

    assertFalse(vcsHasEntry(filtered));
    assertTrue(vcsHasEntry(notFiltered));
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "not_filtered");

    String filtered = Paths.appended(root.getPath(), "CVS");
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertTrue(vcsHasEntry(notFiltered));
    f.rename(null, "CVS");

    assertFalse(vcsHasEntry(notFiltered));
    assertFalse(vcsHasEntry(filtered));
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "CVS");

    String filtered = Paths.appended(root.getPath(), "CVS");

    assertFalse(vcsHasEntry(filtered));
    f.delete(null);

    assertFalse(vcsHasEntry(filtered));
  }

  public void testDeletingBigFiles() throws Exception {
    File tempDir = createTempDirectory();
    File tempFile = new File(tempDir, "bigFile.java");
    OutputStream s = new FileOutputStream(tempFile);
    s.write(new byte[LongContent.MAX_LENGTH + 1]);
    s.close();

    VirtualFile f = LocalFileSystem.getInstance().findFileByIoFile(tempFile);

    f.move(null, root);
    assertTrue(vcsHasEntryFor(f));

    f.delete(null);
    assertFalse(vcsHasEntryFor(f));
  }

  public void testFileContentBeforeFileDeletedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent("content".getBytes());

    final String[] contents = new String[2];

    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void beforeFileDeletion(VirtualFileEvent e) {
        logContent(0);
      }

      @Override
      public void fileDeleted(VirtualFileEvent e) {
        logContent(1);
      }

      private void logContent(int index) {
        try {
          contents[index] = new String(f.contentsToByteArray());
        }
        catch (IOException ex) {
          contents[index] = "exception";
        }
      }
    };

    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        f.delete(null);
        return null;
      }
    });

    assertEquals("content", contents[0]);
    assertEquals("exception", contents[1]);
  }

  public void testRefreshingSynchronously() throws Exception {
    doTestRefreshing(false);
  }

  public void testRefreshingAsynchronously() throws Exception {
    doTestRefreshing(true);
  }

  private void doTestRefreshing(boolean async) throws Exception {
    String path1 = createFileExternally("f1.java");
    String path2 = createFileExternally("f2.java");

    assertFalse(vcsHasEntry(path1));
    assertFalse(vcsHasEntry(path2));

    forceRefreshVFS(async);

    assertTrue(vcsHasEntry(path1));
    assertTrue(vcsHasEntry(path2));

    assertEquals(2, getVcs().getLabelsFor(root.getPath()).size());
  }

  private void forceRefreshVFS(boolean async) {
    LocalFileSystemImpl fs = (LocalFileSystemImpl)LocalFileSystem.getInstance();

    fs.startAsynchronousTasksMonitoring();

    VirtualFileManager.getInstance().refresh(async);

    fs.waitForAsynchronousTasksCompletion();
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testContentOfFileChangedDuringRefresh() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent("before".getBytes());

    performAllPendingJobs();

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        changeContentExternally(f.getPath(), "after");
        forceRefreshVFS(false);
        return null;
      }
    });

    // todo unrelyable test because content recorder before LvcsFileListener do it's job
    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  private void performAllPendingJobs() {
    forceRefreshVFS(false);
  }

  public void testFileCreationDuringRefresh() throws Exception {
    final String path = createFileExternally("f.java");
    changeContentExternally(path, "content");

    final String[] content = new String[1];
    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent e) {
        try {
          if (!e.getFile().getPath().equals(path)) return;
          content[0] = new String(e.getFile().contentsToByteArray());
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    };

    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        forceRefreshVFS(false);
        return null;
      }
    });
    assertEquals("content", content[0]);
  }

  public void testDeletionOfFilteredDirectoryExternallyDoesNotThrowExceptionDuringRefresh() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "CVS");
    String path = Paths.appended(root.getPath(), "CVS");

    assertFalse(vcsHasEntry(path));

    new File(path).delete();
    forceRefreshVFS(false);

    assertFalse(vcsHasEntry(path));
  }

  private void addFileListenerDuring(VirtualFileListener l, Callable task) throws Exception {
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      task.call();
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }
  }

  class ContentChangesListener extends VirtualFileAdapter {
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

  public void testContentForBigFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    byte[] c = new byte[LongContent.MAX_LENGTH + 1];
    c[0] = 7;
    f.setBinaryContent(c);

    assertEquals(7, f.contentsToByteArray()[0]);
  }

  public void testActions() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{0});
    setDocumentTextFor(f, new byte[]{1});

    assertEquals(0, vcsContentOf(f)[0]);

    LocalVcsAction a = getVcsComponent().startAction("label");
    assertEquals(1, vcsContentOf(f)[0]);

    setDocumentTextFor(f, new byte[]{2});

    a.finish();
    assertEquals(2, vcsContentOf(f)[0]);

    List<Label> l = getVcs().getLabelsFor(f.getPath());
    assertEquals("label", l.get(0).getName());
    assertNull(l.get(1).getName());
  }

  public void testRevertion() throws Exception {
    VirtualFile f = root.createChildData(null, "f.java");
    f.setBinaryContent(new byte[]{1}, -1, 123);
    f.setBinaryContent(new byte[]{2}, -1, 456);

    FileHistoryDialogModel d = new FileHistoryDialogModel(f, getVcs(), new IdeaGateway(myProject));
    assertEquals(3, d.getLabels().size());
    d.selectLabels(0, 1);
    d.revert();

    assertEquals(1, f.contentsToByteArray()[0]);
    assertEquals(1, vcsContentOf(f)[0]);
  }
}
