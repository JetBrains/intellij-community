package com.intellij.historyPerfTests;

import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.CacheUpdaterHelper;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.integration.TestVirtualFile;
import com.intellij.history.integration.Updater;
import com.intellij.history.utils.RunnableAdapter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

@Ignore
public class BasicsTest extends LocalVcsPerformanceTestCase {
  @Test
  public void testBuildingTree() {
    assertExecutionTime(4000, new RunnableAdapter() {
      public void doRun() {
        buildVcsTree();
      }
    });
  }

  @Test
  public void testSaving() {
    buildVcsTree();

    assertExecutionTime(230, new RunnableAdapter() {
      public void doRun() {
        vcs.save();
      }
    });
  }

  @Test
  public void testLoading() {
    buildVcsTree();
    vcs.save();

    assertExecutionTime(320, new RunnableAdapter() {
      public void doRun() {
        initVcs();
      }
    });
  }

  @Test
  public void testCopying() {
    buildVcsTree();

    assertExecutionTime(30, new RunnableAdapter() {
      public void doRun() {
        vcs.getEntry("root").copy();
      }
    });
  }

  @Test
  public void testSearchingEntries() {
    buildVcsTree();

    assertExecutionTime(60, new RunnableAdapter() {
      public void doRun() {
        for (int i = 0; i < 10000; i++) {
          vcs.getEntry(createRandomPath());
        }
      }
    });
  }

  @Test
  public void testUpdatingWithCleanVcs() throws Exception {
    measureUpdateTime(5700, 1L);
  }

  @Test
  public void testUpdatingWithAllFilesUpToDate() throws Exception {
    buildVcsTree();
    measureUpdateTime(450, VCS_ENTRIES_TIMESTAMP);
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

    assertExecutionTime(100, new RunnableAdapter() {
      public void doRun() {
        vcs.purgeObsoleteAndSave(0);
      }
    });
  }

  @Test
  public void testBuildingRevisionsList() {
    buildVcsTree();
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 1);

    assertExecutionTime(1100, new RunnableAdapter() {
      public void doRun() {
        vcs.getRevisionsFor("root");
      }
    });
  }

  @Test
  public void testBuildingDifference() {
    buildVcsTree();
    final List<Revision> revisions = vcs.getRevisionsFor("root");

    assertExecutionTime(900, new RunnableAdapter() {
      public void doRun() {
        revisions.get(0).getDifferencesWith(revisions.get(0));
      }
    });
  }

  @Test
  public void testCalculatingChangeChains() {
    vcs.createDirectory("root");
    final Change c = vcs.getLastChange();
    createChildren("root", 5);

    assertExecutionTime(50, new RunnableAdapter() {
      public void doRun() throws Exception {
        vcs.getChain(c);
      }
    });
  }

  @Test
  public void testSearchingForByteContent() {
    buildVcsTree();
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 1);
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 2);
    updateFromTreeWithTimestamp(VCS_ENTRIES_TIMESTAMP + 3);

    assertExecutionTime(250, new RunnableAdapter() {
      public void doRun() throws Exception {
        vcs.getByteContent(createRandomPath(), new FileRevisionTimestampComparator() {
          public boolean isSuitable(long revisionTimestamp) {
            return false;
          }
        });
      }
    });
  }

  private String createRandomPath() {
    return "root/dir" + rand(10) + "/dir" + rand(10) + "/dir" + rand(10) + "/file" + rand(10);
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
