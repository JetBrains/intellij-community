package com.intellij.historyIntegrTests;


import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.vfs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FileListeningTest extends IntegrationTestCase {
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
    assertFalse(hasVcsEntry(f));
  }

  public void testIgnoringFilteredDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);
    assertFalse(hasVcsEntry(f));
  }

  public void testIgnoringExcludedDirectoriesAfterItsRecreation() throws Exception {
    VirtualFile dir = root.createChildDirectory(null, "dir");
    assertTrue(hasVcsEntry(dir));

    addExcludedDir(dir);
    assertFalse(hasVcsEntry(dir));

    int revCount = getVcsRevisionsFor(root).size();

    dir.delete(null);
    assertEquals(revCount, getVcsRevisionsFor(root).size());

    dir = root.createChildDirectory(null, "dir");

    // bug: excluded dir was created during fileCrated event
    // end removed bu rootsChanges event right away
    assertEquals(revCount, getVcsRevisionsFor(root).size());
    assertFalse(hasVcsEntry(dir));
  }

  public void ignoreTestChangingContentOfDeletedFileDoesNotThrowException() throws Exception {
    // todo try to write reliable test for exception handling during file events and update
    final VirtualFile f = root.createChildData(null, "f.java");

    VirtualFileListener l = new VirtualFileAdapter() {
      @Override
      public void beforeContentsChange(VirtualFileEvent e) {
        new File(e.getFile().getPath()).delete();
      }
    };

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent(new byte[]{1});
      }
    });

    assertFalse(getVcs().getEntry(f.getPath()).getContent().isAvailable());
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{1});
    assertEquals(1, getVcsContentOf(f)[0]);

    f.setBinaryContent(new byte[]{2});
    assertEquals(2, getVcsContentOf(f)[0]);
  }

  public void testChangingFileContentOnlyAfterContentChangedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent("before".getBytes());

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.setBinaryContent("after".getBytes());
      }
    });

    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.rename(null, "file2.java");

    assertFalse(hasVcsEntry(Paths.renamed(f.getPath(), "file.java")));
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "old.java");
    final boolean[] log = new boolean[4];
    final String oldPath = Paths.appended(root.getPath(), "old.java");
    final String newPath = Paths.appended(root.getPath(), "new.java");

    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = hasVcsEntry(oldPath);
        log[1] = hasVcsEntry(newPath);
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[2] = hasVcsEntry(oldPath);
        log[3] = hasVcsEntry(newPath);
      }
    };

    addFileListenerDuring(l, new RunnableAdapter() {
      @Override
      public void doRun() throws IOException {
        f.rename(null, "new.java");
      }
    });

    assertEquals(true, log[0]);
    assertEquals(false, log[1]);
    assertEquals(false, log[2]);
    assertEquals(true, log[3]);
  }

  public void testRenamingFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(hasVcsEntry(f));
    f.rename(null, "file.java");
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    f.rename(null, "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    assertTrue(hasVcsEntry(notFiltered));
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "not_filtered");

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertTrue(hasVcsEntry(notFiltered));
    f.rename(null, FILTERED_DIR_NAME);

    assertFalse(hasVcsEntry(notFiltered));
    assertFalse(hasVcsEntry(filtered));
  }
  
  public void testDeletion() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "f.java");

    String path = f.getPath();
    assertTrue(hasVcsEntry(path));

    f.delete(null);
    assertFalse(hasVcsEntry(path));
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    VirtualFile f = root.createChildDirectory(null, FILTERED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), FILTERED_DIR_NAME);

    assertFalse(hasVcsEntry(filtered));
    f.delete(null);

    assertFalse(hasVcsEntry(filtered));
  }

  public void testDeletingBigFiles() throws Exception {
    File tempDir = createTempDir("temp");
    File tempFile = new File(tempDir, "bigFile.java");
    OutputStream s = new FileOutputStream(tempFile);
    s.write(new byte[ContentFactory.MAX_CONTENT_LENGTH + 1]);
    s.close();

    VirtualFile f = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
    assertNotNull(f);

    f.move(null, root);
    assertTrue(hasVcsEntry(f));

    f.delete(null);
    assertFalse(hasVcsEntry(f));
  }
}