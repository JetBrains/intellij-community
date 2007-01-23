package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestStorage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import org.junit.Test;
import org.junit.Before;

public class FileListenerTest extends MockedLocalFileSystemTestCase {
  LocalVcs vcs;
  FileListener l;

  @Before
  public void setUp() {
    vcs = new LocalVcs(new TestStorage());
    l = new FileListener(vcs, new MyFileFilter(), fileSystem);
  }

  @Test
  public void testRenamingFileToFilteredOne() {
    vcs.createFile("allowed", null, null);
    vcs.apply();

    VirtualFile f = new TestVirtualFile("allowed", null, null);
    fireRename(f, "filtered");

    assertFalse(vcs.hasEntry("allowed"));
    assertFalse(vcs.hasEntry("filtered"));
  }

  @Test
  public void testRenamingFileFromFilteredOne() {
    VirtualFile f = new TestVirtualFile("filtered", "content", 123L);
    fireRename(f, "allowed");

    Entry e = vcs.findEntry("allowed");
    assertNotNull(e);
    assertEquals(c("content"), e.getContent());
    assertEquals(123L, e.getTimestamp());
  }

  @Test
  public void testRenamingFileFromFilteredToFilteredOne() {
    VirtualFile f = new TestVirtualFile("filtered1", null, null);
    fireRename(f, "filtered2");

    assertFalse(vcs.hasEntry("filtered1"));
    assertFalse(vcs.hasEntry("filtered2"));
  }

  @Test
  public void testCreationAndDeletionOfFilteredBigFile() {
    VirtualFile f = new TestVirtualFile("allowed", null, null, FileFilter.MAX_FILE_SIZE + 1);

    l.fileCreated(new VirtualFileEvent(null, f, null, null));
    assertFalse(vcs.hasEntry("allowed"));

    // when we catch the beforeFileDeletion event, the file might be already
    // removed from disk, so it will return 0 as it's lenght
    f = new TestVirtualFile("allowed", null, null, 0L);

    l.beforeFileDeletion(new VirtualFileEvent(null, f, null, null));
    assertFalse(vcs.hasEntry("allowed"));
  }

  private void fireRename(VirtualFile f, String newName) {
    l.beforePropertyChange(new VirtualFilePropertyEvent(null, f, VirtualFile.PROP_NAME, null, newName));
  }

  private class MyFileFilter extends FileFilter {
    public MyFileFilter() {
      super(null, null);
    }

    @Override
    protected boolean isFileTypeAllowed(VirtualFile f) {
      return f.getName().equals("allowed");
    }

    @Override
    public boolean isUnderContentRoot(VirtualFile f) {
      return true;
    }
  }
}
