package com.intellij.localvcsperf;

import com.intellij.localvcs.Label;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.LocalVcsStorage;
import com.intellij.localvcs.integration.CacheUpdaterHelper;
import com.intellij.localvcs.integration.TestFileFilter;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.localvcs.integration.Updater;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

// todo it's time to refactor 8)))
public class BasicsTest extends PerformanceTest {
  private LocalVcs vcs;
  private LocalVcsStorage s;
  private static final long VCS_ENTRIES_TIMESTAMP = 1L;

  @Before
  public void initVcs() {
    closeStorage();
    s = new LocalVcsStorage(tempDir);
    vcs = new LocalVcs(s);
  }

  @After
  public void closeStorage() {
    if (s != null) s.close();
  }

  @Test
  public void testBuildingTree() {
    assertExecutionTime(35000, new Task() {
      public void execute() {
        buildVcsTree();
      }
    });
  }

  @Test
  public void testSaving() {
    buildVcsTree();

    assertExecutionTime(1000, new Task() {
      public void execute() {
        vcs.save();
      }
    });
  }

  @Test
  public void testLoading() {
    buildVcsTree();
    vcs.save();

    assertExecutionTime(1200, new Task() {
      public void execute() {
        initVcs();
      }
    });
  }

  @Test
  public void testCopying() {
    buildVcsTree();

    assertExecutionTime(150, new Task() {
      public void execute() {
        vcs.getEntry("root").copy();
      }
    });
  }

  @Test
  public void testSearchingEntries() {
    buildVcsTree();

    assertExecutionTime(200, new Task() {
      public void execute() {
        for (int i = 0; i < 10000; i++) {
          vcs.getEntry("root/dir" + rand(10) + "/dir" + rand(10) + "/dir" + rand(10) + "/file" + rand(10));
        }
      }
    });
  }

  @Test
  public void testUpdatingWithCleanVcs() throws Exception {
    measureUpdateTime(9000, 1L);
  }

  @Test
  public void testUpdatingWithAllFilesUpToDate() throws Exception {
    buildVcsTree();
    measureUpdateTime(1200, VCS_ENTRIES_TIMESTAMP);
  }

  @Test
  public void testUpdatingWithAllFilesOutdated() throws Exception {
    buildVcsTree();
    measureUpdateTime(5500, VCS_ENTRIES_TIMESTAMP + 1);
  }

  private void measureUpdateTime(int expected, long timestamp) throws IOException {
    final LocalFileSystem fs = createMock(LocalFileSystem.class);
    expect(fs.physicalContentsToByteArray((VirtualFile)anyObject())).andStubReturn(new byte[0]);

    final VirtualFile root = buildVFSTree(timestamp);
    assertExecutionTime(expected, new Task() {
      public void execute() {
        Updater u = new Updater(vcs, new TestFileFilter(), root);
        CacheUpdaterHelper.performUpdate(u);
      }
    });
  }

  private void updateFromTreeWithTimestamp(long timestamp) {
    TestVirtualFile root = buildVFSTree(timestamp);
    Updater u = new Updater(vcs, new TestFileFilter(), root);
    CacheUpdaterHelper.performUpdate(u);
  }

  @Test
  public void testBuildingDifference() {
    buildVcsTree();
    final List<Label> labels = vcs.getLabelsFor("root");

    assertExecutionTime(1200, new Task() {
      public void execute() {
        labels.get(0).getDifferenceWith(labels.get(0));
      }
    });
  }

  @Test
  public void testPurging() {
    setCurrentTimestamp(10);
    buildVcsTree();
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 1);

    assertExecutionTime(1, new Task() {
      public void execute() {
        vcs.purgeUpTo(20);
      }
    });
  }

  private void buildVcsTree() {
    vcs.beginChangeSet();
    vcs.createDirectory("root", null);
    createChildren("root", 5);
    vcs.endChangeSet(null);
  }

  private void createChildren(String parent, Integer countdown) {
    if (countdown == 0) return;

    for (Integer i = 0; i < 10; i++) {
      String filePath = parent + "/file" + i;
      vcs.createFile(filePath, b(""), VCS_ENTRIES_TIMESTAMP);

      String dirPath = parent + "/dir" + i;
      vcs.createDirectory(dirPath, null);
      createChildren(dirPath, countdown - 1);
    }
  }

  private TestVirtualFile buildVFSTree(long timestamp) {
    TestVirtualFile root = new TestVirtualFile("root", null);
    createVFSChildren(root, timestamp, 5);
    return root;
  }

  private void createVFSChildren(TestVirtualFile parent, long timestamp, int countdown) {
    if (countdown == 0) return;

    for (int i = 0; i < 10; i++) {
      parent.addChild(new TestVirtualFile("file" + i, null, timestamp));

      TestVirtualFile dir = new TestVirtualFile("dir" + i, null);
      parent.addChild(dir);
      createVFSChildren(dir, timestamp, countdown - 1);
    }
  }
}
