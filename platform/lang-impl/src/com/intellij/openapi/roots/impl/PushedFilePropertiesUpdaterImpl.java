/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.ui.GuiUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexProjectHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

public class PushedFilePropertiesUpdaterImpl extends PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater");

  private final Project myProject;
  private final FilePropertyPusher[] myPushers;
  private final FilePropertyPusher[] myFilePushers;
  private final Queue<Runnable> myTasks = new ConcurrentLinkedQueue<>();

  public PushedFilePropertiesUpdaterImpl(final Project project) {
    myProject = project;
    myPushers = Extensions.getExtensions(FilePropertyPusher.EP_NAME);
    myFilePushers = ContainerUtil.findAllAsArray(myPushers, pusher -> !pusher.pushDirectoriesOnly());

    StartupManager.getInstance(project).registerPreStartupActivity(
      () -> project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
        @Override
        public void rootsChanged(final ModuleRootEvent event) {
          for (FilePropertyPusher pusher : myPushers) {
            pusher.afterRootsChanged(project);
          }
        }
      }));
  }

  public void processAfterVfsChanges(@NotNull List<? extends VFileEvent> events) {
    boolean pushedSomething = false;
    List<Runnable> delayedTasks = ContainerUtil.newArrayList();
    for (VFileEvent event : events) {
      final VirtualFile file = event.getFile();
      if (file == null) continue;

      final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
      if (pushers.length == 0) continue;

      if (event instanceof VFileCreateEvent) {
        if (!event.isFromRefresh() || !file.isDirectory()) {
          // push synchronously to avoid entering dumb mode in the middle of a meaningful write action
          // avoid dumb mode for just one file
          doPushRecursively(file, pushers, ProjectRootManager.getInstance(myProject).getFileIndex());
          pushedSomething = true;
        }
        else if (!ProjectCoreUtil.isProjectOrWorkspaceFile(file)) {
          ContainerUtil.addIfNotNull(delayedTasks, createRecursivePushTask(file, pushers));
        }
      } else if (event instanceof VFileMoveEvent) {
        for (FilePropertyPusher pusher : pushers) {
          file.putUserData(pusher.getFileDataKey(), null);
        }
        // push synchronously to avoid entering dumb mode in the middle of a meaningful write action
        doPushRecursively(file, pushers, ProjectRootManager.getInstance(myProject).getFileIndex());
        pushedSomething = true;
      }
    }
    if (!delayedTasks.isEmpty()) {
      queueTasks(delayedTasks);
    }
    if (pushedSomething) {
      GuiUtils.invokeLaterIfNeeded(() -> scheduleDumbModeReindexingIfNeeded(), ModalityState.defaultModalityState());
    }
  }

  @Override
  public void initializeProperties() {
    for (final FilePropertyPusher pusher : myPushers) {
      pusher.initExtra(myProject, myProject.getMessageBus(), new FilePropertyPusher.Engine() {
        @Override
        public void pushAll() {
          PushedFilePropertiesUpdaterImpl.this.pushAll(pusher);
        }

        @Override
        public void pushRecursively(VirtualFile file, Project project) {
          queueTasks(ContainerUtil.createMaybeSingletonList(createRecursivePushTask(file, new FilePropertyPusher[]{pusher})));
        }
      });
    }
  }

  @Override
  public void pushAllPropertiesNow() {
    performPushTasks();
    doPushAll(myPushers);
  }

  @Nullable
  private Runnable createRecursivePushTask(final VirtualFile dir, final FilePropertyPusher[] pushers) {
    if (pushers.length == 0) return null;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (!fileIndex.isInContent(dir)) return null;
    return () -> doPushRecursively(dir, pushers, fileIndex);
  }

  private void doPushRecursively(VirtualFile dir, final FilePropertyPusher[] pushers, ProjectFileIndex fileIndex) {
    fileIndex.iterateContentUnderDirectory(dir, new ContentIterator() {
      @Override
      public boolean processFile(final VirtualFile fileOrDir) {
        applyPushersToFile(fileOrDir, pushers, null);
        return true;
      }
    });
  }

  private void queueTasks(List<? extends Runnable> actions) {
    for (Runnable action : actions) {
      myTasks.offer(action);
    }
    final DumbModeTask task = new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        performPushTasks();
      }
    };
    myProject.getMessageBus().connect(task).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        DumbService.getInstance(myProject).cancelTask(task);
      }
    });
    DumbService.getInstance(myProject).queueTask(task);
  }

  private void performPushTasks() {
    boolean hadTasks = false;
    while (true) {
      Runnable task = myTasks.poll();
      if (task == null) {
        break;
      }
      try {
        task.run();
        hadTasks = true;
      }
      catch (ProcessCanceledException e) {
        queueTasks(Collections.singletonList(task)); // reschedule dumb mode and ensure the canceled task is enqueued again
        throw e;
      }
    }

    if (hadTasks) {
      scheduleDumbModeReindexingIfNeeded();
    }
  }

  private void scheduleDumbModeReindexingIfNeeded() {
    if (myProject.isDisposed()) return;

    DumbModeTask task = FileBasedIndexProjectHandler.createChangedFilesIndexingTask(myProject);
    if (task != null) {
      DumbService.getInstance(myProject).queueTask(task);
    }
  }

  private static <T> T findPusherValuesUpwards(Project project, VirtualFile dir, FilePropertyPusher<T> pusher, T moduleValue) {
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    if (moduleValue != null) return moduleValue;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    T projectValue = pusher.getImmediateValue(project, null);
    return projectValue != null? projectValue : pusher.getDefaultValue();
  }

  private static <T> T findPusherValuesUpwards(Project project, VirtualFile dir, FilePropertyPusher<T> pusher) {
    final T userValue = dir.getUserData(pusher.getFileDataKey());
    if (userValue != null) return userValue;
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    T projectValue = pusher.getImmediateValue(project, null);
    return projectValue != null ? projectValue : pusher.getDefaultValue();
  }

  @Override
  public void pushAll(final FilePropertyPusher... pushers) {
    queueTasks(Collections.singletonList(() -> doPushAll(pushers)));
  }

  private void doPushAll(final FilePropertyPusher[] pushers) {
    Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      public Module[] compute() {
        return ModuleManager.getInstance(myProject).getModules();
      }
    });

    List<Runnable> tasks = new ArrayList<>();

    for (final Module module : modules) {
      Runnable iteration = ApplicationManager.getApplication().runReadAction(new Computable<Runnable>() {
        @Override
        public Runnable compute() {
          if (module.isDisposed()) return EmptyRunnable.INSTANCE;
          ProgressManager.checkCanceled();

          final Object[] moduleValues = new Object[pushers.length];
          for (int i = 0; i < moduleValues.length; i++) {
            moduleValues[i] = pushers[i].getImmediateValue(module);
          }

          final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
          return () -> fileIndex.iterateContent(new ContentIterator() {
            @Override
            public boolean processFile(final VirtualFile fileOrDir) {
              applyPushersToFile(fileOrDir, pushers, moduleValues);
              return true;
            }
          });
        }
      });
      tasks.add(iteration);
    }

    invokeConcurrentlyIfPossible(tasks);
  }

  public static void invokeConcurrentlyIfPossible(final List<Runnable> tasks) {
    if (tasks.size() == 1 ||
        ApplicationManager.getApplication().isWriteAccessAllowed() ||
        !Registry.is("idea.concurrent.scanning.files.to.index")) {
      for(Runnable r:tasks) r.run();
      return;
    }

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    
    final ConcurrentLinkedQueue<Runnable> tasksQueue = new ConcurrentLinkedQueue<>(tasks);
    List<Future<?>> results = ContainerUtil.newArrayList();
    if (tasks.size() > 1) {
      int numThreads = Math.max(Math.min(CacheUpdateRunner.indexingThreadCount() - 1, tasks.size() - 1), 1);

      for (int i = 0; i < numThreads; ++i) {
        results.add(ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
          Runnable runnable;
          while ((runnable = tasksQueue.poll()) != null) runnable.run();
        }, ProgressWrapper.wrap(progress))));
      }
    }

    Runnable runnable;
    while ((runnable = tasksQueue.poll()) != null) runnable.run();

    for (Future<?> result : results) {
      try {
        result.get();
      } catch (Exception ex) {
        LOG.error(ex);
      }
    }
  }

  private void applyPushersToFile(final VirtualFile fileOrDir, final FilePropertyPusher[] pushers, final Object[] moduleValues) {
    ApplicationManager.getApplication().runReadAction(() -> {
      ProgressManager.checkCanceled();
      if (!fileOrDir.isValid()) return;
      doApplyPushersToFile(fileOrDir, pushers, moduleValues);
    });
  }
  private void doApplyPushersToFile(VirtualFile fileOrDir, FilePropertyPusher[] pushers, Object[] moduleValues) {
    FilePropertyPusher<Object> pusher = null;
    try {
      final boolean isDir = fileOrDir.isDirectory();
      for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
        //noinspection unchecked
        pusher = pushers[i];
        if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir)) || isDir && !pusher.acceptsDirectory(fileOrDir, myProject)) {
          continue;
        }
        findAndUpdateValue(fileOrDir, pusher, moduleValues != null ? moduleValues[i] : null);
      }
    }
    catch (AbstractMethodError ame) { // acceptsDirectory is missed
      if (pusher != null) throw new ExtensionException(pusher.getClass());
      throw ame;
    }
  }

  @Override
  public <T> void findAndUpdateValue(final VirtualFile fileOrDir, final FilePropertyPusher<T> pusher, final T moduleValue) {
    final T value = findPusherValuesUpwards(myProject, fileOrDir, pusher, moduleValue);
    updateValue(myProject, fileOrDir, value, pusher);
  }

  public static <T> void updateValue(final Project project, final VirtualFile fileOrDir, final T value, final FilePropertyPusher<T> pusher) {
    final T oldValue = fileOrDir.getUserData(pusher.getFileDataKey());
    if (value != oldValue) {
      fileOrDir.putUserData(pusher.getFileDataKey(), value);
      try {
        pusher.persistAttribute(project, fileOrDir, value);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void filePropertiesChanged(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    FileBasedIndex.getInstance().requestReindex(file);
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      reloadPsi(file, project);
    }
  }

  private static void reloadPsi(final VirtualFile file, final Project project) {
    final FileManagerImpl fileManager = (FileManagerImpl)((PsiManagerEx)PsiManager.getInstance(project)).getFileManager();
    if (fileManager.findCachedViewProvider(file) != null) {
      Runnable runnable = () -> WriteAction.run(() -> fileManager.forceReload(file));
      if (ApplicationManager.getApplication().isDispatchThread()) {
        runnable.run();
      } else {
        TransactionGuard.submitTransaction(project, runnable);
      }
    }
  }
}
