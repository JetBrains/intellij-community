// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics;
import com.intellij.util.progress.ConcurrentTasksProgressManager;
import com.intellij.util.progress.SubTaskProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

class UnindexedFilesIndexer extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesIndexer.class);
  private static final int MINIMUM_NUMBER_OF_FILES_TO_RUN_CONCURRENT_INDEXING =
    SystemProperties.getIntProperty("intellij.indexing.minimum.number.of.files.to.run.concurrent.indexing", 100);
  private final Project myProject;
  private final FileBasedIndexImpl myIndex;
  private final Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles;

  UnindexedFilesIndexer(Project project,
                        Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles) {
    myProject = project;
    myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    this.providerToFiles = providerToFiles;
  }

  void indexFiles(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                  @NotNull ProgressIndicator indicator) {
    int totalFiles = providerToFiles.values().stream().mapToInt(List::size).sum();
    if (totalFiles == 0) {
      LOG.info("Finished for " + myProject.getName() + ". No files to index with loading content.");
      return;
    }
    if (SystemProperties.getBooleanProperty("idea.indexes.pretendNoFiles", false)) {
      LOG.info("Finished for " + myProject.getName() + ". System property 'idea.indexes.pretendNoFiles' is enabled.");
      return;
    }

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();

    ProgressIndicator poweredIndicator =
      PoweredProgressIndicator.wrap(indicator, getPowerForSmoothProgressIndicator());
    poweredIndicator.setIndeterminate(false);
    poweredIndicator.setFraction(0);
    poweredIndicator.setText(IndexingBundle.message("progress.indexing.updating"));

    myIndex.resetSnapshotInputMappingStatistics();

    projectIndexingHistory.startStage(ProjectIndexingHistoryImpl.Stage.Indexing);
    try {
      doIndexFiles(providerToFiles, projectIndexingHistory, poweredIndicator);
    }
    finally {
      projectIndexingHistory.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing);
    }

    LOG.info(
      snapshot.getLogResponsivenessSinceCreationMessage("Finished for " + myProject.getName() + ". Unindexed files update"));
    List<SnapshotInputMappingsStatistics> snapshotInputMappingsStatistics = myIndex.dumpSnapshotInputMappingStatistics();
    projectIndexingHistory.addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics);
  }

  private void doIndexFiles(@NotNull Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles,
                            @NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                            @NotNull ProgressIndicator progressIndicator) {
    int totalFiles = providerToFiles.values().stream().mapToInt(List::size).sum();
    ConcurrentTasksProgressManager concurrentTasksProgressManager = new ConcurrentTasksProgressManager(progressIndicator, totalFiles);

    int numberOfIndexingThreads = UnindexedFilesUpdater.getNumberOfIndexingThreads();
    LOG.info(
      "Use " + numberOfIndexingThreads + " indexing " + StringUtil.pluralize("thread", numberOfIndexingThreads) +
      " for indexing of " + myProject.getName());
    IndexUpdateRunner
      indexUpdateRunner = new IndexUpdateRunner(myIndex, UnindexedFilesUpdater.GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads);

    ArrayList<IndexableFilesIterator> providers = new ArrayList<>(providerToFiles.keySet());
    for (int index = 0; index < providers.size(); ) {
      List<IndexUpdateRunner.FileSet> fileSets = new ArrayList<>();
      int takenFiles = 0;

      int biggestProviderFiles = 0;
      IndexableFilesIterator biggestProvider = null;

      while (takenFiles < MINIMUM_NUMBER_OF_FILES_TO_RUN_CONCURRENT_INDEXING && index < providers.size()) {
        IndexableFilesIterator provider = providers.get(index++);
        List<VirtualFile> providerFiles = providerToFiles.getOrDefault(provider, Collections.emptyList());
        if (!providerFiles.isEmpty()) {
          fileSets.add(new IndexUpdateRunner.FileSet(myProject, provider.getDebugName(), providerFiles));
        }
        if (biggestProviderFiles < providerFiles.size()) {
          biggestProviderFiles = providerFiles.size();
          biggestProvider = provider;
        }
        takenFiles += providerFiles.size();
      }
      if (fileSets.isEmpty() || biggestProvider == null) {
        break;
      }

      var indexingProgressText = biggestProvider.getIndexingProgressText();

      concurrentTasksProgressManager.setText(indexingProgressText);
      int setFilesNumber = fileSets.stream().mapToInt(b -> b.files.size()).sum();
      SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(setFilesNumber);
      try {
        IndexUpdateRunner.IndexingInterruptedException exception = null;
        try {
          indexUpdateRunner.indexFiles(myProject, fileSets, subTaskIndicator, projectIndexingHistory);
        }
        catch (IndexUpdateRunner.IndexingInterruptedException e) {
          exception = e;
        }

        try {
          fileSets.forEach(b -> projectIndexingHistory.addProviderStatistics(b.statistics));
        }
        catch (Exception e) {
          LOG.error("Failed to add indexing statistics", e);
        }

        if (exception != null) {
          ExceptionUtil.rethrow(exception.getCause());
        }
      }
      finally {
        subTaskIndicator.finished();
      }
    }
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    if (!IndexInfrastructure.hasIndices()) {
      return;
    }
    ProjectIndexingHistoryImpl projectIndexingHistory = new ProjectIndexingHistoryImpl(myProject, "Async indexing", ScanningType.REFRESH);
    ProgressSuspender suspender = ProgressSuspender.getSuspender(indicator);
    if (suspender != null) {
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(ProgressSuspender.TOPIC, projectIndexingHistory.getSuspendListener(suspender));
    }

    try {
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> indexFiles(projectIndexingHistory, indicator));
    }
    catch (Throwable e) {
      projectIndexingHistory.setWasInterrupted(true);
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      projectIndexingHistory.finishTotalUpdatingTime();
      IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
    }
  }

  @Override
  public @Nullable UnindexedFilesIndexer tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
    if (!(taskFromQueue instanceof UnindexedFilesIndexer)) return null;
    UnindexedFilesIndexer otherIndexingTask = (UnindexedFilesIndexer)taskFromQueue;

    Map<IndexableFilesIterator, List<VirtualFile>> largeMap =
      otherIndexingTask.providerToFiles.size() > providerToFiles.size() ? otherIndexingTask.providerToFiles : providerToFiles;
    Map<IndexableFilesIterator, List<VirtualFile>> smallMap =
      largeMap == providerToFiles ? otherIndexingTask.providerToFiles : providerToFiles;

    Map<IndexableFilesIterator, List<VirtualFile>> mergedFilesToIndex = new HashMap<>(largeMap);
    for (Map.Entry<IndexableFilesIterator, List<VirtualFile>> e : smallMap.entrySet()) {
      List<VirtualFile> mergedList;
      if (mergedFilesToIndex.containsKey(e.getKey())) {
        // merge virtual files removing duplicates
        Set<VirtualFile> mergedSet = new HashSet<>(mergedFilesToIndex.get(e.getKey()));
        mergedSet.addAll(e.getValue());
        mergedList = new ArrayList<>(mergedSet);
      } else {
        mergedList = e.getValue();
      }
      mergedFilesToIndex.put(e.getKey(), mergedList);
    }

    return new UnindexedFilesIndexer(myProject, mergedFilesToIndex);
  }

  private static double getPowerForSmoothProgressIndicator() {
    String rawValue = Registry.stringValue("indexing.progress.indicator.power");
    if ("-".equals(rawValue)) {
      return 1.0;
    }
    try {
      return Double.parseDouble(rawValue);
    }
    catch (NumberFormatException e) {
      return 1.0;
    }
  }

  @TestOnly
  Map<IndexableFilesIterator, List<VirtualFile>> getProviderToFiles() {
    return providerToFiles;
  }
}
