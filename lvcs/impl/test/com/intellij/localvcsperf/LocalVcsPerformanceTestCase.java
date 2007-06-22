package com.intellij.localvcsperf;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.TestLocalVcs;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.integration.TestVirtualFile;
import org.junit.After;
import org.junit.Before;

public class LocalVcsPerformanceTestCase extends PerformanceTestCase {
  protected LocalVcs vcs;
  private Storage storage;
  protected static final long VCS_ENTRIES_TIMESTAMP = 1L;

  @Before
  public void initVcs() {
    closeStorage();
    storage = new Storage(tempDir);
    vcs = new TestLocalVcs(storage);
  }

  @After
  public void closeStorage() {
    if (storage != null) {
      storage.close();
      storage = null;
    }
  }

  protected void buildVcsTree() {
    vcs.beginChangeSet();
    vcs.createDirectory("root");
    createChildren("root", 5);
    vcs.endChangeSet(null);
  }

  protected void createChildren(String parent, int depth) {
    if (depth == 0) return;

    for (int i = 0; i < 10; i++) {
      String filePath = parent + "/file" + i;
      long timestamp = VCS_ENTRIES_TIMESTAMP;
      vcs.createFile(filePath, cf(String.valueOf(timestamp + i)), timestamp);

      String dirPath = parent + "/dir" + i;
      vcs.createDirectory(dirPath);
      createChildren(dirPath, depth - 1);
    }
  }

  protected TestVirtualFile buildVFSTree(long timestamp) {
    TestVirtualFile root = new TestVirtualFile("root");
    createVFSChildren(root, timestamp, 5);
    return root;
  }

  private void createVFSChildren(TestVirtualFile parent, long timestamp, int countdown) {
    if (countdown == 0) return;

    for (int i = 0; i < 10; i++) {
      parent.addChild(new TestVirtualFile("file" + i, String.valueOf(timestamp + i), timestamp));

      TestVirtualFile dir = new TestVirtualFile("dir" + i);
      parent.addChild(dir);
      createVFSChildren(dir, timestamp, countdown - 1);
    }
  }
}
