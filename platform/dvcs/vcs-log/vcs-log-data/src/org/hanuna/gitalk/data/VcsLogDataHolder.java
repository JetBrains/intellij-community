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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import org.jetbrains.annotations.NotNull;

/**
 * <p>Holds the commit data loaded from the VCS, and is capable to {@link #refresh(Runnable) refresh} this data by request.</p>
 * <p>The commit data is acquired via {@link #getDataPack()}.</p>
 * <p>If refresh is in progress, {@link #getDataPack()} returns the previous data pack (possible not actual anymore).
 *    When refresh completes, the data pack instance is updated. Refreshes are chained.</p>
 *
 * @author Kirill Likhodedov
 */
public class VcsLogDataHolder implements VcsLogRefresher {

  public static final Topic<Runnable> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", Runnable.class);

  private static final Logger LOG = Logger.getInstance("Git.Log");

  @NotNull private final Project myProject;
  @NotNull private final VcsLogProvider myLogProvider;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final VcsCommitCache myCommitCache;
  @NotNull private final Runnable myRefreshCompletedEvent;

  @NotNull private DataPack myDataPack;

  public static void init(@NotNull final Project project, @NotNull final VcsLogProvider logProvider, @NotNull final VirtualFile root,
                          @NotNull final Consumer<VcsLogDataHolder> onSuccess) {
    final BackgroundTaskQueue taskQueue = new BackgroundTaskQueue(project, "Loading history...");
    final VcsCommitCache commitCache = new VcsCommitCache(logProvider, root);

    doRefresh(taskQueue, project, logProvider, root, commitCache, new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        VcsLogDataHolder dataHolder = new VcsLogDataHolder(project, logProvider, root, taskQueue, commitCache, dataPack);
        onSuccess.consume(dataHolder);
      }
    });
  }

  private VcsLogDataHolder(@NotNull Project project, @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
                           @NotNull BackgroundTaskQueue taskQueue, @NotNull VcsCommitCache commitCache, @NotNull DataPack dataPack) {
    myProject = project;
    myLogProvider = logProvider;
    myRoot = root;
    myDataLoaderQueue = taskQueue;
    myCommitCache = commitCache;
    myDataPack = dataPack;

    myRefreshCompletedEvent = new Runnable() {
      @Override
      public void run() {
        myProject.getMessageBus().syncPublisher(REFRESH_COMPLETED).run();
      }
    };
  }

  public void refresh(@NotNull final Runnable onSuccess) {
    doRefresh(myDataLoaderQueue, myProject, myLogProvider, myRoot, myCommitCache, new Consumer<DataPack>() {
      @Override
      public void consume(DataPack dataPack) {
        myDataPack = dataPack;
        onSuccess.run();
      }
    });
  }

  @Override
  public void refreshCompletely() {
    refresh(myRefreshCompletedEvent);
  }

  @Override
  public void refreshAll(@NotNull VirtualFile root) {
    refresh(myRefreshCompletedEvent);
  }

  @Override
  public void refreshRefs(@NotNull VirtualFile root) {
    refreshAll(root); // TODO
  }

  private static void doRefresh(@NotNull BackgroundTaskQueue queue, @NotNull Project project, @NotNull final VcsLogProvider logProvider,
                                @NotNull final VirtualFile root, @NotNull final VcsCommitCache commitCache,
                                @NotNull final Consumer<DataPack> dataPackConsumer) {
    queue.run(new Task.Backgroundable(project, "Loading history...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final DataPack dataPack = DataPack.build(logProvider.readNextBlock(root), logProvider.readAllRefs(root), indicator, commitCache,
                                                   logProvider, root);

          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              dataPackConsumer.consume(dataPack);
            }
          });
        }
        catch (VcsException e) {
          // TODO
          //notifyError(e);
          throw new RuntimeException(e);
        }

      }
    });
  }

  @NotNull
  public DataPack getDataPack() {
    return myDataPack;
  }

}
