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

import java.util.*;

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
  public void readNextPart(@NotNull ProgressIndicator indicator, @NotNull VirtualFile root) throws VcsException {
    switch (state) {
      case ALL_LOG_READER:
        throw new IllegalStateException("data was read");
      case UNINITIALIZED:
        dataPack = DataPackImpl.buildDataPack(myLogProvider.readNextBlock(root), myLogProvider.readAllRefs(root), indicator, myProject,
                                              myCommitDataCache, myLogProvider, root);
        state = State.PART_LOG_READER;
        break;
      case PART_LOG_READER:
        List<? extends VcsCommit> nextPart = myLogProvider.readNextBlock(root);
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
