package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.data.DataLoader;
import org.hanuna.gitalk.data.DataPack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class DataLoaderImpl implements DataLoader {
  private final Project myProject;
  private final CacheGet<Hash, CommitData> myCommitDataCache;
  @NotNull private final VcsLogProvider myLogProvider;
  private State state = State.UNINITIALIZED;
  private volatile DataPackImpl dataPack;

  public DataLoaderImpl(Project project, CacheGet<Hash, CommitData> commitDataCache, @NotNull VcsLogProvider logProvider) {
    myProject = project;
    myCommitDataCache = commitDataCache;
    myLogProvider = logProvider;
  }

  @Override
  public void readNextPart(@NotNull ProgressIndicator indicator, @NotNull FakeCommitsInfo fakeCommits, @NotNull VirtualFile root)
    throws VcsException {
    switch (state) {
      case ALL_LOG_READER:
        throw new IllegalStateException("data was read");
      case UNINITIALIZED:
        List<Ref> allRefs = new ArrayList<Ref>();
        allRefs.addAll(myLogProvider.readAllRefs(root));
        if (fakeCommits.resultRef != null) {
          allRefs.add(0, fakeCommits.resultRef);
          allRefs.remove(fakeCommits.subjectRef);
        }

        Set<Hash> visible = new HashSet<Hash>();
        for (Ref ref : allRefs) {
          if (ref.getType() != Ref.RefType.HEAD) {
            visible.add(ref.getCommitHash());
          }
        }

        System.out.println("=== readNextPart() called with " + fakeCommits.commits.size() + " fake commits");
        List<CommitParents> commitParentsList = new ArrayList<CommitParents>();
        List<CommitParents> commits = myLogProvider.readNextBlock(root);
        boolean inserted = false;
        for (CommitParents commit : commits) {
          if (!inserted && fakeCommits.base != null && commitParentsList.size() + fakeCommits.commits.size() >= fakeCommits.insertAbove) {
            commitParentsList.addAll(fakeCommits.commits);
            for (CommitParents fakeCommit : fakeCommits.commits) {
              //System.out.println("Visible from fake_" + fakeCommit.getCommitHash() + ": " + fakeCommit.getParentHashes());
              visible.addAll(fakeCommit.getParentHashes());
            }
            inserted = true;
          }
          if (visible.contains(commit.getCommitHash())) {
            commitParentsList.add(commit);
            visible.addAll(commit.getParentHashes());

          }
          else {
            System.out.println("Hidden: " + commit.getCommitHash());
          }
        }
        state = State.PART_LOG_READER;

        dataPack = DataPackImpl.buildDataPack(commitParentsList, allRefs, indicator, myProject, myCommitDataCache, myLogProvider, root);
        break;
      case PART_LOG_READER:
        List<CommitParents> nextPart = myLogProvider.readNextBlock(root);
        dataPack.appendCommits(nextPart);
        break;
      default:
        throw new IllegalStateException();
    }
  }

  @NotNull
  @Override
  public DataPack getDataPack() {
    return dataPack;
  }

  private static enum State {
    UNINITIALIZED,
    ALL_LOG_READER,
    PART_LOG_READER
  }
}
