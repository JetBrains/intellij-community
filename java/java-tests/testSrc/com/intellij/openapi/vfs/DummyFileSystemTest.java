package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.testFramework.IdeaTestCase;

public class DummyFileSystemTest extends IdeaTestCase {
  DummyFileSystem fs;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    fs = new DummyFileSystem();
  }

  public void testDeletionEvents() throws Exception {
    final VirtualFile root = fs.createRoot("root");
    VirtualFile f = new WriteAction<VirtualFile>() {
      @Override
      protected void run(Result<VirtualFile> result) throws Throwable {
        VirtualFile res = root.createChildData(null, "f");
        result.setResult(res);
      }
    }.execute().getResultObject();

    final VirtualFileEvent[] events = new VirtualFileEvent[2];
    fs.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileDeleted(VirtualFileEvent e) {
        events[0] = e;
      }

      @Override
      public void beforeFileDeletion(VirtualFileEvent e) {
        events[1] = e;
      }
    });

    f.delete(null);

    for (int i = 0; i < 2; i++) {
      assertNotNull(events[i]);
      assertEquals(f, events[i].getFile());
      assertEquals("f", events[i].getFileName());
      assertEquals(root, events[i].getParent());
    }
  }
}
