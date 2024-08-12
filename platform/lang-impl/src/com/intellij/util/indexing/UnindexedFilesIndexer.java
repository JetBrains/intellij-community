// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.WrappedProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.PerProjectIndexingQueue.QueuedFiles;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.contentQueue.IndexingProgressReporter2;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import com.intellij.util.indexing.dependencies.ScanningRequestToken;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl;
import com.intellij.util.indexing.events.FileIndexingRequest;
import io.opentelemetry.api.trace.SpanBuilder;
import it.unimi.dsi.fastutil.longs.LongSet;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.*;
import org.jetbrains.annotations.ApiStatus.Internal;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;

/**
 * UnindexedFilesIndexer is to index files: explicitly provided (see providerToFiles in constructor), and implicitly marked as dirty, e.g.,
 * by VFS (as reported by FileBasedIndexImpl#getFilesToUpdate).
 */
@Internal
public final class UnindexedFilesIndexer extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesIndexer.class);
  private final @NotNull Project myProject;
  private final FileBasedIndexImpl myIndex;
  private final @NotNull QueuedFiles files;
  private final @NonNls @NotNull String indexingReason;

  UnindexedFilesIndexer(@NotNull Project project,
                        @NonNls @NotNull String indexingReason) {
    this(project, new QueuedFiles(), indexingReason);
  }

  /**
   * For backward compatibility
   *
   * @deprecated Indexing should always start with scanning. You don't need this class - do scanning instead
   */
  @Deprecated
  public UnindexedFilesIndexer(@NotNull Project project,
                               @NotNull Set<VirtualFile> files,
                               @NonNls @NotNull String indexingReason,
                               @NotNull LongSet scanningIds) {
    this(project, QueuedFiles.fromFilesCollection(files, scanningIds), indexingReason);
  }

  /**
   * if `files` is empty, only FileBasedIndexImpl#getFilesToUpdate files will be indexed.
   * <p>
   * if `files` is not empty, `files` will be indexed in the first order, then files reported by FileBasedIndexImpl#getFilesToUpdate
   */
  @ApiStatus.Experimental
  public UnindexedFilesIndexer(@NotNull Project project,
                               @NotNull QueuedFiles files,
                               @NonNls @NotNull String indexingReason) {
    myProject = project;
    myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    this.files = files;
    this.indexingReason = indexingReason;
  }


  private ProgressIndicator unwrapAll(ProgressIndicator indicator) {
    // Can't use "ProgressWrapper.unwrapAll" here because it unwraps "ProgressWrapper"s only (not any "WrappedProgressIndicator")
    var unwrapped = indicator;
    while (unwrapped instanceof WrappedProgressIndicator wrapped) {
      unwrapped = wrapped.getOriginalProgressIndicator();
    }
    return unwrapped;
  }

  void indexFiles(@NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory,
                  @NotNull ProgressIndicator indicator) {
    if (SystemProperties.getBooleanProperty("idea.indexes.pretendNoFiles", false)) {
      LOG.info("Finished for " + myProject.getName() + ". System property 'idea.indexes.pretendNoFiles' is enabled.");
      return;
    }

    projectDumbIndexingHistory.setScanningIds(files.getScanningIds());

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();

    ProgressIndicator poweredIndicator =
      PoweredProgressIndicator.wrap(indicator, getPowerForSmoothProgressIndicator());
    poweredIndicator.setIndeterminate(false);
    poweredIndicator.setFraction(0);
    poweredIndicator.setText(IndexingBundle.message("progress.indexing.updating"));

    ProgressManager.getInstance().runProcess(() -> {
      doIndexFiles(projectDumbIndexingHistory);
    }, poweredIndicator);

    LOG.info(
      snapshot.getLogResponsivenessSinceCreationMessage("Finished for " + myProject.getName() + ". Unindexed files update"));
  }

  private void doIndexFiles(@NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory) {
    IndexingRequestToken indexingRequest = myProject.getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(myIndex, indexingRequest);

    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    indicator.setIndeterminate(false);
    var originalSuspender = ProgressSuspender.getSuspender(unwrapAll(indicator));
    Function0<Boolean> pauseCondition = originalSuspender == null ? () -> false : originalSuspender::isSuspended;

    IndexUpdateRunner.FileSet fileSets = getExplicitlyRequestedFilesSets(pauseCondition);
    if (!fileSets.isEmpty()) {
      var reporter = IndexingProgressReporter2.Companion.createInstance(indicator, fileSets.size());
      doIndexFiles(projectDumbIndexingHistory, indexUpdateRunner, fileSets, reporter);
    }

    // Order is important: getRefreshedFiles may return some subset of getExplicitlyRequestedFilesSets files (e.g., new files)
    // We first index explicitly requested files, this will also mark indexed files as "up-to-date", then we index remaining dirty files
    fileSets = getRefreshedFiles(projectDumbIndexingHistory, pauseCondition);
    if (!fileSets.isEmpty()) {
      var reporter = IndexingProgressReporter2.Companion.createInstance(indicator, fileSets.size());
      doIndexFiles(projectDumbIndexingHistory, indexUpdateRunner, fileSets, reporter);
    }
  }

  private IndexUpdateRunner.FileSet getRefreshedFiles(@NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory,
                                                      @NotNull Function0<@NotNull Boolean> pauseCondition) {
    String filesetName = "Refreshed files";
    Collection<FileIndexingRequest> files =
      new ProjectChangedFilesScanner(myProject).scan(projectDumbIndexingHistory);
    return new IndexUpdateRunner.FileSet(myProject, filesetName, QueuedFiles.fromRequestsCollection(files, Collections.emptyList()),
                                         pauseCondition);
  }

  private IndexUpdateRunner.@NotNull FileSet getExplicitlyRequestedFilesSets(@NotNull Function0<@NotNull Boolean> pauseCondition) {
    return new IndexUpdateRunner.FileSet(myProject, "<indexing queue>", files, pauseCondition);
  }

  private void doIndexFiles(@NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory,
                            IndexUpdateRunner indexUpdateRunner,
                            IndexUpdateRunner.FileSet fileSet,
                            @NotNull IndexingProgressReporter2 reporter) {
    IndexUpdateRunner.IndexingInterruptedException exception = null;
    try {
      indexUpdateRunner.indexFiles(myProject, fileSet, projectDumbIndexingHistory, reporter);
    }
    catch (IndexUpdateRunner.IndexingInterruptedException e) {
      exception = e;
    }

    try {
      projectDumbIndexingHistory.addProviderStatistics(fileSet.getStatistics());
    }
    catch (Exception e) {
      LOG.error("Failed to add indexing statistics", e);
    }

    if (exception != null) {
      ExceptionUtil.rethrow(exception.getCause());
    }
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    SpanBuilder spanBuilder = TelemetryManager.getInstance().getTracer(Indexes)
      .spanBuilder("UnindexedFilesIndexer.performInDumbMode");
    TraceKt.use(spanBuilder, span -> {
      doPerformInDumbMode(indicator);
      return null;
    });
  }

  private void doPerformInDumbMode(@NotNull ProgressIndicator indicator) {
    myIndex.loadIndexes(); // make sure that indexes are loaded, because we can get here without scanning (e.g., from VFS refresh)
    if (!IndexInfrastructure.hasIndices()) {
      return;
    }
    ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory = new ProjectDumbIndexingHistoryImpl(myProject);
    IndexDiagnosticDumper.getInstance().onDumbIndexingStarted(projectDumbIndexingHistory);
    ScanningRequestToken token = myProject.getService(ProjectIndexingDependenciesService.class).newScanningToken();
    trackSuspends(ProgressSuspender.getSuspender(indicator), this,
                  () -> projectDumbIndexingHistory.suspendStages(Instant.now()),
                  () -> projectDumbIndexingHistory.stopSuspendingStages(Instant.now()));

    try {
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> indexFiles(projectDumbIndexingHistory, indicator));
    }
    catch (Throwable e) {
      token.markUnsuccessful();
      projectDumbIndexingHistory.setWasInterrupted();
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      IndexDiagnosticDumper.getInstance().onDumbIndexingFinished(projectDumbIndexingHistory);
      ProjectIndexingDependenciesService service = myProject.getServiceIfCreated(ProjectIndexingDependenciesService.class);
      if (service != null) {
        service.completeToken(token, false);
      }
    }
  }

  static void trackSuspends(@Nullable ProgressSuspender suspender,
                            @NotNull Disposable parentDisposable,
                            @NotNull Runnable onSuspendStart,
                            @NotNull Runnable onSuspendStop) {
    if (suspender == null) {
      return;
    }
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable)
      .subscribe(ProgressSuspender.TOPIC, new ProgressSuspender.SuspenderListener() {
        @Override
        public void suspendedStatusChanged(@NotNull ProgressSuspender changedSuspender) {
          if (suspender != changedSuspender) {
            return;
          }
          if (suspender.isSuspended()) {
            onSuspendStart.run();
          }
          else {
            onSuspendStop.run();
          }
        }
      });
  }

  @Override
  public @Nullable UnindexedFilesIndexer tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
    if (!(taskFromQueue instanceof UnindexedFilesIndexer otherIndexingTask)) return null;
    QueuedFiles otherQueue = otherIndexingTask.files;

    QueuedFiles mergedQueue = new QueuedFiles();
    mergedQueue.addRequests$intellij_platform_lang_impl(files.getRequests(), files.getScanningIds());
    mergedQueue.addRequests$intellij_platform_lang_impl(otherQueue.getRequests(), otherQueue.getScanningIds());

    String mergedReason = mergeReasons(otherIndexingTask);
    return new UnindexedFilesIndexer(myProject, mergedQueue, mergedReason);
  }

  private @NotNull String mergeReasons(@NotNull UnindexedFilesIndexer otherIndexingTask) {
    String trimmedReason = StringUtil.trimStart(indexingReason, "Merged ");
    String trimmedOtherReason = StringUtil.trimStart(otherIndexingTask.indexingReason, "Merged ");
    if (otherIndexingTask.files.isEmpty() && trimmedReason.endsWith(trimmedOtherReason)) {
      return indexingReason;
    }
    else {
      return "Merged " + trimmedReason + " with " + trimmedOtherReason;
    }
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
  @NotNull
  QueuedFiles getFiles() {
    return files;
  }

  public @NotNull String getIndexingReason() {
    return indexingReason;
  }

  @Override
  public String toString() {
    return "UnindexedFilesIndexer[" + myProject.getName() + ", " + files.getSize() + " files, reason: " + indexingReason + "]";
  }
}
