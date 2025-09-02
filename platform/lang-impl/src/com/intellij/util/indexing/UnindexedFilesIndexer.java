// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.text.Strings;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.PerProjectIndexingQueue.QueuedFiles;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.contentQueue.IndexingProgressReporter2;
import com.intellij.util.indexing.dependencies.IncompleteIndexingToken;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl;
import com.intellij.util.indexing.events.FileIndexingRequest;
import io.opentelemetry.api.trace.SpanBuilder;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.Instant;
import java.util.*;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.Indexes;

/**
 * UnindexedFilesIndexer is to index files: explicitly provided (see {@linkplain PerProjectIndexingQueue}), and implicitly marked as dirty, e.g.,
 * by VFS (as reported by FileBasedIndexImpl#getFilesToUpdate).
 */
@Internal
public final class UnindexedFilesIndexer extends DumbModeTask {
  private record IndexingReason(@NonNls @NotNull String reason, int counter) {
    @Override
    public String toString() {
      if (counter > 1) {
        return reason + " (x" + counter + ")";
      }
      else {
        return reason;
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(UnindexedFilesIndexer.class);
  private final @NotNull Project myProject;
  private final FileBasedIndexImpl myIndex;
  private final @NotNull List<IndexingReason> indexingReasons;
  private final IncompleteIndexingToken taskToken;
  private final @Nullable PerProjectIndexingQueue customFilesSource;

  @VisibleForTesting
  public UnindexedFilesIndexer(@NotNull Project project, @NonNls @NotNull String indexingReason) {
    this(project, null, indexingReason);
  }

  /**
   * If customFilesSource is NOT null,
   * then files from customFilesSource will be indexed in the first order, then files reported by FileBasedIndexImpl#getFilesToUpdate
   * (files from PerProjectIndexingQueue are not indexed in this case)
   * <br>
   * If customFilesSource is null,
   * then files from PerProjectIndexingQueue will be indexed in the first order, then files reported by FileBasedIndexImpl#getFilesToUpdate
   */
  @VisibleForTesting
  public UnindexedFilesIndexer(@NotNull Project project,
                               @Nullable PerProjectIndexingQueue customFilesSource,
                               @NonNls @NotNull String indexingReason) {
    this(project, customFilesSource, Collections.singletonList(new IndexingReason(indexingReason, 1)));
  }

  private UnindexedFilesIndexer(@NotNull Project project,
                                @Nullable PerProjectIndexingQueue customFilesSource,
                                @NotNull List<@NotNull IndexingReason> indexingReasons) {
    myProject = project;
    myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    this.customFilesSource = customFilesSource;
    this.indexingReasons = indexingReasons;
    taskToken = myProject.getService(ProjectIndexingDependenciesService.class).newIncompleteIndexingToken();
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
    SpanBuilder spanBuilder = TelemetryManager.getInstance().getTracer(Indexes).spanBuilder("InternalSpanForIndexingDiagnostic");
    TraceKt.use(spanBuilder, span -> {
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
      return null;
    });
  }

  private void doIndexFiles(@NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory) {
    IndexingRequestToken indexingRequest = myProject.getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(myIndex, indexingRequest);

    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    indicator.setIndeterminate(false);
    var originalSuspender = ProgressSuspender.getSuspender(unwrapAll(indicator));
    Function0<Boolean> pauseCondition = originalSuspender == null ? () -> false : originalSuspender::isSuspended;

    QueuedFiles queuedFiles = getExplicitlyRequestedFiles();
    projectDumbIndexingHistory.setScanningIds(queuedFiles.getScanningIds());

    IndexUpdateRunner.FileSet fileSets = getExplicitlyRequestedFilesSets(pauseCondition, queuedFiles);
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

  private @NotNull QueuedFiles getExplicitlyRequestedFiles() {
    PerProjectIndexingQueue sourceQueue;
    if (customFilesSource != null) {
      sourceQueue = customFilesSource;
    }
    else {
      sourceQueue = myProject.getService(PerProjectIndexingQueue.class);
    }
    return sourceQueue.getAndResetQueuedFiles$intellij_platform_lang_impl();
  }

  private IndexUpdateRunner.@NotNull FileSet getExplicitlyRequestedFilesSets(@NotNull Function0<@NotNull Boolean> pauseCondition,
                                                                             QueuedFiles files) {
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
      // indexingQueue.wrapIndexing will try to acquire scanningIndexingMutex, and will wait until scanning is completed.
      // Set progress text for this "waiting"
      indicator.setIndeterminate(true);
      indicator.setText(IndexingBundle.message("progress.indexing.waiting.for.scanning.to.complete"));

      PerProjectIndexingQueue indexingQueue = myProject.getService(PerProjectIndexingQueue.class);
      indexingQueue.wrapIndexing$intellij_platform_lang_impl(() -> {
        doPerformInDumbMode(indicator);
      });
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
    trackSuspends(ProgressSuspender.getSuspender(indicator), this,
                  () -> projectDumbIndexingHistory.suspendStages(Instant.now()),
                  () -> projectDumbIndexingHistory.stopSuspendingStages(Instant.now()));

    try {
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> indexFiles(projectDumbIndexingHistory, indicator));
    }
    catch (Throwable e) {
      taskToken.markUnsuccessful();
      projectDumbIndexingHistory.setWasCancelled(e.getMessage());
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      IndexDiagnosticDumper.getInstance().onDumbIndexingFinished(projectDumbIndexingHistory);
    }
  }

  @Override
  public void dispose() {
    if (!myProject.isDisposed()) {
      var service = myProject.getServiceIfCreated(ProjectIndexingDependenciesService.class);
      if (service != null) {
        service.completeToken(taskToken);
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
    if (otherIndexingTask.customFilesSource != customFilesSource) {
      LOG.error("Indexing tasks with different sources should not appear in the same task queue. " +
                "This is likely a bug in a test (custom sources is a test-only feature)");
      return null;
    }

    List<IndexingReason> mergedReason = mergeReasons(otherIndexingTask);
    return new UnindexedFilesIndexer(myProject, customFilesSource, mergedReason);
  }

  private List<IndexingReason> mergeReasons(@NotNull UnindexedFilesIndexer otherIndexingTask) {
    TreeMap<String, IndexingReason> mergedReasons = new TreeMap<>(); // to preserve the order
    for (IndexingReason reason : indexingReasons) {
      mergedReasons.put(reason.reason, reason);
    }
    for (IndexingReason reason : otherIndexingTask.indexingReasons) {
      var existingReason = mergedReasons.get(reason.reason);
      if (existingReason != null) {
        mergedReasons.put(existingReason.reason, new IndexingReason(existingReason.reason, existingReason.counter + reason.counter));
      }
      else {
        mergedReasons.put(reason.reason, reason);
      }
    }

    return new ArrayList<>(mergedReasons.values());
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

  public @NotNull String getIndexingReason() {
    if (indexingReasons.size() == 1) {
      return indexingReasons.get(0).toString();
    }
    else if (indexingReasons.size() > 1) {
      return "Merged " + Strings.join(indexingReasons, " with ");
    }
    else {
      LOG.error("indexingReasons should never be empty");
      return "<unknown>";
    }
  }

  @Override
  public String toString() {
    return "UnindexedFilesIndexer[" + myProject.getName() + ", reasons: " + indexingReasons + "]";
  }
}
