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
package com.intellij.openapi.externalSystem.service.project.autoimport;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 6:38 PM
 */
public class ExternalSystemAutoImporter implements BulkFileListener, DocumentListener {

  @NotNull private final ConcurrentMap<ProjectSystemId, Set<String /* external project path */>> myFilesToRefresh
    = ContainerUtil.newConcurrentMap();

  @NotNull private final Alarm         myVfsAlarm;
  @NotNull private final ReadWriteLock myVfsLock  = new ReentrantReadWriteLock();

  @NotNull private final Set<Document> myDocumentsToSave = ContainerUtilRt.newHashSet();
  @NotNull private final Alarm         myDocumentAlarm;
  @NotNull private final ReadWriteLock myDocumentLock    = new ReentrantReadWriteLock();

  @NotNull private final Runnable                       myFilesRequest         = new Runnable() {
    @Override
    public void run() {
      refreshFilesIfNecessary();
    }
  };
  @NotNull private final Runnable                       myDocumentsSaveRequest = new Runnable() {
    @Override
    public void run() {
      saveDocumentsIfNecessary();
    }
  };
  @NotNull private final ExternalProjectRefreshCallback myRefreshCallback      = new ExternalProjectRefreshCallback() {
    @Override
    public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
      if (externalProject != null) {
        myProjectDataManager.importData(externalProject, myProject, true);
      }
    }

    @Override
    public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
      // Do nothing. 
    }
  };

  @NotNull private final Project            myProject;
  @NotNull private final ProjectDataManager myProjectDataManager;

  @NotNull private final MyEntry[] myAutoImportAware;

  public ExternalSystemAutoImporter(@NotNull Project project,
                                    @NotNull ProjectDataManager projectDataManager,
                                    @NotNull MyEntry[] autoImportAware)
  {
    myProject = project;
    myProjectDataManager = projectDataManager;
    myAutoImportAware = autoImportAware;
    myVfsAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
    myDocumentAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, project);
  }

  @SuppressWarnings("unchecked")
  public static void letTheMagicBegin(@NotNull Project project) {
    List<MyEntry> autoImportAware = ContainerUtilRt.newArrayList();
    Collection<ExternalSystemManager<?, ?, ?, ?, ?>> managers = ExternalSystemApiUtil.getAllManagers();
    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : managers) {
      AbstractExternalSystemSettings<?, ?, ?> systemSettings = manager.getSettingsProvider().fun(project);
      ExternalSystemAutoImportAware defaultImportAware = createDefault(systemSettings);
      final ExternalSystemAutoImportAware aware;
      if (manager instanceof ExternalSystemAutoImportAware) {
        aware = combine(defaultImportAware, (ExternalSystemAutoImportAware)manager);
      }
      else {
        aware = defaultImportAware;
      }
      autoImportAware.add(new MyEntry(manager.getSystemId(), systemSettings, aware));
    }

    MyEntry[] entries = autoImportAware.toArray(new MyEntry[autoImportAware.size()]);
    ExternalSystemAutoImporter autoImporter = new ExternalSystemAutoImporter(
      project,
      ServiceManager.getService(ProjectDataManager.class),
      entries
    );
    final MessageBus messageBus = project.getMessageBus();
    messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, autoImporter);

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(autoImporter, project);
  }

  @NotNull
  private static ExternalSystemAutoImportAware combine(@NotNull final ExternalSystemAutoImportAware aware1,
                                                       @NotNull final ExternalSystemAutoImportAware aware2)
  {
    return new ExternalSystemAutoImportAware() {
      @Nullable
      @Override
      public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
        String projectPath = aware1.getAffectedExternalProjectPath(changedFileOrDirPath, project);
        return projectPath == null ? aware2.getAffectedExternalProjectPath(changedFileOrDirPath, project) : projectPath;
      }
    };
  }

  @NotNull
  private static ExternalSystemAutoImportAware createDefault(@NotNull final AbstractExternalSystemSettings<?, ?, ?> systemSettings) {
    return new ExternalSystemAutoImportAware() {
      @Nullable
      @Override
      public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
        return systemSettings.getLinkedProjectSettings(changedFileOrDirPath) == null ? null : changedFileOrDirPath;
      }
    };
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    Document document = event.getDocument();
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile file = fileDocumentManager.getFile(document);
    if (file == null) {
      return;
    }

    String path = ExternalSystemApiUtil.getLocalFileSystemPath(file);
    for (MyEntry entry : myAutoImportAware) {
      if (entry.aware.getAffectedExternalProjectPath(path, myProject) != null) {
        // Document save triggers VFS event but FileDocumentManager might be registered after the current listener, that's why
        // call to 'saveDocument()' might not produce the desired effect. That's why we reschedule document save if necessary.
        scheduleDocumentSave(document);
        return;
      }
    } 
  }

  private void scheduleDocumentSave(@NotNull Document document) {
    Lock lock = myDocumentLock.readLock();
    lock.lock();
    try {
      myDocumentsToSave.add(document);
      if (myDocumentAlarm.getActiveRequestCount() <= 0) {
        myDocumentAlarm.addRequest(myDocumentsSaveRequest, 100);
      }
    }
    finally {
      lock.unlock();
    }
  }

  private void saveDocumentsIfNecessary() {
    if(ApplicationManager.getApplication().isDisposed()) return;

    final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Lock lock = myDocumentLock.writeLock();
    Set<Document> toKeep = ContainerUtilRt.newHashSet();
    final Set<Document> toSave = ContainerUtilRt.newHashSet();
    lock.lock();
    try {
      myDocumentAlarm.cancelAllRequests();
      for (Document document : myDocumentsToSave) {
        if (fileDocumentManager.isDocumentUnsaved(document)) {
          toSave.add(document);
        }
        else {
          toKeep.add(document);
        }
      }
      myDocumentsToSave.clear();
      if (!toSave.isEmpty()) {
        invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            for (Document document : toSave) {
              fileDocumentManager.saveDocument(document);
            }
          }
        });
      }
      if (!toKeep.isEmpty()) {
        myDocumentsToSave.addAll(toKeep);
        myDocumentAlarm.addRequest(myDocumentsSaveRequest, 100);
      }
    }
    finally {
      lock.unlock();
    }
  }

  private static void invokeLaterIfNeeded(Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    boolean scheduleRefresh = false;
    for (VFileEvent event : events) {
      String changedPath = event.getPath();
      for (MyEntry entry : myAutoImportAware) {
        String projectPath = entry.aware.getAffectedExternalProjectPath(changedPath, myProject);
        if (projectPath == null) {
          continue;
        }
        ExternalProjectSettings projectSettings = entry.systemSettings.getLinkedProjectSettings(projectPath);
        if (projectSettings != null && projectSettings.isUseAutoImport()) {
          addPath(entry.externalSystemId, projectPath);
          scheduleRefresh = true;
          break;
        }
      }
    }
    if (scheduleRefresh) {
      myVfsAlarm.cancelAllRequests();
      myVfsAlarm.addRequest(myFilesRequest, ExternalSystemConstants.AUTO_IMPORT_DELAY_MILLIS);
    }
  }

  private void addPath(@NotNull ProjectSystemId externalSystemId, @NotNull String path) {
    Lock lock = myVfsLock.readLock();
    lock.lock();
    try {
      Set<String> paths = myFilesToRefresh.get(externalSystemId);
      while (paths == null) {
        myFilesToRefresh.putIfAbsent(externalSystemId, ContainerUtilRt.<String>newHashSet());
        paths = myFilesToRefresh.get(externalSystemId);
      }
      paths.add(path);
    }
    finally {
      lock.unlock();
    }
  }

  private void refreshFilesIfNecessary() {
    if (myFilesToRefresh.isEmpty() || myProject.isDisposed()) {
      return;
    }

    Map<ProjectSystemId, Set<String>> copy = ContainerUtilRt.newHashMap();
    Lock fileLock = myVfsLock.writeLock();
    fileLock.lock();
    try {
      copy.putAll(myFilesToRefresh);
      myFilesToRefresh.clear();
    }
    finally {
      fileLock.unlock();
    }
    
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    Lock documentLock = myDocumentLock.writeLock();
    documentLock.lock();
    try {
      for (Set<String> paths : copy.values()) {
        for (String path : paths) {
          VirtualFile file = fileSystem.findFileByPath(path);
          if (file != null) {
            Document document = fileDocumentManager.getCachedDocument(file);
            if (document != null) {
              myDocumentsToSave.remove(document);
            }
          }
        }
      }
    }
    finally {
      documentLock.unlock();
    }

    boolean scheduleRefresh = false;
    ExternalSystemProcessingManager processingManager = ServiceManager.getService(ExternalSystemProcessingManager.class);
    for (Map.Entry<ProjectSystemId, Set<String>> entry : copy.entrySet()) {
      for (String path : entry.getValue()) {
        final ExternalSystemTask resolveTask = processingManager.findTask(ExternalSystemTaskType.RESOLVE_PROJECT, entry.getKey(), path);
        final ExternalSystemTaskState taskState = resolveTask == null ? null : resolveTask.getState();
        if (taskState == null || taskState.isStopped()) {
          ExternalSystemUtil.refreshProject(
            myProject, entry.getKey(), path, myRefreshCallback, false, ProgressExecutionMode.IN_BACKGROUND_ASYNC, false);
        }
        else if (taskState != ExternalSystemTaskState.NOT_STARTED) {
          // re-schedule to wait for the project import task end
          scheduleRefresh = true;
          addPath(entry.getKey(), path);
        }
      }
    }

    if (scheduleRefresh) {
      myVfsAlarm.cancelAllRequests();
      myVfsAlarm.addRequest(myFilesRequest, ExternalSystemConstants.AUTO_IMPORT_DELAY_MILLIS);
    }
  }
  
  private static class MyEntry {

    @NotNull public final ProjectSystemId                         externalSystemId;
    @NotNull public final AbstractExternalSystemSettings<?, ?, ?> systemSettings;
    @NotNull public final ExternalSystemAutoImportAware           aware;

    MyEntry(@NotNull ProjectSystemId externalSystemId,
            @NotNull AbstractExternalSystemSettings<?, ?, ?> systemSettings,
            @NotNull ExternalSystemAutoImportAware aware)
    {
      this.externalSystemId = externalSystemId;
      this.systemSettings = systemSettings;
      this.aware = aware;
    }
  }
}
