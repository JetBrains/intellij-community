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
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.VcsLogLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Holds the commit data loaded from the VCS, and is capable to {@link #refresh(Runnable) refresh} this data by request.</p>
 * <p>The commit data is acquired via {@link #getDataPack()}.</p>
 * <p>If refresh is in progress, {@link #getDataPack()} returns the previous data pack (possible not actual anymore).
 * When refresh completes, the data pack instance is updated. Refreshes are chained.</p>
 * <p>Thread-safety: TODO </p>
 * <p/>
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

  @NotNull private volatile DataPack myDataPack;
  @Nullable private volatile List<TimeCommitParents> myAllLog; // null means the whole log was not yet read from the VCS

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
    }, true);
  }

  private void loadAllLog() {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        Collection<List<? extends TimeCommitParents>> logs = ContainerUtil.newArrayList();
        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          List<TimeCommitParents> log = entry.getValue().readAllHashes(entry.getKey());
          logs.add(log);
        }
        myAllLog = myMultiRepoJoiner.join(logs);
      }
    });
  }

  public void rebuildLog(@NotNull final Runnable onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        if (myAllLog != null) {
          Collection<VcsRef> refs = readAllRefs();
          myDataPack = DataPack.build(myAllLog, refs, indicator);
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

  @NotNull
  private Collection<VcsRef> readAllRefs() throws VcsException {
    Collection<VcsRef> refs = ContainerUtil.newHashSet();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
      refs.addAll(entry.getValue().readAllRefs(entry.getKey()));
    }
    return refs;
  }

  /**
   * @param onSuccess this task is called on the EDT after loading and graph building completes.
   * @param ordered   passed to the {@link VcsLogProvider} to tell it is it is necessary to get commits from the VCS topologically ordered.
   */
  private void loadFirstPart(final Consumer<DataPack> onSuccess, final boolean ordered) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        Collection<List<? extends TimeCommitParents>> logs = new ArrayList<List<? extends TimeCommitParents>>(myLogProviders.size());
        Collection<VcsRef> allRefs = ContainerUtil.newHashSet();
        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          VcsLogProvider logProvider = entry.getValue();
          VirtualFile root = entry.getKey();
          List<? extends VcsCommitDetails> firstBlock = logProvider.readFirstBlock(root, ordered);
          Collection<VcsRef> refs = logProvider.readAllRefs(root);

          myDetailsGetter.saveInCache(firstBlock);
          myMiniDetailsGetter.saveInCache(firstBlock);

          List<? extends TimeCommitParents> refreshedLog;
          if (myAllLog == null) {
            // the whole log is not loaded before the first refresh
            refreshedLog = firstBlock;
          }
          else {
            // The whole log can't become null once loaded
            // noinspection ConstantConditions
            refreshedLog = myLogJoiner.addCommits(myAllLog, firstBlock, refs);
          }

          logs.add(refreshedLog);
          allRefs.addAll(refs);
        }

        List<TimeCommitParents> combinedLog = myMultiRepoJoiner.join(logs);
        myDataPack = DataPack.build(combinedLog, allRefs, indicator);

        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            onSuccess.consume(myDataPack);
          }
        });
      }
    });
  }

  @SuppressWarnings("ConstantConditions")
  @NotNull
  private List<TimeCommitParents> readLogFromStorage() {
    return myAllLog;
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
    }, false);
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
    myAllLog = null;
    myDataLoaderQueue.clear();
  }

  public boolean isAllLogReady() {
    return myAllLog != null;
  }

  @NotNull
  public VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

}
