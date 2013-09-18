/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hanuna.gitalk.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Holds the commit data loaded from the VCS, and is capable to refresh this data by
 * {@link VcsLogProvider#subscribeToRootRefreshEvents(Collection, VcsLogRefresher) events from the VCS}.</p>
 * <p>The commit data is acquired via {@link #getDataPack()}.</p>
 * <p>If refresh is in progress, {@link #getDataPack()} returns the previous data pack (possible not actual anymore).
 *    When refresh completes, the data pack instance is updated. Refreshes are chained.</p>
 *
 * <p><b>Thread-safety:</b> the class is thread-safe:
 *   <ul>
 *     <li>All write-operations made to the log and data pack are made sequentially, from the same background queue;</li>
 *     <li>Once a refresh request is received, we read logs and refs from all providers, join refresh data, join repositories,
 *         and build the new data pack sequentially in a single thread.</li>
 *     <li>Whilst we are in the middle of this refresh process, anyone who requests the data pack (and possibly the log if this would
 *         be available in the future), will get the consistent previous version of it.</li>
 *   </ul></p>
 *
 * <p><b>Initialization:</b><ul>
 *    <li>Initially the first part of the log is loaded and is shown to the user.</li>
 *    <li>Right after that, the whole log is loaded in the background. We need the whole log for two reasons:
 *        we don't want to hang for a minute if user decides to go to an old commit or even scroll;
 *        we need the whole log to properly perform the optimized refresh procedure.</li>
 *    <li>Once the whole log information is loaded, we don't rebuild the graphical log, because users rarely need old commits while
 *        memory would be occupied and performance would suffer.</li>
 *    <li>If user requests a commit that is not displayed (by clicking on an old branch reference, by going to a hash,
 *        just by scrolling down to the end of the table), we take the whole log, build the graph and show to the user.</li>
 *    <li>Once the whole log was once requested, we never hide it after a refresh, because it could annoy the user if the log would hide
 *        because of some external event which forces log refresh (fetch, branch switch, etc.) while he was looking at old history.</li>
 *    </li></ul>
 * </p>
 *
 * <p><b>Refresh procedure:</b><ul>
 *    <li>When a refresh request comes, we ask {@link VcsLogProvider log providers} about the last N commits unordered (which
 *        adds a significant performance gain in the case of Git).</li>
 *    <li>Then we order and attach these commits to the log with the help of {@link VcsLogJoiner}.</li>
 *    <li>In the multiple repository case logs from different providers are joined together in a single log.</li>
 *    <li>If user was looking at the top part of the log, the log is rebuilt, and new top of the log is shown to the user.
 *        If, however, he was looking at the whole log, the data pack for the whole log is rebuilt and shown to the user.
 *    </li></ul></p>
 *
 * TODO: error handling
 *
 * @author Kirill Likhodedov
 */
public class VcsLogDataHolder implements Disposable {

  public static final Topic<Runnable> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", Runnable.class);

  private static final Logger LOG = VcsLogLogger.LOG;

  @NotNull private final Project myProject;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;
  @NotNull private final VcsLogJoiner myLogJoiner;
  @NotNull private final VcsLogMultiRepoJoiner myMultiRepoJoiner;

  // all write-access to myDataPack & myLogData is performed only via the myDataLoaderQueue
  @NotNull private volatile DataPack myDataPack;
  @NotNull private volatile LogData myLogData;
  private volatile boolean myFullLogShowing;

  public VcsLogDataHolder(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    myProject = project;
    myLogProviders = logProviders;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    myMiniDetailsGetter = new MiniDetailsGetter(this, logProviders);
    myDetailsGetter = new CommitDetailsGetter(this, logProviders);
    myLogJoiner = new VcsLogJoiner();
    myMultiRepoJoiner = new VcsLogMultiRepoJoiner();
  }

  /**
   * Initializes the VcsLogDataHolder in background in the following sequence:
   * <ul>
   * <li>Loads the first part of the log with details.</li>
   * <li>Invokes the Consumer to initialize the UI with the initial data pack.</li>
   * <li>Loads the whole log in background. When completed, substitutes the data and tells the UI to refresh itself.</li>
   * </ul>
   *
   * @param onInitialized This is called when the holder is initialized with the initial data received from the VCS.
   *                      The consumer is called on the EDT.
   */
  public static void init(@NotNull final Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                          @NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    final VcsLogDataHolder dataHolder = new VcsLogDataHolder(project, logProviders);
    dataHolder.initialize(onInitialized);
  }

  private void initialize(@NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        onInitialized.consume(VcsLogDataHolder.this);
        // after first part is loaded and shown to the user, load the whole log in background
        loadAllLog();
      }
    });
  }

  private void loadAllLog() {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        Map<VirtualFile, List<TimeCommitParents>> logs = ContainerUtil.newHashMap();
        Map<VirtualFile, Collection<VcsRef>> refs = ContainerUtil.newHashMap();
        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          VirtualFile root = entry.getKey();
          VcsLogProvider logProvider = entry.getValue();
          logs.put(root, logProvider.readAllHashes(root));
          refs.put(root, logProvider.readAllRefs(root));
        }
        myLogData = new LogData(logs, refs);
      }
    });
  }

  public void showFullLog(@NotNull final Runnable onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        if (myLogData != null) {
          List<TimeCommitParents> compoundLog = myMultiRepoJoiner.join(myLogData.myLogsByRoot.values());
          myDataPack = DataPack.build(compoundLog, myLogData.getAllRefs(), indicator);
          myFullLogShowing = true;
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              notifyAboutDataRefresh();
              onSuccess.run();
            }
          });
        }
      }
    });
  }

  /**
   * Loads the top part of the log and rebuilds the graph & log table.
   *
   * @param onSuccess this task is called {@link UIUtil.invokeAndWaitIfNeeded(Runnable) on the EDT} after loading and graph
   *                  building completes.
   */
  private void loadFirstPart(@NotNull final Consumer<DataPack> onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        boolean ordered = !isFullLogReady(); // full log is not ready (or it is initial loading) => need to fairly query the VCS

        Map<VirtualFile, List<TimeCommitParents>> logsToBuild = ContainerUtil.newHashMap();
        Collection<VcsRef> allRefs = ContainerUtil.newHashSet();

        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          VirtualFile root = entry.getKey();
          VcsLogProvider logProvider = entry.getValue();
          List<? extends VcsCommitDetails> firstBlock = logProvider.readFirstBlock(root, ordered);
          Collection<VcsRef> newRefs = logProvider.readAllRefs(root);

          myDetailsGetter.saveInCache(firstBlock);
          myMiniDetailsGetter.saveInCache(firstBlock);

          List<TimeCommitParents> refreshedLog;
          if (ordered) {
            // the whole log is not loaded before the first refresh
            refreshedLog = new ArrayList<TimeCommitParents>(firstBlock);
          }
          else {
            refreshedLog = myLogJoiner.addCommits(myLogData.getLog(root), myLogData.getRefs(root), firstBlock, newRefs);
          }

          if (myFullLogShowing) {
            logsToBuild.put(root, refreshedLog);
          }
          else {
            int bottomIndex = firstBlock.size() - 1;
            if (myDataPack != null) {
              // TODO identify the bottom commit which was showing previously and show the part of the log up to it
              bottomIndex = myDataPack.getGraphModel().getGraph().getNodeRows().size() - 1;
            }
            logsToBuild.put(root, refreshedLog.subList(0, Math.min(bottomIndex, refreshedLog.size())));
          }
          allRefs.addAll(newRefs);
          myLogData.setRefs(root, newRefs); // update references, because the joiner needs to know reference changes after each refresh
                                            // log is not updated: once loaded, joiner always join to the old part of it
        }

        List<TimeCommitParents> compoundLog = myMultiRepoJoiner.join(logsToBuild.values());
        myDataPack = DataPack.build(compoundLog, allRefs, indicator);

        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            onSuccess.consume(myDataPack);
          }
        });
      }
    });
  }

  private void runInBackground(final ThrowableConsumer<ProgressIndicator, VcsException> task) {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, "Loading history...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          task.consume(indicator);
        }
        catch (VcsException e) {
          throw new RuntimeException(e); // TODO
        }
      }
    });
  }

  private void refresh(@NotNull final Runnable onSuccess) {
    loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        onSuccess.run();
      }
    });
  }

  /**
   * Makes the log perform complete refresh for all roots.
   * It fairly retrieves the data from the VCS and rebuilds the whole log.
   */
  public void refreshCompletely() {
    initialize(new Consumer<VcsLogDataHolder>() {
      @Override
      public void consume(VcsLogDataHolder holder) {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  public void refresh(@NotNull VirtualFile root) {
    // TODO refresh only the given root, not all roots
    refresh(new Runnable() {
      @Override
      public void run() {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log refresh only the reference labels for the given root.
   */
  public void refreshRefs(@NotNull VirtualFile root) {
    // TODO no need to query the VCS for commit & rebuild the whole log; just replace refs labels.
    refresh(root);
  }

  @NotNull
  public DataPack getDataPack() {
    return myDataPack;
  }

  private void notifyAboutDataRefresh() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(REFRESH_COMPLETED).run();
    }
  }

  public CommitDetailsGetter getCommitDetailsGetter() {
    return myDetailsGetter;
  }

  @NotNull
  public MiniDetailsGetter getMiniDetailsGetter() {
    return myMiniDetailsGetter;
  }

  @Override
  public void dispose() {
    myLogData = null;
    myDataLoaderQueue.clear();
  }

  public boolean isFullLogReady() {
    return myLogData != null;
  }

  @NotNull
  public VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

  /**
   * Contains full logs per repository root & references per root.
   */
  private static class LogData {
    @NotNull private final Map<VirtualFile, List<TimeCommitParents>> myLogsByRoot;
    @NotNull private final Map<VirtualFile, Collection<VcsRef>> myRefsByRoot;

    private LogData(@NotNull Map<VirtualFile, List<TimeCommitParents>> logsByRoot,
                    @NotNull Map<VirtualFile, Collection<VcsRef>> refsByRoot) {
      myLogsByRoot = logsByRoot;
      myRefsByRoot = refsByRoot;
    }

    @NotNull
    public List<TimeCommitParents> getLog(@NotNull VirtualFile root) {
      return myLogsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getRefs(@NotNull VirtualFile root) {
      return myRefsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getAllRefs() {
      Collection<VcsRef> allRefs = new HashSet<VcsRef>();
      for (Collection<VcsRef> refs : myRefsByRoot.values()) {
        allRefs.addAll(refs);
      }
      return allRefs;
    }

    public void setRefs(@NotNull VirtualFile root, @NotNull Collection<VcsRef> refs) {
      myRefsByRoot.put(root, refs);
    }
  }

}
