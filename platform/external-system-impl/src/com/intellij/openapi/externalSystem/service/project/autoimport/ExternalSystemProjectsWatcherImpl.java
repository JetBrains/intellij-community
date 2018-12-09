// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.ProjectTopics;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT;

/**
 * @author Vladislav.Soroka
 */
public class ExternalSystemProjectsWatcherImpl extends ExternalSystemTaskNotificationListenerAdapter
  implements ExternalSystemProjectsWatcher {

  private static final Logger LOG = Logger.getInstance(ExternalSystemProjectsWatcherImpl.class);

  private static final ExtensionPointName<Contributor> EP_NAME =
    ExtensionPointName.create("com.intellij.externalProjectWatcherContributor");

  private static final Key<Long> CRC_WITHOUT_SPACES_CURRENT = Key.create("ExternalSystemProjectsWatcher.CRC_WITHOUT_SPACES_CURRENT");
  private static final Key<Long> CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT =
    Key.create("ExternalSystemProjectsWatcher.CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT");
  private static final int DOCUMENT_SAVE_DELAY = 1000;
  private static final int REFRESH_MERGING_TIME_SPAN = 2000;

  private final Project myProject;
  private final MergingUpdateQueue myChangedDocumentsQueue;
  private final List<ExternalSystemAutoImportAware> myImportAwareManagers;
  private final MergingUpdateQueue myUpdatesQueue;
  private final Map<ProjectSystemId, MyNotification> myNotificationMap;
  private final MultiMap<String/* project path */, String /* files paths */> myKnownAffectedFiles = MultiMap.createConcurrentSet();
  private final MultiMap<VirtualFilePointer, String /* project path */> myFilesPointers = MultiMap.createConcurrentSet();
  private final List<LocalFileSystem.WatchRequest> myWatchedRoots = new ArrayList<>();
  private final MergingUpdateQueue myRefreshRequestsQueue;

  public ExternalSystemProjectsWatcherImpl(Project project) {
    myProject = project;
    myChangedDocumentsQueue = new MergingUpdateQueue("ExternalSystemProjectsWatcher: Document changes queue",
                                                     DOCUMENT_SAVE_DELAY, false, ANY_COMPONENT, myProject);

    myRefreshRequestsQueue = new MergingUpdateQueue("ExternalSystemProjectsWatcher: Refresh requests queue",
                                                    REFRESH_MERGING_TIME_SPAN, false, ANY_COMPONENT, myProject, null, false);

    myImportAwareManagers = ContainerUtil.newArrayList();
    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      if (manager instanceof ExternalSystemAutoImportAware) {
        myImportAwareManagers.add((ExternalSystemAutoImportAware)manager);

        NotificationsConfiguration.getNotificationsConfiguration().register(
          manager.getSystemId().getReadableName() + " Import", NotificationDisplayType.STICKY_BALLOON, false);
      }
    }

    myUpdatesQueue =
      new MergingUpdateQueue("ExternalSystemProjectsWatcher: Notifier queue", 500, false, ANY_COMPONENT, myProject);

    myNotificationMap = ContainerUtil.newConcurrentMap();

    ApplicationManager.getApplication().getMessageBus().connect(myProject)
      .subscribe(BatchFileChangeListener.TOPIC, new BatchFileChangeListener() {
        @Override
        public void batchChangeStarted(@NotNull Project project, @Nullable String activityName) {
          myRefreshRequestsQueue.suspend();
        }

        @Override
        public void batchChangeCompleted(@NotNull Project project) {
          myRefreshRequestsQueue.resume();
        }
      });
  }

  @Override
  public void markDirtyAllExternalProjects() {
    findLinkedProjectsSettings().forEach(this::scheduleUpdate);
    for (Contributor contributor : EP_NAME.getExtensions()) {
      contributor.markDirtyAllExternalProjects(myProject);
    }
  }

  @Override
  public void markDirty(Module module) {
    scheduleUpdate(ExternalSystemApiUtil.getExternalProjectPath(module));
    for (Contributor contributor : EP_NAME.getExtensions()) {
      contributor.markDirty(module);
    }
  }

  @Override
  public void markDirty(String projectPath) {
    scheduleUpdate(projectPath);
  }

  public synchronized void start() {
    if (ExternalSystemUtil.isNoBackgroundMode()) {
      return;
    }
    myUpdatesQueue.activate();
    final MessageBusConnection myBusConnection = myProject.getMessageBus().connect(myChangedDocumentsQueue);
    myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new MyFileChangeListener(this));

    makeUserAware(myChangedDocumentsQueue, myProject);
    myChangedDocumentsQueue.activate();
    myRefreshRequestsQueue.activate();

    DocumentListener myDocumentListener = new DocumentListener() {
      private final Map<Document, Pair<String, VirtualFile>> myChangedDocuments = new THashMap<>();

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        Document doc = event.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
        if (file == null) return;
        String externalProjectPath = getRelatedExternalProjectPath(file);
        if (externalProjectPath == null) return;

        synchronized (myChangedDocuments) {
          myChangedDocuments.put(doc, Pair.create(externalProjectPath, file));
        }
        myChangedDocumentsQueue.queue(new Update(ExternalSystemProjectsWatcherImpl.this) {
          @Override
          public void run() {
            final Map<Document, Pair<String, VirtualFile>> copy;

            synchronized (myChangedDocuments) {
              copy = new THashMap<>(myChangedDocuments);
              myChangedDocuments.clear();
            }

            ExternalSystemUtil.invokeLater(myProject, () -> WriteAction.run(
              () -> copy.forEach((document, pair) -> {
                if (!pair.second.isValid()) return;

                PsiDocumentManager.getInstance(myProject).commitDocument(document);
                FileDocumentManagerImpl fileDocumentManager = (FileDocumentManagerImpl)FileDocumentManager.getInstance();
                fileDocumentManager.saveDocumentAsIs(document);
                Long beforeImport = pair.second.getUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT);
                Long current = pair.second.getUserData(CRC_WITHOUT_SPACES_CURRENT);
                if (current != null && current.equals(beforeImport)) {
                  Long newCrc = calculateCrc(pair.second);
                  pair.second.putUserData(CRC_WITHOUT_SPACES_CURRENT, newCrc);
                  if (!current.equals(newCrc)) {
                    scheduleUpdate(pair.first, false);
                  }
                }
              })
            ));
          }
        });
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, myBusConnection);
    ServiceManager.getService(ExternalSystemProgressNotificationManager.class).addNotificationListener(this);

    updateWatchedRoots(true);
    Disposer.register(myChangedDocumentsQueue, () -> myFilesPointers.clear());
  }

  public synchronized void stop() {
    Disposer.dispose(myChangedDocumentsQueue);
    Disposer.dispose(myUpdatesQueue);
    Disposer.dispose(myRefreshRequestsQueue);
    myNotificationMap.clear();
    ServiceManager.getService(ExternalSystemProgressNotificationManager.class).removeNotificationListener(this);
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    if (id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      final ProjectSystemId systemId = id.getProjectSystemId();
      for (String filePath : ContainerUtil.newArrayList(myKnownAffectedFiles.get(workingDir))) {
        VirtualFile file = VfsUtil.findFileByIoFile(new File(filePath), false);
        if (file != null && !file.isDirectory()) {
          file.putUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT, file.getUserData(CRC_WITHOUT_SPACES_CURRENT));
        }
      }

      myUpdatesQueue.queue(new Update(Pair.create(systemId, workingDir)) {
        @Override
        public void run() {
          doUpdateNotifications(true, systemId, workingDir);
        }
      });
    }
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
    if (id.getType() == ExternalSystemTaskType.RESOLVE_PROJECT) {
      updateWatchedRoots(false);
    }
  }

  private void scheduleUpdate(@Nullable String projectPath) {
    scheduleUpdate(projectPath, true);
  }

  private void scheduleUpdate(@Nullable String projectPath, boolean reportRefreshError) {
    if (projectPath == null || ExternalSystemUtil.isNoBackgroundMode()) {
      return;
    }
    Pair<ExternalSystemManager, ExternalProjectSettings> linkedProject = findLinkedProjectSettings(projectPath);
    if (linkedProject == null) return;
    scheduleUpdate(linkedProject, reportRefreshError);
  }

  private void scheduleUpdate(@NotNull Pair<ExternalSystemManager, ExternalProjectSettings> linkedProject) {
    scheduleUpdate(linkedProject, true);
  }

  private void scheduleUpdate(@NotNull Pair<ExternalSystemManager, ExternalProjectSettings> linkedProject, boolean reportRefreshError) {
    if (ExternalSystemUtil.isNoBackgroundMode()) {
      return;
    }
    ExternalSystemManager<?, ?, ?, ?, ?> manager = linkedProject.first;
    String projectPath = linkedProject.second.getExternalProjectPath();
    ProjectSystemId systemId = manager.getSystemId();
    boolean useAutoImport = linkedProject.second.isUseAutoImport();

    if (useAutoImport) {
      final ExternalSystemTask resolveTask = ServiceManager.getService(ExternalSystemProcessingManager.class)
        .findTask(ExternalSystemTaskType.RESOLVE_PROJECT, systemId, projectPath);
      final ExternalSystemTaskState taskState = resolveTask == null ? null : resolveTask.getState();
      if (taskState == null || taskState.isStopped()) {
        addToRefreshQueue(projectPath, systemId, reportRefreshError);
      }
      else if (taskState != ExternalSystemTaskState.NOT_STARTED) {
        if (manager instanceof ExternalSystemAutoImportAware) {
          Long lastTimestamp =
            (Long)manager.getLocalSettingsProvider().fun(myProject).getExternalConfigModificationStamps().get(projectPath);
          if (lastTimestamp != null) {
            List<File> affectedFiles = ((ExternalSystemAutoImportAware)manager).getAffectedExternalProjectFiles(projectPath, myProject);
            long currentTimeStamp = affectedFiles.stream().mapToLong(File::lastModified).sum();
            if (lastTimestamp != currentTimeStamp) {
              return;
            }
          }
        }
        // re-schedule to wait for the active project import task end
        final ExternalSystemProgressNotificationManager progressManager =
          ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
        final ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {
          @Override
          public void onEnd(@NotNull ExternalSystemTaskId id) {
            progressManager.removeNotificationListener(this);
            addToRefreshQueue(projectPath, systemId, reportRefreshError);
          }
        };
        progressManager.addNotificationListener(resolveTask.getId(), taskListener);
      }
    }
    else {
      LOG.debug("Scheduling new external project update notification", new Throwable("Schedule update call trace"));
      myUpdatesQueue.queue(new Update(Pair.create(systemId, projectPath)) {
        @Override
        public void run() {
          doUpdateNotifications(false, systemId, projectPath);
        }
      });
    }
  }

  private void addToRefreshQueue(String projectPath, ProjectSystemId systemId, boolean reportRefreshError) {
    myRefreshRequestsQueue.queue(new Update(Pair.create(systemId, projectPath)) {
      @Override
      public void run() {
        scheduleRefresh(myProject, projectPath, systemId, reportRefreshError);
      }
    });
  }

  private void updateWatchedRoots(boolean isProjectOpen) {
    List<String> pathsToWatch = new SmartList<>();
    myFilesPointers.clear();
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchedRoots);
    Map<String, VirtualFilePointer> pointerMap = ContainerUtil.newHashMap();

    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      if (!(manager instanceof ExternalSystemAutoImportAware)) continue;
      ExternalSystemAutoImportAware importAware = (ExternalSystemAutoImportAware)manager;
      for (ExternalProjectSettings settings : manager.getSettingsProvider().fun(myProject).getLinkedProjectsSettings()) {
        List<File> files = importAware.getAffectedExternalProjectFiles(settings.getExternalProjectPath(), myProject);
        long timeStamp = 0;
        for (File file : files) {
          timeStamp += file.lastModified();
        }
        Map<String, Long> modificationStamps = manager.getLocalSettingsProvider().fun(myProject).getExternalConfigModificationStamps();
        if (isProjectOpen &&
            myProject.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) != Boolean.TRUE &&
            myProject.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) != Boolean.TRUE) {
          Long affectedFilesTimestamp = modificationStamps.get(settings.getExternalProjectPath());
          affectedFilesTimestamp = affectedFilesTimestamp == null ? -1L : affectedFilesTimestamp;
          if (timeStamp != affectedFilesTimestamp) {
            scheduleUpdate(settings.getExternalProjectPath());
          }
        }
        modificationStamps.put(settings.getExternalProjectPath(), timeStamp);

        for (File file : files) {
          if (file == null) continue;
          String path = getNormalizedPath(file);
          if (path == null) continue;

          pathsToWatch.add(path);
          String url = VfsUtilCore.pathToUrl(path);
          VirtualFilePointer pointer = pointerMap.get(url);
          if (pointer == null) {
            pointer = VirtualFilePointerManager.getInstance().create(url, myChangedDocumentsQueue, null);
            pointerMap.put(url, pointer);

            // update timestamps based on file crc and local settings
            final VirtualFile virtualFile = pointer.getFile();
            if (virtualFile != null) {
              Long crc = virtualFile.getUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT);
              if (crc != null) {
                modificationStamps.put(path, crc);
              }
              else {
                UIUtil.invokeLaterIfNeeded(() -> {
                  Long newCrc = calculateCrc(virtualFile);
                  virtualFile.putUserData(CRC_WITHOUT_SPACES_CURRENT, newCrc);
                  virtualFile.putUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT, newCrc);
                  modificationStamps.put(path, newCrc);
                });
              }
            }
          }
          myFilesPointers.putValue(pointer, settings.getExternalProjectPath());
        }
      }
    }
    myWatchedRoots.addAll(LocalFileSystem.getInstance().addRootsToWatch(pathsToWatch, false));
  }

  @Nullable
  private String getRelatedExternalProjectPath(VirtualFile file) {
    String path = file.getPath();
    return getRelatedExternalProjectPath(path);
  }

  @Nullable
  private String getRelatedExternalProjectPath(String path) {
    String externalProjectPath = null;
    for (ExternalSystemAutoImportAware importAware : myImportAwareManagers) {
      externalProjectPath = importAware.getAffectedExternalProjectPath(path, myProject);
      if (externalProjectPath != null) {
        break;
      }
    }
    if (externalProjectPath != null) {
      myKnownAffectedFiles.putValue(externalProjectPath, path);
    }
    return externalProjectPath;
  }

  private void doUpdateNotifications(boolean close, @NotNull ProjectSystemId systemId, @NotNull String projectPath) {
    MyNotification notification = myNotificationMap.get(systemId);
    if (close) {
      if (notification == null) return;
      notification.projectPaths.remove(projectPath);
      if (notification.projectPaths.isEmpty()) {
        notification.expire();
      }
    }
    else {
      if (notification != null && !notification.isExpired()) {
        notification.projectPaths.add(projectPath);
        return;
      }
      notification = new MyNotification(myProject, myNotificationMap, systemId, projectPath);
      myNotificationMap.put(systemId, notification);
      Notifications.Bus.notify(notification, myProject);
    }
  }

  private static void scheduleRefresh(@NotNull final Project project,
                                      String projectPath,
                                      ProjectSystemId systemId,
                                      boolean reportRefreshError) {
    ExternalSystemUtil.refreshProject(
      project, systemId, projectPath, new ExternalProjectRefreshCallback() {
        @Override
        public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
          if (externalProject != null) {
            ServiceManager.getService(ProjectDataManager.class).importData(externalProject, project, true);
          }
        }
      }, false, ProgressExecutionMode.IN_BACKGROUND_ASYNC, reportRefreshError);
  }

  private static void makeUserAware(final MergingUpdateQueue mergingUpdateQueue, final Project project) {
    ApplicationManager.getApplication().runReadAction(() -> {
      EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

      multicaster.addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
          mergingUpdateQueue.restartTimer();
        }
      }, mergingUpdateQueue);

      multicaster.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          mergingUpdateQueue.restartTimer();
        }
      }, mergingUpdateQueue);

      project.getMessageBus().connect(mergingUpdateQueue).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        int beforeCalled;

        @Override
        public void beforeRootsChange(@NotNull ModuleRootEvent event) {
          if (beforeCalled++ == 0) {
            mergingUpdateQueue.suspend();
          }
        }

        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          if (beforeCalled == 0) {
            return; // This may occur if listener has been added between beforeRootsChange() and rootsChanged() calls.
          }

          if (--beforeCalled == 0) {
            mergingUpdateQueue.resume();
            mergingUpdateQueue.restartTimer();
          }
        }
      });
    });
  }

  private static class MyNotification extends Notification {

    private final ProjectSystemId mySystemId;
    private final Map<ProjectSystemId, MyNotification> myNotificationMap;
    private final Set<String> projectPaths;

    MyNotification(Project project,
                          Map<ProjectSystemId, MyNotification> notificationMap,
                          ProjectSystemId systemId,
                          String projectPath) {
      super(systemId.getReadableName() + " Import",
            ExternalSystemBundle.message("import.needed", systemId.getReadableName()), "",
            NotificationType.INFORMATION, null);
      mySystemId = systemId;
      myNotificationMap = notificationMap;
      projectPaths = ContainerUtil.newHashSet(projectPath);
      addAction(new NotificationAction(ExternalSystemBundle.message("import.importChanged")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          doAction(notification, project, systemId, false);
        }
      });
      addAction(new NotificationAction(ExternalSystemBundle.message("import.enableAutoImport")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          doAction(notification, project, systemId, true);
        }
      });
    }

    @Override
    public void expire() {
      super.expire();
      projectPaths.clear();
      myNotificationMap.remove(mySystemId);
    }

    private void doAction(@NotNull Notification notification,
                          Project project,
                          ProjectSystemId systemId,
                          boolean isAutoImport) {
      projectPaths.stream()
        .map(path -> ExternalSystemApiUtil.getSettings(project, systemId).getLinkedProjectSettings(path))
        .distinct()
        .filter(Objects::nonNull)
        .forEach(settings -> {
          if (isAutoImport) {
            settings.setUseAutoImport(true);
          }
          scheduleRefresh(project, settings.getExternalProjectPath(), systemId, !isAutoImport);
        });
      notification.expire();
    }
  }

  private class MyFileChangeListener extends FileChangeListenerBase {
    private final ExternalSystemProjectsWatcherImpl myWatcher;
    private final MultiMap<String/* file path */, String /* project path */> myKnownFiles = MultiMap.createSet();
    private List<VirtualFile> filesToUpdate;
    private List<VirtualFile> filesToRemove;

    MyFileChangeListener(ExternalSystemProjectsWatcherImpl watcher) {
      myWatcher = watcher;
    }

    @Override
    protected boolean isRelevant(String path) {
      if (!myKnownFiles.get(path).isEmpty()) return true;

      for (VirtualFilePointer pointer : myFilesPointers.keySet()) {
        String filePath = VfsUtilCore.urlToPath(pointer.getUrl());
        if (StringUtil.isNotEmpty(filePath) && FileUtil.namesEqual(path, filePath)) {
          for (String projectPath : myFilesPointers.get(pointer)) {
            myKnownFiles.putValue(path, projectPath);
            myKnownAffectedFiles.putValue(projectPath, path);
          }
          return true;
        }
        else if (LOG.isDebugEnabled()) {
          if (StringUtil.isNotEmpty(filePath) && FileUtil.namesEqual(FileUtil.toCanonicalPath(path), FileUtil.toCanonicalPath(filePath))) {
            LOG.debug("Changes were ignored: " + path + ". Related FW: " + filePath);
          }
        }
      }
      String affectedProjectPath = getRelatedExternalProjectPath(path);
      if (affectedProjectPath != null) {
        myKnownFiles.putValue(path, affectedProjectPath);
      }
      return affectedProjectPath != null;
    }

    @Override
    protected void updateFile(VirtualFile file, VFileEvent event) {
      doUpdateFile(file, event, false);
    }

    @Override
    protected void deleteFile(VirtualFile file, VFileEvent event) {
      doUpdateFile(file, event, true);
    }

    private void doUpdateFile(VirtualFile file, VFileEvent event, boolean remove) {
      init();
      if (remove) {
        filesToRemove.add(file);
      }
      else {
        if (fileWasChanged(file, event)) {
          filesToUpdate.add(file);
        }
        else {
          for (String externalProjectPath : myKnownFiles.get(file.getPath())) {
            handleRevertedChanges(externalProjectPath);
          }
        }
      }
    }

    private void handleRevertedChanges(final String externalProjectPath) {
      for (String filePath : ContainerUtil.newArrayList(myKnownAffectedFiles.get(externalProjectPath))) {
        VirtualFile f = VfsUtil.findFileByIoFile(new File(filePath), false);
        if (f == null ||
            !Objects.equals(f.getUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT), f.getUserData(CRC_WITHOUT_SPACES_CURRENT))) {
          return;
        }
      }

      ProjectSystemId systemId = null;
      for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
        if (manager.getSettingsProvider().fun(myProject).getLinkedProjectSettings(externalProjectPath) != null) {
          systemId = manager.getSystemId();
        }
      }

      if (systemId != null) {
        ProjectSystemId finalSystemId = systemId;
        myUpdatesQueue.queue(new Update(Pair.create(finalSystemId, externalProjectPath)) {
          @Override
          public void run() {
            doUpdateNotifications(true, finalSystemId, externalProjectPath);
          }
        });
      }
    }

    private boolean fileWasChanged(VirtualFile file, VFileEvent event) {
      if (!file.isValid()) {
        return true;
      }
      if (!(event instanceof VFileContentChangeEvent)) {
        return false;
      }

      Long newCrc = calculateCrc(file);
      file.putUserData(CRC_WITHOUT_SPACES_CURRENT, newCrc);

      Long crc = file.getUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT);
      if (crc == null) {
        file.putUserData(CRC_WITHOUT_SPACES_BEFORE_LAST_IMPORT, newCrc);
        return true;
      }
      return !newCrc.equals(crc);
    }

    @Override
    protected void apply() {
      // the save may occur during project close. in this case the background task
      // can not be started since the window has already been closed.
      if (areFileSetsInitialised()) {
        filesToUpdate.removeAll(filesToRemove);
        scheduleUpdate(ContainerUtil.concat(filesToUpdate, filesToRemove));
      }
      clear();
    }

    private boolean areFileSetsInitialised() {
      return filesToUpdate != null;
    }

    private void scheduleUpdate(List<VirtualFile> filesToUpdate) {
      filesToUpdate.stream()
        .flatMap(f -> myKnownFiles.get(f.getPath()).stream())
        .distinct()
        .forEach(path -> myWatcher.scheduleUpdate(path, false));
    }

    private void init() {
      // Do not use before() method to initialize the lists
      // since the listener can be attached during the update
      // and before method can be skipped.
      // The better way to fix if, of course, is to do something with
      // subscription - add listener not during postStartupActivity
      // but on project initialization to avoid this situation.
      if (areFileSetsInitialised()) return;

      filesToUpdate = new ArrayList<>();
      filesToRemove = new ArrayList<>();
    }

    private void clear() {
      filesToUpdate = null;
      filesToRemove = null;
      myKnownFiles.clear();
    }
  }

  @Nullable
  private Pair<ExternalSystemManager, ExternalProjectSettings> findLinkedProjectSettings(String projectPath) {
    final ExternalProjectSettings[] linkedProjectSettings = new ExternalProjectSettings[1];
    Optional<ExternalSystemManager<?, ?, ?, ?, ?>> systemManager = ExternalSystemApiUtil.getAllManagers().stream()
      .filter(m -> {
        linkedProjectSettings[0] = m.getSettingsProvider().fun(myProject).getLinkedProjectSettings(projectPath);
        return linkedProjectSettings[0] != null;
      }).findAny();

    if (!systemManager.isPresent()) return null;
    ExternalSystemManager<?, ?, ?, ?, ?> manager = systemManager.get();
    return Pair.create(manager, linkedProjectSettings[0]);
  }

  @NotNull
  private List<Pair<ExternalSystemManager, ExternalProjectSettings>> findLinkedProjectsSettings() {
    return ExternalSystemApiUtil.getAllManagers().stream()
      .flatMap(
        manager -> manager.getSettingsProvider().fun(myProject).getLinkedProjectsSettings().stream()
          .map(settings -> Pair.create((ExternalSystemManager)manager, (ExternalProjectSettings)settings)))
      .collect(Collectors.toList());
  }

  @Nullable
  private static String getNormalizedPath(@NotNull File file) {
    String canonized = PathUtil.getCanonicalPath(file.getAbsolutePath());
    return canonized == null ? null : FileUtil.toSystemIndependentName(canonized);
  }

  @NotNull
  private Long calculateCrc(@NotNull VirtualFile file) {
    return new ConfigurationFileCrcFactory(myProject, file).create();
  }

  @ApiStatus.Experimental
  public interface Contributor {

    void markDirtyAllExternalProjects(@NotNull Project project);

    void markDirty(@NotNull Module module);
  }
}
