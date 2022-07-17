// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

final class ProjectFileBasedIndexStartupActivity implements StartupActivity.RequiredForSmartMode {
  private static final Logger LOG = Logger.getInstance(ProjectFileBasedIndexStartupActivity.class);

  ProjectFileBasedIndexStartupActivity() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        removeProjectIndexableSet(project);
      }
    });
  }

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isInternal()) {
      project.getMessageBus().connect().subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void exitDumbMode() {
          LOG.info("Has changed files: " + FileBasedIndexProjectHandler.mightHaveManyChangedFilesInProject(project) +
                   "; project=" + project);
        }
      });
    }

    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    PushedFilePropertiesUpdater propertiesUpdater = PushedFilePropertiesUpdater.getInstance(project);
    if (propertiesUpdater instanceof PushedFilePropertiesUpdaterImpl) {
      ((PushedFilePropertiesUpdaterImpl)propertiesUpdater).initializeProperties();
    }

    fileBasedIndex.registerProjectFileSets(project);
    // schedule dumb mode start after the read action we're currently in
    if (fileBasedIndex instanceof FileBasedIndexImpl) {
      boolean suspended = IndexInfrastructure.isIndexesInitializationSuspended();
      UnindexedFilesUpdater.scanAndIndexProjectAfterOpen(project, suspended, "On project open");
    }

    // done mostly for tests. In real life this is no-op, because the set was removed on project closing
    Disposer.register(project, () -> removeProjectIndexableSet(project));
  }

  private static void removeProjectIndexableSet(@NotNull Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ReadAction.run(() -> FileBasedIndex.getInstance().removeProjectFileSets(project));
    }, IndexingBundle.message("removing.indexable.set.project.handler"), false, project);
  }
}
