// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectFileIndex;
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
    myCheckingQueue = new MergingUpdateQueue("Checking for changes in generated sources", 500, false, null, project, null, Alarm.ThreadToUse.POOLED_THREAD);
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
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (myProject.isDisposed()) return;
        VirtualFile file = myDocumentManager.getFile(e.getDocument());
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
        if (file != null && (fileIndex.isInContent(file) || fileIndex.isInLibrary(file))) {
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
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        myEditedGeneratedFiles.remove(file);
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
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
      @Override
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
