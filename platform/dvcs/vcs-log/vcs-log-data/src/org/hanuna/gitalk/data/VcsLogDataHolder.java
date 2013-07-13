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
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import org.hanuna.gitalk.common.compressedlist.VcsLogLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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
public class VcsLogDataHolder implements VcsLogRefresher, Disposable {

  public static final Topic<Runnable> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", Runnable.class);

  private static final Logger LOG = VcsLogLogger.LOG;

  @NotNull private final Project myProject;
  @NotNull private final VcsLogProvider myLogProvider;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;

  @NotNull private volatile DataPack myDataPack;
  @Nullable private volatile List<? extends CommitParents> myAllLog; // null means the whole log was not yet read

  public VcsLogDataHolder(@NotNull Project project, @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root) {
    myProject = project;
    myLogProvider = logProvider;
    myRoot = root;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    myMiniDetailsGetter = new MiniDetailsGetter(this, logProvider, root);
    myDetailsGetter = new CommitDetailsGetter(this, logProvider, root);
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
  public static void init(@NotNull final Project project, @NotNull final VcsLogProvider logProvider, @NotNull final VirtualFile root,
                          @NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    final VcsLogDataHolder dataHolder = new VcsLogDataHolder(project, logProvider, root);
    dataHolder.loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        dataHolder.myDataPack = dataPack;
        onInitialized.consume(dataHolder);
        dataHolder.loadAllLog();
      }
    });
  }

  private void loadAllLog() {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        List<? extends CommitParents> all = myLogProvider.readAllHashes(myRoot);
        Collection<Ref> refs = myLogProvider.readAllRefs(myRoot);

        myAllLog = all;
        myDataPack = DataPack.build(all, refs, indicator);
        notifyAboutDataRefresh();
      }
    });
  }

  private void loadFirstPart(final Consumer<DataPack> onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        List<? extends VcsCommitDetails> commits = myLogProvider.readFirstBlock(myRoot);
        Collection<Ref> refs = myLogProvider.readAllRefs(myRoot);

        myDetailsGetter.saveInCache(commits);
        myMiniDetailsGetter.saveInCache(commits);

        myDataPack = DataPack.build(commits, refs, indicator);

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

  public void refresh(@NotNull final Runnable onSuccess) {
    loadFirstPart(new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        onSuccess.run();
      }
    });
  }

  @Override
  public void refreshCompletely() {
    refresh(new Runnable() {
      @Override
      public void run() {
        notifyAboutDataRefresh();
      }
    });
  }

  @Override
  public void refresh(@NotNull VirtualFile root) {
    refresh(new Runnable() {
      @Override
      public void run() {
        notifyAboutDataRefresh();
      }
    });
  }

  @Override
  public void refreshRefs(@NotNull VirtualFile root) {
    refresh(root); // TODO no need to query the VCS for commit & rebuild the whole log; just replace refs labels.
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
}
