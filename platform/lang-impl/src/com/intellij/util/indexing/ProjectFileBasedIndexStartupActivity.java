// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

final class ProjectFileBasedIndexStartupActivity implements StartupActivity.RequiredForSmartMode {
  ProjectFileBasedIndexStartupActivity() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        removeProjectIndexableSet(project);
      }
    });
  }

  @Override
  public void runActivity(@NotNull Project project) {
    ProgressManager.progress(IndexingBundle.message("progress.text.loading.indexes"));
    FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(project);
    if (propertiesUpdater instanceof PushedFilePropertiesUpdaterImpl) {
      ((PushedFilePropertiesUpdaterImpl)propertiesUpdater).initializeProperties();
    }

    fileBasedIndex.registerProjectFileSets(project);

    // load indexes while in dumb mode, otherwise someone from read action may hit `FileBasedIndex.getIndex` and hang (IDEA-316697)
    fileBasedIndex.loadIndexes();
    fileBasedIndex.waitUntilIndicesAreInitialized();

    // schedule dumb mode start after the read action we're currently in
    boolean suspended = IndexInfrastructure.isIndexesInitializationSuspended();
    UnindexedFilesScanner.scanAndIndexProjectAfterOpen(project, suspended, "On project open");

    // done mostly for tests. In real life this is no-op, because the set was removed on project closing
    Disposer.register(project, () -> removeProjectIndexableSet(project));
  }

  private static void removeProjectIndexableSet(@NotNull Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ReadAction.run(() -> FileBasedIndex.getInstance().removeProjectFileSets(project));
    }, IndexingBundle.message("removing.indexable.set.project.handler"), false, project);
  }
}
