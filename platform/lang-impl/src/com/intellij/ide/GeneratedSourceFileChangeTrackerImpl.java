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
package com.intellij.ide;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class GeneratedSourceFileChangeTrackerImpl extends GeneratedSourceFileChangeTracker {
  private final FileDocumentManager myDocumentManager;
  private final EditorNotifications myEditorNotifications;
  private final MergingUpdateQueue myCheckingQueue;
  private final Set<VirtualFile> myFilesToCheck = Collections.synchronizedSet(new HashSet<VirtualFile>());
  private final Set<VirtualFile> myEditedGeneratedFiles = Collections.synchronizedSet(new HashSet<VirtualFile>());

  public GeneratedSourceFileChangeTrackerImpl(Project project, FileDocumentManager documentManager, EditorNotifications editorNotifications) {
    super(project);
    myDocumentManager = documentManager;
    myEditorNotifications = editorNotifications;
    myCheckingQueue = new MergingUpdateQueue("Checking for changes in generated sources", 500, false, null, project, null, Alarm.ThreadToUse.SHARED_THREAD);
  }

  @Override
  public boolean isEditedGeneratedFile(@NotNull VirtualFile file) {
    return myEditedGeneratedFiles.contains(file);
  }

  @Override
  public void projectOpened() {
    final Update check = new Update("check for changes in generated files") {
      @Override
      public void run() {
        checkFiles();
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        VirtualFile file = myDocumentManager.getFile(e.getDocument());
        if (file != null) {
          myFilesToCheck.add(file);
          myCheckingQueue.queue(check);
        }
      }
    }, myProject);
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        myFilesToCheck.remove(file);
        if (myEditedGeneratedFiles.remove(file)) {
          myEditorNotifications.updateNotifications(file);
        }
      }
    });
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        myEditedGeneratedFiles.remove(file);
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        myFilesToCheck.addAll(myEditedGeneratedFiles);
        myEditedGeneratedFiles.clear();
        myCheckingQueue.queue(check);
      }
    });
    myCheckingQueue.activate();
  }

  @Override
  public void projectClosed() {
    myCheckingQueue.deactivate();
  }

  private void checkFiles() {
    final VirtualFile[] files;
    synchronized (myFilesToCheck) {
      files = myFilesToCheck.toArray(new VirtualFile[myFilesToCheck.size()]);
      myFilesToCheck.clear();
    }
    final List<VirtualFile> newEditedGeneratedFiles = new ArrayList<>();
    new ReadAction() {
      protected void run(final @NotNull Result result) {
        if (myProject.isDisposed()) return;
        for (VirtualFile file : files) {
          if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
            newEditedGeneratedFiles.add(file);
          }
        }
      }
    }.execute();

    if (!newEditedGeneratedFiles.isEmpty()) {
      myEditedGeneratedFiles.addAll(newEditedGeneratedFiles);
      myEditorNotifications.updateAllNotifications();
    }
  }
}
