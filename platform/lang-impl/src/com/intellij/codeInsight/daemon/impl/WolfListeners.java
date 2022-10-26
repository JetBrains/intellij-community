// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.CommonProcessors;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class WolfListeners implements Disposable {
  private final Project myProject;
  private final WolfTheProblemSolverImpl myWolfTheProblemSolver;
  private final MergingUpdateQueue invalidateFileQueue = new MergingUpdateQueue("WolfListeners.invalidateFileQueue", 0, true, null, this, null, false);

  WolfListeners(@NotNull Project project, @NotNull WolfTheProblemSolverImpl wolfTheProblemSolver) {
    myProject = project;
    myWolfTheProblemSolver = wolfTheProblemSolver;
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childMoved(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        myWolfTheProblemSolver.clearSyntaxErrorFlag(event);
      }
    }, this);
    var busConnection = project.getMessageBus().simpleConnect();
    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        boolean dirChanged = false;
        Set<VirtualFile> toRemove = new HashSet<>();
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent) {
            VirtualFile file = event.getFile();
            if (file.isDirectory()) {
              dirChanged = true;
            }
            else {
              toRemove.add(file);
            }
          }
        }
        if (dirChanged) {
          clearInvalidFiles();
        }
        for (VirtualFile file : toRemove) {
          myWolfTheProblemSolver.doRemove(file);
        }
      }
    });
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    if (fileStatusManager != null) { //tests?
      fileStatusManager.addFileStatusListener(new FileStatusListener() {
        @Override
        public void fileStatusesChanged() {
          clearInvalidFiles();
        }

        @Override
        public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
          fileStatusesChanged();
        }
      }, this);
    }

    busConnection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // Ensure we don't have any leftover problems referring to classes from plugin being unloaded
        Set<VirtualFile> allFiles = new HashSet<>();
        wolfTheProblemSolver.processProblemFiles(new CommonProcessors.CollectProcessor<>(allFiles));
        wolfTheProblemSolver.processProblemFilesFromExternalSources(new CommonProcessors.CollectProcessor<>(allFiles));
        for (VirtualFile file : allFiles) {
          myWolfTheProblemSolver.doRemove(file);
        }
      }
    });
  }

  private void clearInvalidFiles() {
    myWolfTheProblemSolver.processProblemFiles(file -> {
      invalidateFileQueue.queue(Update.create(file, () -> {
        boolean toRemove = ReadAction.compute(() -> !myProject.isDisposed() && (!file.isValid() || !myWolfTheProblemSolver.isToBeHighlighted(file)));
        if (toRemove) {
          myWolfTheProblemSolver.doRemove(file);
        }
      }));
      return true;
    });
  }

  @Override
  public void dispose() {
  }

  @TestOnly
  void waitForFilesQueuedForInvalidationAreProcessed() {
    try {
      invalidateFileQueue.waitForAllExecuted(1, TimeUnit.MINUTES);
    }
    catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
