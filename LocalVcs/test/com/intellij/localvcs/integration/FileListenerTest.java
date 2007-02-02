package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.TestLocalVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.junit.Before;
import org.junit.Test;

public class FileListenerTest extends MockedLocalFileSystemTestCase {
  LocalVcs vcs;
  FileListener l;

  @Before
  public void setUp() {
    vcs = new TestLocalVcs();
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

  private void fireRename(VirtualFile f, String newName) {
    l.beforePropertyChange(new VirtualFilePropertyEvent(null, f, VirtualFile.PROP_NAME, null, newName));
  }

  private class MyFileFilter extends FileFilter {
    public MyFileFilter() {
      super(null, null);
    }

    @Override
    public boolean isUnderContentRoot(VirtualFile f) {
      return true;
    }

    @Override
    public boolean isAllowed(VirtualFile f) {
      return f.getName().equals("allowed");
    }
  }
}
