// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.IndexingProgressReporter.CheckPauseOnlyProgressIndicator;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.ModuleContentOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.getImmediateValuesEx;
import static com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.getModuleImmediateValues;

final class SingleProviderIterator implements ContentIterator {
  private final Project project;
  private final PerProjectIndexingQueue.PerProviderSink perProviderSink;
  private final CheckPauseOnlyProgressIndicator indicator;
  private final List<FilePropertyPusher<?>> pushers;
  private final List<FilePropertyPusherEx<?>> pusherExs;
  private final Object[] moduleValues;
  private final UnindexedFilesFinder unindexedFileFinder;
  private final ScanningStatistics scanningStatistics;
  private final PushedFilePropertiesUpdater pushedFilePropertiesUpdater;
  private final boolean mayBeUsed;

  SingleProviderIterator(Project project, @NotNull CheckPauseOnlyProgressIndicator indicator, IndexableFilesIterator provider,
                         UnindexedFilesFinder unindexedFileFinder, ScanningStatistics scanningStatistics,
                         PerProjectIndexingQueue.PerProviderSink perProviderSink) {
    this.project = project;
    this.indicator = indicator;
    this.unindexedFileFinder = unindexedFileFinder;
    this.scanningStatistics = scanningStatistics;

    pushedFilePropertiesUpdater = PushedFilePropertiesUpdater.getInstance(project);

    // We always need to properly dispose perProviderSink. Make this fact explicit to clients by requiring clients to provide an instance
    this.perProviderSink = perProviderSink;

    IndexableSetOrigin origin = provider.getOrigin();
    if (origin instanceof ModuleContentOrigin && !((ModuleContentOrigin)origin).getModule().isDisposed()) {
      pushers = FilePropertyPusher.EP_NAME.getExtensionList();
      pusherExs = null;
      moduleValues = ReadAction.compute(() -> {
        if (((ModuleContentOrigin)origin).getModule().isDisposed()) return null;
        return getModuleImmediateValues(pushers, ((ModuleContentOrigin)origin).getModule());
      });
      mayBeUsed = moduleValues != null;
    }
    else {
      pushers = null;
      List<FilePropertyPusherEx<?>> extendedPushers = new SmartList<>();
      for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
        if (pusher instanceof FilePropertyPusherEx && ((FilePropertyPusherEx<?>)pusher).acceptsOrigin(project, origin)) {
          extendedPushers.add((FilePropertyPusherEx<?>)pusher);
        }
      }
      if (extendedPushers.isEmpty()) {
        pusherExs = null;
        moduleValues = null;
      }
      else {
        pusherExs = extendedPushers;
        moduleValues = ReadAction.compute(() -> getImmediateValuesEx(extendedPushers, origin));
      }
      mayBeUsed = true;
    }
  }

  /**
   * Introduced to check if providers are still not obviously broken to the level they should not be used, as they didn't spend their life
   * under read lock
   */
  boolean mayBeUsed() {
    return mayBeUsed;
  }

  @Override
  public boolean processFile(@NotNull VirtualFile fileOrDir) {
    indicator.freezeIfPaused(); // give a chance to suspend indexing
    ProgressManager.checkCanceled();

    try {
      processFileRethrowExceptions(fileOrDir);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      UnindexedFilesScanner.LOG.error("Error while scanning " + fileOrDir.getPresentableUrl() + "\n" +
                                      "To reindex this file IDE has to be restarted", e);
    }
    return true;
  }

  private void processFileRethrowExceptions(@NotNull VirtualFile fileOrDir) {
    if (pushers != null && pushedFilePropertiesUpdater instanceof PushedFilePropertiesUpdaterImpl) {
      ((PushedFilePropertiesUpdaterImpl)pushedFilePropertiesUpdater).applyPushersToFile(fileOrDir, pushers, moduleValues);
    }
    else if (pusherExs != null && pushedFilePropertiesUpdater instanceof PushedFilePropertiesUpdaterImpl) {
      ((PushedFilePropertiesUpdaterImpl)pushedFilePropertiesUpdater).applyPushersToFile(fileOrDir, pusherExs, moduleValues);
    }

    UnindexedFileStatus status;
    long statusTime = System.nanoTime();
    try {
      status =
        UnindexedFilesScanner.ourTestMode == UnindexedFilesScanner.TestMode.PUSHING ? null : unindexedFileFinder.getFileStatus(fileOrDir);
    }
    finally {
      statusTime = System.nanoTime() - statusTime;
    }
    if (status != null) {
      if (status.getShouldIndex() && UnindexedFilesScanner.ourTestMode == null) {
        perProviderSink.addFile(fileOrDir);
      }
      scanningStatistics.addStatus(fileOrDir, status, statusTime, project);
    }
  }
}
