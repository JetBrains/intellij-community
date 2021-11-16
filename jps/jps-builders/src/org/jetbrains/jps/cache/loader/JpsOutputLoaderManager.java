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
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.cache.client.JpsNettyClient;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.client.JpsServerConnectionUtil;
import org.jetbrains.jps.cache.git.GitCommitsIterator;
import org.jetbrains.jps.cache.git.GitRepositoryUtil;
import org.jetbrains.jps.cache.loader.JpsOutputLoader.LoaderStatus;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
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
import static org.jetbrains.jps.cache.JpsCachesPluginUtil.INTELLIJ_REPO_NAME;

public class JpsOutputLoaderManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(JpsOutputLoaderManager.class);
  private static final double SEGMENT_SIZE = 0.33;
  private final AtomicBoolean hasRunningTask;
  //private final CompilerWorkspaceConfiguration myWorkspaceConfiguration;
  private List<JpsOutputLoader<?>> myJpsOutputLoadersLoaders;
  private final JpsMetadataLoader myMetadataLoader;
  private final CanceledStatus myCanceledStatus;
  private final JpsServerClient myServerClient;
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
    myServerClient = JpsServerClient.getServerClient();
    myMetadataLoader = new JpsMetadataLoader(projectPath, myServerClient);
    //myWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    // Configure build manager
    //BuildManager buildManager = BuildManager.getInstance();
    //if (!buildManager.isGeneratePortableCachesEnabled()) buildManager.setGeneratePortableCachesEnabled(true);
  }

  public void measureConnectionSpeed() {
    JpsServerConnectionUtil.measureConnectionSpeed(myNettyClient);
  }

  public void load(boolean isForceUpdate, boolean verbose) {
    if (!canRunNewLoading()) return;
    Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate, verbose);
    if (commitInfo != null) {
      //assert myProject != null;
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingStarted();
      // Drop JPS metadata to force plugin for downloading all compilation outputs
      if (isForceUpdate) {
        myMetadataLoader.dropCurrentProjectMetadata();
        File outDir = new File(myBuildOutDir);
        if (outDir.exists()) {
          myNettyClient.sendMainStatusMessage(JpsBuildBundle.message("progress.text.clean.output.directories"));
          FileUtil.delete(outDir);
        }
        LOG.info("Compilation output folder empty");
      }
      startLoadingForCommit(commitInfo.first);
    }
    hasRunningTask.set(false);
  }

  //public void load(boolean isForceUpdate, boolean verbose) {
  //  Task.Backgroundable task = new Task.Backgroundable(myProject, JpsCacheBundle.message("progress.title.updating.compiler.caches")) {
  //    @Override
  //    public void run(@NotNull ProgressIndicator indicator) {
  //      Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate, verbose);
  //      if (commitInfo != null) {
  //        assert myProject != null;
  //        myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingStarted();
  //        // Drop JPS metadata to force plugin for downloading all compilation outputs
  //        if (isForceUpdate) {
  //          myMetadataLoader.dropCurrentProjectMetadata();
  //          File outDir = new File(myBuildOutDir);
  //          if (outDir.exists()) {
  //            indicator.setText(JpsCacheBundle.message("progress.text.clean.output.directories"));
  //            FileUtil.delete(outDir);
  //          }
  //          LOG.info("Compilation output folder empty");
  //        }
  //        startLoadingForCommit(commitInfo.first);
  //      }
  //      hasRunningTask.set(false);
  //    }
  //  };
  //
  //  if (!canRunNewLoading()) return;
  //  BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
  //  processIndicator.setIndeterminate(false);
  //  ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  //}

  @Nullable
  private Pair<String, Integer> getNearestCommit(boolean isForceUpdate, boolean verbose) {
    Map<String, Set<String>> availableCommitsPerRemote = myServerClient.getCacheKeysPerRemote(myNettyClient);

    String previousCommitId = GitRepositoryUtil.getLatestDownloadedCommit(myNettyClient);
    //String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
    //List<GitCommitsIterator> repositoryList = GitRepositoryUtil.getCommitsIterator(myProject, availableCommitsPerRemote.keySet());

    GitCommitsIterator commitsIterator = new GitCommitsIterator(myNettyClient, INTELLIJ_REPO_NAME);
    Set<String> availableCommitsForRemote = availableCommitsPerRemote.get(commitsIterator.getRemote());
    if (availableCommitsForRemote == null) {
      String message = JpsBuildBundle.message("notification.content.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      myNettyClient.sendMainStatusMessage(message);
      return null;
    }

    String commitId = "";
    int commitsBehind = 0;
    while (commitsIterator.hasNext() && !availableCommitsForRemote.contains(commitId)) {
      commitId = commitsIterator.next();
      commitsBehind++;
    }

    if (!availableCommitsForRemote.contains(commitId)) {
      String message = JpsBuildBundle.message("notification.content.not.found.any.caches.for.latest.commits.in.branch");
      LOG.warn(message);
      myNettyClient.sendMainStatusMessage(message);
      return null;
    }
    if (previousCommitId != null && commitId.equals(previousCommitId) && !isForceUpdate) {
      String message = JpsBuildBundle.message("notification.content.system.contains.up.to.date.caches");
      LOG.info(message);
      myNettyClient.sendMainStatusMessage(message);
      return null;
    }
    return Pair.create(commitId, commitsBehind);
  }

  private void startLoadingForCommit(@NotNull String commitId) {
    long startTime = System.nanoTime();
    myNettyClient.sendMainStatusMessage(JpsBuildBundle.message("progress.text.fetching.cache.for.commit", commitId));

    // Loading metadata for commit
    Map<String, Map<String, BuildTargetState>> commitSourcesState = myMetadataLoader.loadMetadataForCommit(myNettyClient, commitId);
    if (commitSourcesState == null) {
      LOG.warn("Couldn't load metadata for commit: " + commitId);
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
      return;
    }

    // Calculate downloads
    Map<String, Map<String, BuildTargetState>> currentSourcesState = myMetadataLoader.loadCurrentProjectMetadata();
    int totalDownloads =
      getLoaders().stream().mapToInt(loader -> loader.calculateDownloads(commitSourcesState, currentSourcesState)).sum();
    //indicator.setFraction(0.01);

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
          myNettyClient.sendMainStatusMessage(JpsBuildBundle.message("progress.text.rolling.back.downloaded.caches"));
        }
      }).handle((result, ex) -> handleExceptions(result, ex)).get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.warn("Couldn't fetch jps compilation caches", e);
      onFail();
      //myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
    }
  }

  //@Nullable
  //private static String getBuildOutDir(@NotNull Project project) {
  //  VirtualFile projectFile = project.getProjectFile();
  //  String projectBasePath = project.getBasePath();
  //  if (projectFile == null || projectBasePath == null) {
  //    LOG.warn("Project files doesn't exist");
  //    return null;
  //  }
  //  String fileExtension = projectFile.getExtension();
  //  if (fileExtension != null && fileExtension.equals("irp")) {
  //    LOG.warn("File base project not supported");
  //    return null;
  //  }
  //
  //  Path configFile = Paths.get(FileUtil.toCanonicalPath(projectFile.getPath()));
  //  Element componentTag = JDomSerializationUtil.findComponent(JpsLoaderBase.tryLoadRootElement(configFile), "ProjectRootManager");
  //  if (componentTag == null) {
  //    LOG.warn("Component tag in config file doesn't exist");
  //    return null;
  //  }
  //  Element output = componentTag.getChild(OUTPUT_TAG);
  //  if (output == null) {
  //    LOG.warn("Output tag in config file doesn't exist");
  //    return null;
  //  }
  //  String url = output.getAttributeValue(URL_ATTRIBUTE);
  //  if (url == null) {
  //    LOG.warn("URL attribute in output tag doesn't exist");
  //    return null;
  //  }
  //  return JpsPathUtil.urlToPath(url).replace("$" + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$", projectBasePath);
  //}

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
      myNettyClient.sendMainStatusMessage(JpsBuildBundle.message("progress.text.rolling.back"));
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
      myNettyClient.sendMainStatusMessage(JpsBuildBundle.message("progress.text.rolling.back.downloaded.caches"));
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
}
