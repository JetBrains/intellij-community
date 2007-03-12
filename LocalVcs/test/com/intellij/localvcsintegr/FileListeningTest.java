package com.intellij.localvcsintegr;


import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LongContent;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.vfs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;

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
    assertFalse(vcsHasEntryFor(f));
  }

  public void testIgnoringDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);
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
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertFalse(vcsHasEntry(filtered));
    f.rename(null, "not_filtered");

    assertFalse(vcsHasEntry(filtered));
    assertTrue(vcsHasEntry(notFiltered));
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "not_filtered");

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertTrue(vcsHasEntry(notFiltered));
    f.rename(null, EXCLUDED_DIR_NAME);

    assertFalse(vcsHasEntry(notFiltered));
    assertFalse(vcsHasEntry(filtered));
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);

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
}