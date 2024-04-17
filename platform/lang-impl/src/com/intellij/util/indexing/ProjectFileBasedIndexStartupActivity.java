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
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.indexing.UnindexedFilesScannerStartupKt.scanAndIndexProjectAfterOpen;

final class ProjectFileBasedIndexStartupActivity implements StartupActivity.RequiredForSmartMode {
  private final ConcurrentList<Project> myOpenProjects = ContainerUtil.createConcurrentList();
  private final CoroutineScope myCoroutineScope;

  ProjectFileBasedIndexStartupActivity(@NotNull CoroutineScope coroutineScope) {
    myCoroutineScope = coroutineScope;
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        onProjectClosing(project);
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

    // Add project to various lists in read action to make sure that
    // they are not added to lists during disposing of project (in this case project may be stuck forever in those lists)
    boolean registered = ReadAction.compute(() -> {
      if (project.isDisposed()) return false;
      // done mostly for tests. In real life this is no-op, because the set was removed on project closing
      // note that disposing happens in write action, so it'll be executed after this read action
      Disposer.register(project, () -> onProjectClosing(project));

      fileBasedIndex.registerProjectFileSets(project);
      fileBasedIndex.getIndexableFilesFilterHolder().onProjectOpened(project);

      myOpenProjects.add(project);
      return true;
    });

    if (!registered) return;

    // load indexes while in dumb mode, otherwise someone from read action may hit `FileBasedIndex.getIndex` and hang (IDEA-316697)
    fileBasedIndex.loadIndexes();
    fileBasedIndex.waitUntilIndicesAreInitialized();

    // schedule dumb mode start after the read action we're currently in
    boolean suspended = IndexInfrastructure.isIndexesInitializationSuspended();
    scanAndIndexProjectAfterOpen(project, suspended, true, myCoroutineScope, "On project open");
  }

  private void onProjectClosing(@NotNull Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ReadAction.run(() -> {
        if (myOpenProjects.remove(project)) {
          FileBasedIndex.getInstance().onProjectClosing(project);
        }
      });
    }, IndexingBundle.message("removing.indexable.set.project.handler"), false, project);
  }
}
