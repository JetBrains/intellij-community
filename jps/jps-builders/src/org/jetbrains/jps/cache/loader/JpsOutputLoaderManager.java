package org.jetbrains.jps.cache.loader;

import com.intellij.openapi.Disposable;
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
import org.jetbrains.jps.cache.client.JpsNettyClient;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.client.JpsServerConnectionUtil;
import org.jetbrains.jps.cache.git.GitCommitsIterator;
import org.jetbrains.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
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
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;
import static org.jetbrains.jps.cache.JpsCachesPluginUtil.INTELLIJ_REPO_NAME;

public class JpsOutputLoaderManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(JpsOutputLoaderManager.class);
  private static final String FS_STATE_FILE = "fs_state.dat";
  private static final int DEFAULT_PROJECT_MODULES_COUNT = 2200;
  private static final int COMMITS_COUNT_THRESHOLD = 600;
  private static final int PROJECT_MODULE_SIZE_KB = 500;
  private final AtomicBoolean hasRunningTask;
  //private final CompilerWorkspaceConfiguration myWorkspaceConfiguration;
  private Map<BuildTargetType<?>, Long> myOriginalBuildStatistic;
  private List<JpsOutputLoader<?>> myJpsOutputLoadersLoaders;
  private final JpsMetadataLoader myMetadataLoader;
  private final CanceledStatus myCanceledStatus;
  private final JpsServerClient myServerClient;
  private boolean isCacheDownloaded;
  private final String myBuildOutDir;
  private final String myProjectPath;
  private final JpsNettyClient myNettyClient;

  @Override
  public void dispose() { }

  //@NotNull
  //public static JpsOutputLoaderManager getInstance(@NotNull Project project) {
  //  return project.getService(JpsOutputLoaderManager.class);
  //}

  public JpsOutputLoaderManager(@NotNull JpsProject project,
                                @NotNull CanceledStatus canceledStatus,
                                @NotNull String projectPath,
                                @NotNull Channel channel,
                                @NotNull UUID sessionId) {
    myNettyClient = new JpsNettyClient(channel, sessionId);
    myCanceledStatus = canceledStatus;
    myProjectPath = projectPath;
    hasRunningTask = new AtomicBoolean();
    myBuildOutDir = getBuildDirPath(project);
    myOriginalBuildStatistic = new HashMap<>();
    myServerClient = JpsServerClient.getServerClient();
    myMetadataLoader = new JpsMetadataLoader(projectPath, myServerClient);
    //myWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    // Configure build manager
    //BuildManager buildManager = BuildManager.getInstance();
    //if (!buildManager.isGeneratePortableCachesEnabled()) buildManager.setGeneratePortableCachesEnabled(true);
  }

  public void load(@NotNull BuildRunner buildRunner, boolean isForceUpdate,
                   @NotNull List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    if (!canRunNewLoading()) return;
    Pair<String, Integer> commitInfo = getNearestCommit(buildRunner, isForceUpdate, scopes);
    if (commitInfo != null) {
      //assert myProject != null;
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingStarted();
      // Drop JPS metadata to force plugin for downloading all compilation outputs
      if (isForceUpdate) {
        myMetadataLoader.dropCurrentProjectMetadata();
        File outDir = new File(myBuildOutDir);
        if (outDir.exists()) {
          myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.clean.output.directories"));
          FileUtil.delete(outDir);
        }
        LOG.info("Compilation output folder empty");
      }
      startLoadingForCommit(commitInfo.first);
    }
    hasRunningTask.set(false);
  }

  public void updateBuildStatistic(@NotNull ProjectDescriptor projectDescriptor) {
    if (!hasRunningTask.get() && isCacheDownloaded) {
      BuildTargetsState targetsState = projectDescriptor.getTargetsState();
      myOriginalBuildStatistic.forEach((buildTargetType, originalBuildTime) -> {
        targetsState.setAverageBuildTime(buildTargetType, originalBuildTime);
      });
    }
  }

  public void saveLatestBuiltCommitId(@NotNull CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status) {
    if (status == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.CANCELED ||
        status == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.ERRORS ) return;
    myNettyClient.saveLatestBuiltCommit();
  }

  @Nullable
  private Pair<String, Integer> getNearestCommit(@NotNull BuildRunner buildRunner, boolean isForceUpdate,
                                                 @NotNull List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    Map<String, Set<String>> availableCommitsPerRemote = myServerClient.getCacheKeysPerRemote(myNettyClient);
    //List<GitCommitsIterator> repositoryList = GitRepositoryUtil.getCommitsIterator(myProject, availableCommitsPerRemote.keySet());

    GitCommitsIterator commitsIterator = new GitCommitsIterator(myNettyClient, INTELLIJ_REPO_NAME);
    String latestDownloadedCommit = commitsIterator.getLatestDownloadedCommit();
    String latestBuiltCommit = commitsIterator.getLatestBuiltRemoteMasterCommit();
    Set<String> availableCommitsForRemote = availableCommitsPerRemote.get(commitsIterator.getRemote());
    if (availableCommitsForRemote == null) {
      String message = JpsBuildBundle.message("notification.content.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      myNettyClient.sendDescriptionStatusMessage(message);
      return null;
    }

    int commitsBehind = 0;
    int commitsCountBetweenCompilation = 0;
    String commitToDownload = "";
    boolean latestBuiltCommitFound = false;
    while (commitsIterator.hasNext()) {
      String commitId = commitsIterator.next();
      if (commitId.equals(latestBuiltCommit) && !latestBuiltCommitFound) {
        latestBuiltCommitFound = true;
      }
      if (!latestBuiltCommitFound) {
        commitsCountBetweenCompilation++;
      }
      if (availableCommitsForRemote.contains(commitId) && commitToDownload.isEmpty()) {
        commitToDownload = commitId;
      }
      if (commitToDownload.isEmpty()) {
        commitsBehind++;
      }

      if (latestBuiltCommitFound && !commitToDownload.isEmpty()) break;
    }

    if (!availableCommitsForRemote.contains(commitToDownload)) {
      String message = JpsBuildBundle.message("notification.content.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      myNettyClient.sendDescriptionStatusMessage(message);
      return null;
    }
    LOG.info("Commits count between latest success compilation and current commit: " + commitsCountBetweenCompilation +
             ". Detected commit to download: " + commitToDownload);
    if (!isDownloadQuickerThanLocalBuild(buildRunner, commitsCountBetweenCompilation, scopes)) {
      String message = JpsBuildBundle.message("notification.content.local.build.is.quicker");
      LOG.warn(message);
      myNettyClient.sendDescriptionStatusMessage(message);
      return null;
    }
    if (commitToDownload.equals(latestDownloadedCommit) && !isForceUpdate) {
      String message = JpsBuildBundle.message("notification.content.system.contains.up.to.date.caches");
      LOG.info(message);
      myNettyClient.sendDescriptionStatusMessage(message);
      return null;
    }
    return Pair.create(commitToDownload, commitsBehind);
  }

  private boolean isDownloadQuickerThanLocalBuild(BuildRunner buildRunner, int commitsCountBetweenCompilation,
                                                  List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> scopes) {
    Long connectionSpeed = JpsServerConnectionUtil.measureConnectionSpeed(myNettyClient);
    if (connectionSpeed == null) {
      LOG.info("Connection speed is too small to download caches");
      return false;
    }

    Pair<Long, Integer> buildTimeAndProjectModulesCount = estimateProjectBuildTime(buildRunner, scopes);
    int projectModulesCount = DEFAULT_PROJECT_MODULES_COUNT;
    long approximateBuildTime = 0;
    if (buildTimeAndProjectModulesCount != null) {
      approximateBuildTime = buildTimeAndProjectModulesCount.first;
      projectModulesCount = buildTimeAndProjectModulesCount.second;
    }
    int approximateDownloadSizeKB = projectModulesCount * PROJECT_MODULE_SIZE_KB + 512_000;
    long expectedDownloadTimeSec = approximateDownloadSizeKB * 1024L / connectionSpeed;
    LOG.info("Approximate expected download size: " + approximateDownloadSizeKB  + "KB. Expected download time: " + expectedDownloadTimeSec + "sec.");

    if (approximateBuildTime == 0 && commitsCountBetweenCompilation > COMMITS_COUNT_THRESHOLD) {
      LOG.info("Can't calculate approximate project build time, but there are " + commitsCountBetweenCompilation + " not compiled " +
               "commits and internet connection is good enough. Expected download time: " + expectedDownloadTimeSec + "sec.");
      return true;
    }
    if (approximateBuildTime == 0) {
      LOG.info("Can't calculate approximate project build time");
      return false;
    }
    return expectedDownloadTimeSec * 1000 > approximateBuildTime;
  }

  private void startLoadingForCommit(@NotNull String commitId) {
    long startTime = System.nanoTime();
    myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.fetching.cache.for.commit", commitId));

    // Loading metadata for commit
    Map<String, Map<String, BuildTargetState>> commitSourcesState = myMetadataLoader.loadMetadataForCommit(myNettyClient, commitId);
    if (commitSourcesState == null) {
      LOG.warn("Couldn't load metadata for commit: " + commitId);
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
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
          //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
          myNettyClient.sendDescriptionStatusMessage(JpsBuildBundle.message("progress.text.rolling.back.downloaded.caches"));
        }
      }).handle((result, ex) -> handleExceptions(result, ex)).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Couldn't fetch jps compilation caches", e);
      onFail();
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
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
    //if (myWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE) {
    //  LOG.warn("Project automatic build should be disabled, it can affect portable caches");
    //  return false;
    //}
    //if (!JpsCacheStartupActivity.isLineEndingsConfiguredCorrectly()) {
    //  LOG.warn("Git line-endings not configured correctly for the project");
    //  return false;
    //}
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
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
      return;
    }

    myNettyClient.sendLatestDownloadCommitMessage(commitId);
    isCacheDownloaded = true;
    //PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
    //BuildManager.getInstance().clearState(myProject);
    //long endTime = System.nanoTime() - startTime;
    //ApplicationManager.getApplication().invokeLater(() -> {
    //  STANDARD
    //    .createNotification(JpsCacheBundle.message("notification.title.compiler.caches.loader"),
    //                        JpsCacheBundle.message("notification.content.update.compiler.caches.completed.successfully.in.s",
    //                                               endTime / 1_000_000_000),
    //                        NotificationType.INFORMATION)
    //    .notify(myProject);
    //});
    //DOWNLOAD_DURATION_EVENT_ID.log(endTime);
    //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(true);
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
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
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

  private void onFail() {
    //ApplicationManager.getApplication().invokeLater(() -> {
    //  ATTENTION.createNotification(JpsCacheBundle.message("notification.title.compiler.caches.loader"),
    //                               JpsCacheBundle.message("notification.content.update.compiler.caches.failed"), NotificationType.WARNING)
    //    .notify(myProject);
    //});
  }

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
      for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
        myOriginalBuildStatistic.put(type,  targetsState.getAverageBuildTime(type));
      }
      long totalCalculationTime = System.currentTimeMillis() - startTime;
      LOG.info("Calculated build time: " + estimatedBuildTime);
      LOG.info("Time spend to context initialization and time calculation: " + totalCalculationTime);
      return Pair.create(estimatedBuildTime, projectDescriptor.getProject().getModules().size());
    }
    catch (Exception e) {
      LOG.warn("Exception at calculation approximate build time", e);
    }
    return null;
  }
}
