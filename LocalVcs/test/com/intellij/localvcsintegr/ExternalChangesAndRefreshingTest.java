package com.intellij.localvcsintegr;


import com.intellij.localvcs.Paths;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

public class ExternalChangesAndRefreshingTest extends IntegrationTestCase {
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

  public void testRefreshInsideCommand() {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        forceRefreshVFS(false);
      }
    }, "", null);
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

    // todo unrelable test because content recorded before LvcsFileListener does its job
    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  private void performAllPendingJobs() {
    //forceRefreshVFS(false);
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

  public void ignoreTestCreationOfExcludedDirectoryDuringRefresh() throws Exception {
    // todo does not work due to FileListener order. FileIndex gets event later than Lvcs.

    VirtualFile dir = root.createChildDirectory(null, "EXCLUDED");
    String p = dir.getPath();

    assertTrue(vcsHasEntry(p));

    ModifiableRootModel m = ModuleRootManager.getInstance(myModule).getModifiableModel();
    m.getContentEntries()[0].addExcludeFolder(dir);
    m.commit();

    assertFalse(vcsHasEntry(p));

    dir.delete(null);

    createDirectoryExternally("EXCLUDED");
    forceRefreshVFS(false);

    assertFalse(vcsHasEntry(p));
  }

  public void testDeletionOfFilteredDirectoryExternallyDoesNotThrowExceptionDuringRefresh() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);
    String path = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);

    assertFalse(vcsHasEntry(path));

    new File(path).delete();
    forceRefreshVFS(false);

    assertFalse(vcsHasEntry(path));
  }
}