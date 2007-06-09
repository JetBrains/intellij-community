package com.intellij.localvcsperf;

import com.intellij.idea.Bombed;
import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.CacheUpdaterHelper;
import com.intellij.localvcs.integration.TestIdeaGateway;
import com.intellij.localvcs.integration.TestVirtualFile;
import com.intellij.localvcs.integration.Updater;
import com.intellij.localvcs.utils.RunnableAdapter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

@Bombed(month = Calendar.AUGUST, day = 31, user = "anton")
public class BasicsTest extends LocalVcsPerformanceTestCase {
  @Test
  public void testBuildingTree() {
    assertExecutionTime(10000, new RunnableAdapter() {
      public void doRun() {
        buildVcsTree();
      }
    });
  }

  @Test
  public void testSaving() {
    buildVcsTree();

    assertExecutionTime(600, new RunnableAdapter() {
      public void doRun() {
        vcs.save();
      }
    });
  }

  @Test
  public void testLoading() {
    buildVcsTree();
    vcs.save();

    assertExecutionTime(750, new RunnableAdapter() {
      public void doRun() {
        initVcs();
      }
    });
  }

  @Test
  public void testCopying() {
    buildVcsTree();

    assertExecutionTime(80, new RunnableAdapter() {
      public void doRun() {
        vcs.getEntry("root").copy();
      }
    });
  }

  @Test
  public void testSearchingEntries() {
    buildVcsTree();

    assertExecutionTime(200, new RunnableAdapter() {
      public void doRun() {
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
    measureUpdateTime(9500, VCS_ENTRIES_TIMESTAMP + 1);
  }

  private void measureUpdateTime(int expected, long timestamp) throws IOException {
    final LocalFileSystem fs = createMock(LocalFileSystem.class);
    expect(fs.physicalContentsToByteArray((VirtualFile)anyObject())).andStubReturn(new byte[0]);

    final VirtualFile root = buildVFSTree(timestamp);
    assertExecutionTime(expected, new RunnableAdapter() {
      public void doRun() {
        updateFrom(root);
      }
    });
  }

  @Test
  public void testPurging() {
    setCurrentTimestamp(10);
    buildVcsTree();
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 1);

    assertExecutionTime(500, new RunnableAdapter() {
      public void doRun() {
        vcs.purgeObsolete(0);
      }
    });
  }

  @Test
  public void testBuildingRevisionsList() {
    buildVcsTree();
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 1);

    assertExecutionTime(800, new RunnableAdapter() {
      public void doRun() {
        vcs.getRevisionsFor("root");
      }
    });
  }

  @Test
  public void testBuildingDifference() {
    buildVcsTree();
    final List<Revision> revisions = vcs.getRevisionsFor("root");

    assertExecutionTime(1200, new RunnableAdapter() {
      public void doRun() {
        revisions.get(0).getDifferenceWith(revisions.get(0));
      }
    });
  }

  @Test
  public void testCalculatingChangeChains() {
    vcs.createDirectory("root");
    final Change c = vcs.getLastChange();
    createChildren("root", 5);

    assertExecutionTime(180, new RunnableAdapter() {
      public void doRun() throws Exception {
        vcs.getChain(c);
      }
    });
  }

  private void updateFromTreeWithTimestamp(long timestamp) {
    TestVirtualFile root = buildVFSTree(timestamp);
    updateFrom(root);
  }

  private void updateFrom(VirtualFile root) {
    TestIdeaGateway gw = new TestIdeaGateway();
    gw.setContentRoots(root);
    Updater u = new Updater(vcs, gw);
    CacheUpdaterHelper.performUpdate(u);
  }
}
