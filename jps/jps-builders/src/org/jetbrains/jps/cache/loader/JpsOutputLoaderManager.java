package org.jetbrains.jps.cache.loader;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.cache.client.JpsNettyClient;
import org.jetbrains.jps.cache.client.JpsServerAuthUtil;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.client.JpsServerConnectionUtil;
import org.jetbrains.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.statistics.JpsCacheLoadingSystemStats;
import org.jetbrains.jps.cache.statistics.ProjectBuildStatistic;
import org.jetbrains.jps.cache.statistics.SystemOpsStatistic;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public class JpsOutputLoaderManager {
  private static final Logger LOG = Logger.getInstance(JpsOutputLoaderManager.class);
  private static final String FS_STATE_FILE = "fs_state.dat";
  private static final int DEFAULT_PROJECT_MODULES_COUNT = 2200;
  private static final int PROJECT_MODULE_DOWNLOAD_SIZE_BYTES = 512_000;
  private static final int AVERAGE_CACHE_SIZE_BYTES = 512_000 * 1024;
  private static final int PROJECT_MODULE_SIZE_DISK_BYTES = 921_600;
  private static final int COMMITS_COUNT_THRESHOLD = 600;
  private final AtomicBoolean hasRunningTask;
  private List<JpsOutputLoader<?>> myJpsOutputLoadersLoaders;
  private final JpsMetadataLoader myMetadataLoader;
  private ProjectBuildStatistic myOriginalBuildStatistic;
  private final CanceledStatus myCanceledStatus;
  private final JpsServerClient myServerClient;
  private boolean isCacheDownloaded;
  private final String myBuildOutDir;
  private final String myProjectPath;
  private final String myCommitHash;
  private final int myCommitsCountBetweenCompilation;
  private final JpsNettyClient myNettyClient;

  public JpsOutputLoaderManager(@NotNull JpsProject project,
                                @NotNull CanceledStatus canceledStatus,
                                @NotNull String projectPath,
                                @NotNull Channel channel,
                                @NotNull UUID sessionId,
                                @NotNull CmdlineRemoteProto.Message.ControllerMessage.CacheDownloadSettings cacheDownloadSettings) {
    myNettyClient = new JpsNettyClient(channel, sessionId);
    myCanceledStatus = canceledStatus;
    myProjectPath = projectPath;
    hasRunningTask = new AtomicBoolean();
    myBuildOutDir = getBuildDirPath(project);
    myServerClient = JpsServerClient.getServerClient(cacheDownloadSettings.getServerUrl());
    myMetadataLoader = new JpsMetadataLoader(projectPath, myServerClient);
    myCommitHash = cacheDownloadSettings.getDownloadCommit();
    myCommitsCountBetweenCompilation = cacheDownloadSettings.getCommitsCountLatestBuild();
    JpsServerAuthUtil.setRequestHeaders(cacheDownloadSettings.getAuthHeadersMap());
    JpsCacheLoadingSystemStats.setDeletionSpeed(cacheDownloadSettings.getDeletionSpeed());
    JpsCacheLoadingSystemStats.setDecompressionSpeed(cacheDownloadSettings.getDecompressionSpeed());
  }

  public void load(@NotNull BuildRunner buildRunner, boolean isForceUpdate,
                   @NotNull List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    if (!canRunNewLoading()) return;
    if (isDownloadQuickerThanLocalBuild(buildRunner, myCommitsCountBetweenCompilation, scopes)) {
      // Drop JPS metadata to force plugin for downloading all compilation outputs
      myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.fetching.cache.for.commit", myCommitHash));
      if (isForceUpdate) {
        myMetadataLoader.dropCurrentProjectMetadata();
        File outDir = new File(myBuildOutDir);
        if (outDir.exists()) {
          myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.clean.output.directories"));
          FileUtil.delete(outDir);
        }
        LOG.info("Compilation output folder empty");
      }
      startLoadingForCommit(myCommitHash);
    } else {
      String message = JpsBuildBundle.message("progress.text.local.build.is.quicker");
      LOG.warn(message);
      myNettyClient.sendDescriptionStatusMessage(message);
    }
    hasRunningTask.set(false);
  }

  public void updateBuildStatistic(@NotNull ProjectDescriptor projectDescriptor) {
    if (!hasRunningTask.get() && isCacheDownloaded) {
      BuildTargetsState targetsState = projectDescriptor.getTargetsState();
      myOriginalBuildStatistic.getBuildTargetTypeStatistic().forEach((buildTargetType, originalBuildTime) -> {
        targetsState.setAverageBuildTime(buildTargetType, originalBuildTime);
        LOG.info("Saving old build statistic for " + buildTargetType.getTypeId() + " with value " + originalBuildTime);
      });
      Long originalBuildStatisticProjectRebuildTime = myOriginalBuildStatistic.getProjectRebuildTime();
      targetsState.setLastSuccessfulRebuildDuration(originalBuildStatisticProjectRebuildTime);
      LOG.info("Saving old project rebuild time " + originalBuildStatisticProjectRebuildTime);

    }
  }

  public void saveLatestBuiltCommitId(@NotNull CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status) {
    if (status == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.CANCELED ||
        status == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.ERRORS ) return;
    myNettyClient.saveLatestBuiltCommit();
  }

  private boolean isDownloadQuickerThanLocalBuild(BuildRunner buildRunner, int commitsCountBetweenCompilation,
                                                  List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    SystemOpsStatistic systemOpsStatistic = JpsServerConnectionUtil.measureConnectionSpeed(myNettyClient);
    if (systemOpsStatistic == null) {
      LOG.info("Connection speed is too small to download caches");
      return false;
    }

    Pair<Long, Integer> buildTimeAndProjectModulesCount = estimateProjectBuildTime(buildRunner, scopes);
    int projectModulesCount;
    long approximateBuildTime;
    if (buildTimeAndProjectModulesCount != null) {
      approximateBuildTime = buildTimeAndProjectModulesCount.first;
      projectModulesCount = buildTimeAndProjectModulesCount.second;
    } else {
      LOG.info("Rebuild or unexpected behaviour at build time calculation. Local build will be executed");
      return false;
    }

    if (approximateBuildTime == 0 && commitsCountBetweenCompilation > COMMITS_COUNT_THRESHOLD) {
      LOG.info("Can't calculate approximate project build time, but there are " + commitsCountBetweenCompilation + " not compiled " +
               "commits and it seems that it will be faster to download caches.");
      return true;
    }
    if (approximateBuildTime == 0) {
      LOG.info("Can't calculate approximate project build time");
      return false;
    }
    return calculateApproximateDownloadTimeMs(systemOpsStatistic, projectModulesCount) < approximateBuildTime;
  }

  private static long calculateApproximateDownloadTimeMs(SystemOpsStatistic systemOpsStatistic, int projectModulesCount) {
    double magicCoefficient = 1.3;
    long decompressionSpeed;
    if (JpsCacheLoadingSystemStats.getDecompressionSpeedBytesPesSec() > 0) {
      decompressionSpeed = JpsCacheLoadingSystemStats.getDecompressionSpeedBytesPesSec();
      LOG.info("Using previously saved statistic about decompression speed: " + StringUtil.formatFileSize(decompressionSpeed) + "/s");
    } else {
      decompressionSpeed = systemOpsStatistic.getDecompressionSpeedBytesPesSec();
    }
    long deletionSpeed;
    if (JpsCacheLoadingSystemStats.getDeletionSpeedBytesPerSec() > 0) {
      deletionSpeed = JpsCacheLoadingSystemStats.getDeletionSpeedBytesPerSec();
      LOG.info("Using previously saved statistic about deletion speed: " + StringUtil.formatFileSize(deletionSpeed) + "/s");
    } else {
      deletionSpeed = systemOpsStatistic.getDeletionSpeedBytesPerSec();
    }

    int approximateSizeToDelete = projectModulesCount * PROJECT_MODULE_SIZE_DISK_BYTES;
    int approximateDownloadSize = projectModulesCount * PROJECT_MODULE_DOWNLOAD_SIZE_BYTES + AVERAGE_CACHE_SIZE_BYTES;
    long expectedDownloadTimeSec = approximateDownloadSize / systemOpsStatistic.getConnectionSpeedBytesPerSec();
    long expectedDecompressionTimeSec = approximateDownloadSize / decompressionSpeed;
    long expectedDeleteTimeSec = approximateSizeToDelete / deletionSpeed;
    long expectedTimeOfWorkMs = ((long)(expectedDeleteTimeSec * magicCoefficient) + expectedDownloadTimeSec + expectedDecompressionTimeSec) * 1000;
    LOG.info("Expected download size: " + StringUtil.formatFileSize(approximateDownloadSize) + ". Expected download time: " + expectedDownloadTimeSec + "sec. " +
             "Expected decompression time: " + expectedDecompressionTimeSec + "sec. " +
             "Expected size to delete: " + StringUtil.formatFileSize(approximateDownloadSize) + ". Expected delete time: " + expectedDeleteTimeSec + "sec. " +
             "Total time of work: " + StringUtil.formatDuration(expectedTimeOfWorkMs));
    return expectedTimeOfWorkMs;
  }

  private void startLoadingForCommit(@NotNull String commitId) {
    long startTime = System.nanoTime();
    // Loading metadata for commit
    Map<String, Map<String, BuildTargetState>> commitSourcesState = myMetadataLoader.loadMetadataForCommit(myNettyClient, commitId);
    if (commitSourcesState == null) {
      LOG.warn("Couldn't load metadata for commit: " + commitId);
      return;
    }

    // Calculate downloads
    Map<String, Map<String, BuildTargetState>> currentSourcesState = myMetadataLoader.loadCurrentProjectMetadata();
    int totalDownloads = getLoaders().stream().mapToInt(loader -> loader.calculateDownloads(commitSourcesState, currentSourcesState)).sum();
    try {
      // Computation with loaders results. If at least one of them failed rollback all job
      initLoaders(commitId, totalDownloads, commitSourcesState, currentSourcesState).thenAccept(loaderStatus -> {
        LOG.info("Loading finished with " + loaderStatus + " status");
        try {
          CompletableFuture.allOf(getLoaders().stream()
                                    .map(loader -> applyChanges(loaderStatus, loader))
                                    .toArray(CompletableFuture[]::new))
            .thenRun(() -> saveStateAndNotify(loaderStatus, commitId, startTime))
            .get();
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.warn("Unexpected exception rollback all progress", e);
          onFail();
          getLoaders().forEach(loader -> loader.rollback());
          myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.rolling.back.downloaded.caches"));
        }
      }).handle((result, ex) -> handleExceptions(result, ex)).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Couldn't fetch jps compilation caches", e);
      onFail();
    }
  }

  @Nullable
  private static String getBuildDirPath(@NotNull JpsProject project) {
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(project);
    if (projectExtension == null) return null;
    String url = projectExtension.getOutputUrl();
    if (StringUtil.isEmpty(url)) return null;
    return JpsPathUtil.urlToFile(url).getAbsolutePath();
  }

  private synchronized boolean canRunNewLoading() {
    if (hasRunningTask.get()) {
      LOG.warn("Jps cache loading already in progress, can't start the new one");
      return false;
    }
    if (myBuildOutDir == null) {
      LOG.warn("Build output dir is not configured for the project");
      return false;
    }
    hasRunningTask.set(true);
    return true;
  }

  private <T> CompletableFuture<LoaderStatus> initLoaders(String commitId, int totalDownloads,
                                                          Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                                          Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    JpsLoaderContext loaderContext =
      JpsLoaderContext.createNewContext(totalDownloads, myCanceledStatus, commitId, myNettyClient, commitSourcesState, currentSourcesState);
    List<JpsOutputLoader<?>> loaders = getLoaders();
    loaders.forEach(loader -> loader.setContext(loaderContext));
    // Start loaders with own context
    List<CompletableFuture<LoaderStatus>> completableFutures = ContainerUtil.map(loaders, loader ->
      CompletableFuture.supplyAsync(() -> loader.extract(loader.load()), INSTANCE));

    // Reduce loaders statuses into the one
    CompletableFuture<LoaderStatus> initialFuture = completableFutures.get(0);
    if (completableFutures.size() > 1) {
      for (int i = 1; i < completableFutures.size(); i++) {
        initialFuture = initialFuture.thenCombine(completableFutures.get(i), JpsOutputLoaderManager::combine);
      }
    }
    return initialFuture;
  }

  private CompletableFuture<Void> applyChanges(LoaderStatus loaderStatus, JpsOutputLoader<?> loader) {
    if (loaderStatus == LoaderStatus.FAILED) {
      myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.rolling.back"));
      return CompletableFuture.runAsync(() -> loader.rollback(), INSTANCE);
    }
    return CompletableFuture.runAsync(() -> loader.apply(), INSTANCE);
  }

  private void saveStateAndNotify(LoaderStatus loaderStatus, String commitId, long startTime) {
    if (loaderStatus == LoaderStatus.FAILED) {
      onFail();
      return;
    }

    // Statistic should be available if cache downloaded successfully
    myNettyClient.sendDownloadStatisticMessage(commitId, JpsCacheLoadingSystemStats.getDecompressionSpeedBytesPesSec(),
                                               JpsCacheLoadingSystemStats.getDeletionSpeedBytesPerSec());
    isCacheDownloaded = true;
    LOG.info("Loading finished");
  }

  private Void handleExceptions(Void result, Throwable ex) {
    if (ex != null) {
      Throwable cause = ex.getCause();
      if (cause instanceof ProcessCanceledException) {
        LOG.info("Jps caches download canceled");
      }
      else {
        LOG.warn("Couldn't fetch jps compilation caches", ex);
        onFail();
      }
      getLoaders().forEach(loader -> loader.rollback());
      myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.rolling.back.downloaded.caches"));
    }
    return result;
  }

  private List<JpsOutputLoader<?>> getLoaders() {
    if (myJpsOutputLoadersLoaders != null) return myJpsOutputLoadersLoaders;
    myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, myProjectPath),
                                              new JpsCompilationOutputLoader(myServerClient, myBuildOutDir));
    return myJpsOutputLoadersLoaders;
  }

  private static LoaderStatus combine(LoaderStatus firstStatus, LoaderStatus secondStatus) {
    if (firstStatus == LoaderStatus.FAILED || secondStatus == LoaderStatus.FAILED) return LoaderStatus.FAILED;
    return LoaderStatus.COMPLETE;
  }

  private void onFail() { }

  private @Nullable Pair<Long, Integer> estimateProjectBuildTime(BuildRunner buildRunner,
                                       List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    try {
      long startTime = System.currentTimeMillis();
      BuildFSState fsState = new BuildFSState(false);
      final File dataStorageRoot = Utils.getDataStorageRoot(myProjectPath);
      if (dataStorageRoot == null) {
        LOG.warn("Cannot determine build data storage root for project");
        return null;
      }
      if (!dataStorageRoot.exists() || !new File(dataStorageRoot, FS_STATE_FILE).exists()) {
        // invoked the very first time for this project
        buildRunner.setForceCleanCaches(true);
        LOG.info("Storage files are absent");
      }
      ProjectDescriptor projectDescriptor = buildRunner.load(MessageHandler.DEAF, dataStorageRoot, fsState);
      long contextInitializationTime = System.currentTimeMillis() - startTime;
      LOG.info("Time spend to context initialization: " + contextInitializationTime);
      CompileScope compilationScope = buildRunner.createCompilationScope(projectDescriptor, scopes);
      long estimatedBuildTime = IncProjectBuilder.calculateEstimatedBuildTime(projectDescriptor, projectDescriptor.getTargetsState(),
                                                                       compilationScope);
      BuildTargetsState targetsState = projectDescriptor.getTargetsState();
      if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(compilationScope)) {
        LOG.info("Project rebuild enabled, caches will not be download");
        return null;
      }
      Map<BuildTargetType<?>, Long> buildTargetTypeStatistic = new HashMap<>();
      for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
        buildTargetTypeStatistic.put(type,  targetsState.getAverageBuildTime(type));
      }
      myOriginalBuildStatistic = new ProjectBuildStatistic(targetsState.getLastSuccessfulRebuildDuration(), buildTargetTypeStatistic);
      long totalCalculationTime = System.currentTimeMillis() - startTime;
      LOG.info("Calculated build time: " + StringUtil.formatDuration(estimatedBuildTime));
      LOG.info("Time spend to context initialization and time calculation: " + totalCalculationTime);
      return Pair.create(estimatedBuildTime, projectDescriptor.getProject().getModules().size());
    }
    catch (Exception e) {
      LOG.warn("Exception at calculation approximate build time", e);
    }
    return null;
  }
}
