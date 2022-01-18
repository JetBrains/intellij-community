// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIteratorEx;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileBasedIndexProjectHandler;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.indexing.diagnostic.ChangedFilesPushedDiagnostic;
import com.intellij.util.indexing.diagnostic.ChangedFilesPushingStatistics;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.roots.*;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PushedFilePropertiesUpdaterImpl extends PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance(PushedFilePropertiesUpdater.class);

  private final Project myProject;

  private final Queue<Runnable> myTasks = new ConcurrentLinkedQueue<>();

  public PushedFilePropertiesUpdaterImpl(@NotNull Project project) {
    myProject = project;

    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        if (LOG.isTraceEnabled()) {
          LOG
            .trace(new Throwable("Processing roots changed event (caused by file type change: " + event.isCausedByFileTypesChange() + ")"));
        }
        for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
          pusher.afterRootsChanged(project);
        }
      }
    });

    project.getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
        myTasks.clear();
      }
    });
  }

  @ApiStatus.Internal
  public void processAfterVfsChanges(@NotNull List<? extends VFileEvent> events) {
    List<Runnable> syncTasks = new ArrayList<>();
    List<Runnable> delayedTasks = new ArrayList<>();
    List<FilePropertyPusher<?>> filePushers = getFilePushers();

    for (VFileEvent event : events) {
      if (event instanceof VFileCreateEvent) {
        boolean isDirectory = ((VFileCreateEvent)event).isDirectory();
        List<FilePropertyPusher<?>> pushers = isDirectory ? FilePropertyPusher.EP_NAME.getExtensionList() : filePushers;

        if (!event.isFromRefresh()) {
          ContainerUtil.addIfNotNull(syncTasks, createRecursivePushTask(event, pushers));
        }
        else {
          FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(((VFileCreateEvent)event).getChildName());
          boolean isProjectOrWorkspaceFile = fileType instanceof InternalFileType ||
                                             VfsUtilCore.findContainingDirectory(((VFileCreateEvent)event).getParent(),
                                                                                 Project.DIRECTORY_STORE_FOLDER) != null;
          if (!isProjectOrWorkspaceFile) {
            ContainerUtil.addIfNotNull(delayedTasks, createRecursivePushTask(event, pushers));
          }
        }
      }
      else if (event instanceof VFileMoveEvent || event instanceof VFileCopyEvent) {
        VirtualFile file = getFile(event);
        if (file == null) continue;
        boolean isDirectory = file.isDirectory();
        List<FilePropertyPusher<?>> pushers = isDirectory ? FilePropertyPusher.EP_NAME.getExtensionList() : filePushers;
        for (FilePropertyPusher<?> pusher : pushers) {
          file.putUserData(pusher.getFileDataKey(), null);
        }
        ContainerUtil.addIfNotNull(syncTasks, createRecursivePushTask(event, pushers));
      }
    }
    boolean pushingSomethingSynchronously =
      !syncTasks.isEmpty() && syncTasks.size() < FileBasedIndexProjectHandler.ourMinFilesToStartDumbMode;
    if (pushingSomethingSynchronously) {
      // push synchronously to avoid entering dumb mode in the middle of a meaningful write action
      // when only a few files are created/moved
      syncTasks.forEach(Runnable::run);
    }
    else {
      delayedTasks.addAll(syncTasks);
    }
    if (!delayedTasks.isEmpty()) {
      queueTasks(delayedTasks, "Push on VFS changes");
    }
    if (pushingSomethingSynchronously) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> scheduleDumbModeReindexingIfNeeded());
    }
  }

  private static VirtualFile getFile(@NotNull VFileEvent event) {
    VirtualFile file = event.getFile();
    if (event instanceof VFileCopyEvent) {
      file = ((VFileCopyEvent)event).getNewParent().findChild(((VFileCopyEvent)event).getNewChildName());
    }
    return file;
  }

  @Override
  public void runConcurrentlyIfPossible(List<? extends Runnable> tasks) {
    invokeConcurrentlyIfPossible(tasks);
  }

  public void initializeProperties() {
    FilePropertyPusher.EP_NAME.forEachExtensionSafe(pusher -> {
      pusher.initExtra(myProject);
    });
  }

  public static void applyScannersToFile(@NotNull VirtualFile fileOrDir, List<IndexableFileScanner.IndexableFileVisitor> sessions) {
    for (IndexableFileScanner.IndexableFileVisitor session : sessions) {
      try {
        session.visitFile(fileOrDir);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error("Failed to visit file", e, new Attachment("filePath.txt", fileOrDir.getPath()));
      }
    }
  }

  @Nullable
  private Runnable createRecursivePushTask(@NotNull VFileEvent event, @NotNull List<? extends FilePropertyPusher<?>> pushers) {
    List<IndexableFileScanner> scanners = IndexableFileScanner.EP_NAME.getExtensionList();
    if (pushers.isEmpty() && scanners.isEmpty()) {
      return null;
    }

    return () -> {
      // delay calling event.getFile() until background to avoid expensive VFileCreateEvent.getFile() in EDT
      VirtualFile dir = getFile(event);
      ProjectFileIndex fileIndex = ReadAction.compute(() -> ProjectFileIndex.getInstance(myProject));
      if (dir != null && ReadAction.compute(() -> fileIndex.isInContent(dir)) && !ProjectUtil.isProjectOrWorkspaceFile(dir)) {
        doPushRecursively(pushers, scanners, new ProjectIndexableFilesIteratorImpl(dir));
      }
    };
  }

  private void doPushRecursively(@NotNull List<? extends FilePropertyPusher<?>> pushers,
                                 @NotNull List<IndexableFileScanner> scanners,
                                 @NotNull IndexableFilesIterator indexableFilesIterator) {
    List<IndexableFileScanner.IndexableFileVisitor> sessions =
      ContainerUtil.mapNotNull(scanners, visitor -> visitor.startSession(myProject).createVisitor(indexableFilesIterator.getOrigin()));
    indexableFilesIterator.iterateFiles(myProject, fileOrDir -> {
      applyPushersToFile(fileOrDir, pushers, null);
      applyScannersToFile(fileOrDir, sessions);
      return true;
    }, IndexableFilesDeduplicateFilter.create());
  }

  private void queueTasks(@NotNull List<? extends Runnable> actions, @NotNull @NonNls String reason) {
    actions.forEach(myTasks::offer);
    DumbModeTask task = new MyDumbModeTask(reason, this);
    myProject.getMessageBus().connect(task).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        for (RootsChangeIndexingInfo info : ((ModuleRootEventImpl)event).getInfos()) {
          if (info == RootsChangeIndexingInfo.TOTAL_REINDEX) {
            DumbService.getInstance(myProject).cancelTask(task);
            return;
          }
        }
      }
    });
    task.queue(myProject);
  }

  public void performDelayedPushTasks() { performDelayedPushTasks(null); }

  private void performDelayedPushTasks(@Nullable ChangedFilesPushingStatistics statistics) {
    boolean hadTasks = false;
    while (true) {
      ProgressManager.checkCanceled(); // give a chance to suspend indexing

      Runnable task = myTasks.poll();
      if (task == null) {
        break;
      }
      try {
        task.run();
        hadTasks = true;
      }
      catch (ProcessCanceledException e) {
        if (statistics != null) {
          statistics.finished(true);
          ChangedFilesPushedDiagnostic.INSTANCE.addEvent(myProject, statistics);
        }
        queueTasks(Collections.singletonList(task),
                   "Rerun pushing tasks after process cancelled"); // reschedule dumb mode and ensure the canceled task is enqueued again
        throw e;
      }
    }

    if (hadTasks) {
      scheduleDumbModeReindexingIfNeeded();
    }
    if (statistics != null) {
      statistics.finished(false);
      ChangedFilesPushedDiagnostic.INSTANCE.addEvent(myProject, statistics);
    }
  }

  private void scheduleDumbModeReindexingIfNeeded() {
    FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(myProject);
  }

  @Override
  public void filePropertiesChanged(@NotNull VirtualFile fileOrDir, @NotNull Condition<? super VirtualFile> acceptFileCondition) {
    if (fileOrDir.isDirectory()) {
      for (VirtualFile child : fileOrDir.getChildren()) {
        if (!child.isDirectory() && acceptFileCondition.value(child)) {
          filePropertiesChanged(child);
        }
      }
    }
    else if (acceptFileCondition.value(fileOrDir)) {
      filePropertiesChanged(fileOrDir);
    }
  }

  private static <T> T findNewPusherValue(Project project, VirtualFile fileOrDir, FilePropertyPusher<? extends T> pusher, T moduleValue) {
    //Do not check fileOrDir.getUserData() as it may be outdated.
    T immediateValue = pusher.getImmediateValue(project, fileOrDir);
    if (immediateValue != null) return immediateValue;
    if (moduleValue != null) return moduleValue;
    return findNewPusherValueFromParent(project, fileOrDir, pusher);
  }

  private static <T> T findNewPusherValueFromParent(Project project, VirtualFile fileOrDir, FilePropertyPusher<? extends T> pusher) {
    final VirtualFile parent = fileOrDir.getParent();
    if (parent != null && ProjectFileIndex.getInstance(project).isInContent(parent)) {
      final T userValue = parent.getUserData(pusher.getFileDataKey());
      if (userValue != null) return userValue;
      return findNewPusherValue(project, parent, pusher, null);
    }
    T projectValue = pusher.getImmediateValue(project, null);
    return projectValue != null ? projectValue : pusher.getDefaultValue();
  }

  @Override
  public void pushAll(FilePropertyPusher<?> @NotNull ... pushers) {
    queueTasks(Collections.singletonList(() -> doPushAll(Arrays.asList(pushers))), "Push all on " + Arrays.toString(pushers));
  }

  private void doPushAll(@NotNull List<? extends FilePropertyPusher<?>> pushers) {
    scanProject(myProject, module -> {
      final Object[] moduleValues = getModuleImmediateValues(pushers, module);
      return fileOrDir -> {
        applyPushersToFile(fileOrDir, pushers, moduleValues);
        return ContentIteratorEx.Status.CONTINUE;
      };
    });
  }

  public static Object @NotNull [] getModuleImmediateValues(@NotNull List<? extends FilePropertyPusher<?>> pushers,
                                                            @NotNull Module module) {
    final Object[] moduleValues;
    moduleValues = new Object[pushers.size()];
    for (int i = 0; i < moduleValues.length; i++) {
      moduleValues[i] = pushers.get(i).getImmediateValue(module);
    }
    return moduleValues;
  }

  public static void scanProject(@NotNull Project project,
                                 @NotNull Function<Module, ? extends ContentIteratorEx> iteratorProducer) {
    Stream<Runnable> tasksStream;
    //noinspection deprecation
    if (DefaultProjectIndexableFilesContributor.indexProjectBasedOnIndexableEntityProviders()) {
      Sequence<ModuleEntity> modulesSequence = ReadAction.compute(() ->
                                                                    WorkspaceModel.getInstance(project).getEntityStorage().
                                                                      getCurrent().entities(ModuleEntity.class));
      List<ModuleEntity> moduleEntities = SequencesKt.toList(modulesSequence);
      IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();
      tasksStream = moduleEntities.stream()
        .flatMap(moduleEntity -> {
          return ReadAction.compute(() -> {
            WorkspaceEntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
            Module module = IndexableEntityProviderMethods.INSTANCE.findModuleForEntity(moduleEntity, storage, project);
            if (module == null) {
              return Stream.empty();
            }
            ProgressManager.checkCanceled();
            return ContainerUtil.map(IndexableEntityProviderMethods.INSTANCE.createIterators(moduleEntity, storage, project),
                                     it -> new Object() {
                                       final IndexableFilesIterator files = it;
                                       final ContentIteratorEx iterator = iteratorProducer.apply(module);
                                     })
              .stream()
              .map(pair -> (Runnable)() -> {
                pair.files.iterateFiles(project, pair.iterator, indexableFilesDeduplicateFilter);
              });
          });
        });
    }
    else {
      Module[] modules = ReadAction.compute(() -> ModuleManager.getInstance(project).getModules());
      IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();
      tasksStream = Arrays.stream(modules)
        .flatMap(module -> {
          return ReadAction.compute(() -> {
            if (module.isDisposed()) return Stream.empty();
            ProgressManager.checkCanceled();
            //noinspection deprecation
            return ContainerUtil.map(ModuleIndexableFilesIteratorImpl.getModuleIterators(module), it -> new Object() {
                final IndexableFilesIterator files = it;
                final ContentIteratorEx iterator = iteratorProducer.apply(it.getOrigin().getModule());
              })
              .stream()
              .map(pair -> (Runnable)() -> {
                pair.files.iterateFiles(project, pair.iterator, indexableFilesDeduplicateFilter);
              });
          });
        });
    }
    List<Runnable> tasks = tasksStream.collect(Collectors.toList());
    invokeConcurrentlyIfPossible(tasks);
  }

  public static void invokeConcurrentlyIfPossible(@NotNull List<? extends Runnable> tasks) {
    if (tasks.isEmpty()) return;
    if (tasks.size() == 1 || ApplicationManager.getApplication().isWriteAccessAllowed()) {
      for (Runnable r : tasks) r.run();
      return;
    }
    ConcurrentLinkedQueue<Runnable> tasksQueue = new ConcurrentLinkedQueue<>(tasks);
    FilesScanExecutor.runOnAllThreads(() -> {
      Runnable runnable;
      while ((runnable = tasksQueue.poll()) != null) runnable.run();
    });
  }

  public void applyPushersToFile(final VirtualFile fileOrDir,
                                 @NotNull List<? extends FilePropertyPusher<?>> pushers,
                                 final Object[] moduleValues) {
    if (pushers.isEmpty()) return;
    if (fileOrDir.isDirectory()) {
      fileOrDir.getChildren(); // outside read action to avoid freezes
    }

    ReadAction.run(() -> {
      if (!fileOrDir.isValid() || !(fileOrDir instanceof VirtualFileWithId)) return;
      doApplyPushersToFile(fileOrDir, pushers, moduleValues);
    });
  }

  private void doApplyPushersToFile(@NotNull VirtualFile fileOrDir,
                                    @NotNull List<? extends FilePropertyPusher<?>> pushers,
                                    Object @Nullable[] moduleValues) {
    final boolean isDir = fileOrDir.isDirectory();
    for (int i = 0; i < pushers.size(); i++) {
      //noinspection unchecked
      FilePropertyPusher<Object> pusher = (FilePropertyPusher<Object>)pushers.get(i);
      if (isDir
          ? !pusher.acceptsDirectory(fileOrDir, myProject)
          : pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir, myProject)) {
        continue;
      }
      Object value = moduleValues != null ? moduleValues[i] : null;
      findAndUpdateValue(fileOrDir, pusher, value);
    }
  }

  @Override
  public <T> void findAndUpdateValue(@NotNull VirtualFile fileOrDir, @NotNull FilePropertyPusher<T> pusher, @Nullable T moduleValue) {
    T newValue = findNewPusherValue(myProject, fileOrDir, pusher, moduleValue);
    T oldValue = fileOrDir.getUserData(pusher.getFileDataKey());
    if (newValue != oldValue) {
      fileOrDir.putUserData(pusher.getFileDataKey(), newValue);
      try {
        pusher.persistAttribute(myProject, fileOrDir, newValue);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void filePropertiesChanged(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    if (fileBasedIndex instanceof FileBasedIndexImpl) {
      ((FileBasedIndexImpl) fileBasedIndex).requestReindex(file, false);
    }
    for (final Project project : ProjectManager.getInstance().getOpenProjects()) {
      reloadPsi(file, project);
    }
  }

  private static void reloadPsi(final VirtualFile file, final Project project) {
    final FileManagerImpl fileManager = (FileManagerImpl)PsiManagerEx.getInstanceEx(project).getFileManager();
    if (fileManager.findCachedViewProvider(file) != null) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), project.getDisposed(),
                                         () -> WriteAction.run(() -> fileManager.forceReload(file))
      );
    }
  }

  private static List<FilePropertyPusher<?>> getFilePushers() {
    return ContainerUtil.findAll(FilePropertyPusher.EP_NAME.getExtensionList(), pusher -> !pusher.pushDirectoriesOnly());
  }

  private static class MyDumbModeTask extends DumbModeTask {
    private final @NotNull @NonNls String myReason;
    private final PushedFilePropertiesUpdaterImpl myUpdater;

    private MyDumbModeTask(@NotNull @NonNls String reason, @NotNull PushedFilePropertiesUpdaterImpl updater) {
      myUpdater = updater;
      myReason = reason;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText(IndexingBundle.message("progress.indexing.scanning"));
      ChangedFilesPushingStatistics statistics;
      if (!ApplicationManager.getApplication().isUnitTestMode() || IndexDiagnosticDumper.getShouldDumpInUnitTestMode()) {
        statistics = new ChangedFilesPushingStatistics(myReason);
      }
      else {
        statistics = null;
      }
      ((GistManagerImpl)GistManager.getInstance()).runWithMergingDependentCacheInvalidations(() ->
        myUpdater.performDelayedPushTasks(statistics));
    }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof MyDumbModeTask && ((MyDumbModeTask)taskFromQueue).myUpdater == myUpdater) return this;
      return null;
    }
  }
}
