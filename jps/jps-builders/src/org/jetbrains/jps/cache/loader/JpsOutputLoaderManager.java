//package org.jetbrains.jps.cache.loader;
//
//import com.intellij.compiler.CompilerWorkspaceConfiguration;
//import com.intellij.compiler.server.BuildManager;
//import com.intellij.compiler.server.PortableCachesLoadListener;
//import com.intellij.ide.util.PropertiesComponent;
//import com.intellij.jps.cache.JpsCacheBundle;
//import com.intellij.jps.cache.JpsCacheStartupActivity;
//import com.intellij.jps.cache.client.JpsServerClient;
//import com.intellij.jps.cache.git.GitCommitsIterator;
//import com.intellij.jps.cache.git.GitRepositoryUtil;
//import com.intellij.jps.cache.loader.JpsCompilationOutputLoader;
//import com.intellij.jps.cache.loader.JpsOutputLoader.LoaderStatus;
//import com.intellij.jps.cache.model.BuildTargetState;
//import com.intellij.jps.cache.model.JpsLoaderContext;
//import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
//import com.intellij.notification.NotificationAction;
//import com.intellij.notification.NotificationType;
//import com.intellij.openapi.Disposable;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.diagnostic.Logger;
//import com.intellij.openapi.progress.ProcessCanceledException;
//import com.intellij.openapi.progress.ProgressIndicator;
//import com.intellij.openapi.progress.ProgressManager;
//import com.intellij.openapi.progress.Task;
//import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.util.Pair;
//import com.intellij.openapi.util.io.FileUtil;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.util.containers.ContainerUtil;
//import org.jdom.Element;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.jps.cache.JpsCacheBundle;
//import org.jetbrains.jps.cache.client.JpsServerClient;
//import org.jetbrains.jps.cache.git.GitRepositoryUtil;
//import org.jetbrains.jps.cache.loader.JpsOutputLoader.LoaderStatus;
//import org.jetbrains.jps.cache.model.BuildTargetState;
//import org.jetbrains.jps.cache.model.JpsLoaderContext;
//import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;
//import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
//import org.jetbrains.jps.model.serialization.JpsLoaderBase;
//import org.jetbrains.jps.model.serialization.PathMacroUtil;
//import org.jetbrains.jps.util.JpsPathUtil;
//
//import java.io.File;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.*;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;
//import static com.intellij.jps.cache.statistics.JpsCacheUsagesCollector.*;
//import static com.intellij.jps.cache.ui.JpsLoaderNotifications.*;
//import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.OUTPUT_TAG;
//import static org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension.URL_ATTRIBUTE;
//
//public class JpsOutputLoaderManager implements Disposable {
//  private static final Logger LOG = Logger.getInstance(JpsOutputLoaderManager.class);
//  private static final String LATEST_COMMIT_ID = "JpsOutputLoaderManager.latestCommitId";
//  private static final double SEGMENT_SIZE = 0.33;
//  private final AtomicBoolean hasRunningTask;
//  private final CompilerWorkspaceConfiguration myWorkspaceConfiguration;
//  private List<JpsOutputLoader<?>> myJpsOutputLoadersLoaders;
//  private final JpsMetadataLoader myMetadataLoader;
//  private final JpsServerClient myServerClient;
//  private final String myBuildOutDir;
//  private final Project myProject;
//
//  @Override
//  public void dispose() { }
//
//  @NotNull
//  public static JpsOutputLoaderManager getInstance(@NotNull Project project) {
//    return project.getService(JpsOutputLoaderManager.class);
//  }
//
//  public JpsOutputLoaderManager(@NotNull Project project) {
//    myProject = project;
//    hasRunningTask = new AtomicBoolean();
//    myBuildOutDir = getBuildOutDir(myProject);
//    myServerClient = JpsServerClient.getServerClient();
//    myMetadataLoader = new JpsMetadataLoader(project, myServerClient);
//    myWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
//    // Configure build manager
//    BuildManager buildManager = BuildManager.getInstance();
//    if (!buildManager.isGeneratePortableCachesEnabled()) buildManager.setGeneratePortableCachesEnabled(true);
//  }
//
//  public void load(boolean isForceUpdate, boolean verbose) {
//    Task.Backgroundable task = new Task.Backgroundable(myProject, JpsCacheBundle.message("progress.title.updating.compiler.caches")) {
//      @Override
//      public void run(@NotNull ProgressIndicator indicator) {
//        Pair<String, Integer> commitInfo = getNearestCommit(isForceUpdate, verbose);
//        if (commitInfo != null) {
//          assert myProject != null;
//          myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingStarted();
//          // Drop JPS metadata to force plugin for downloading all compilation outputs
//          if (isForceUpdate) {
//            myMetadataLoader.dropCurrentProjectMetadata();
//            File outDir = new File(myBuildOutDir);
//            if (outDir.exists()) {
//              indicator.setText(JpsCacheBundle.message("progress.text.clean.output.directories"));
//              FileUtil.delete(outDir);
//            }
//            LOG.info("Compilation output folder empty");
//          }
//          startLoadingForCommit(commitInfo.first);
//        }
//        hasRunningTask.set(false);
//      }
//    };
//
//    if (!canRunNewLoading()) return;
//    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
//    processIndicator.setIndeterminate(false);
//    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
//  }
//
//  public void notifyAboutNearestCache() {
//    Pair<String, Integer> commitInfo = getNearestCommit(false, false);
//    if (commitInfo == null) return;
//
//    String notificationContent = commitInfo.second == 1
//                                 ? JpsCacheBundle.message("notification.content.caches.are.for.current.commit")
//                                 : JpsCacheBundle
//                                   .message("notification.content.caches.are.for.commit.commits.prior.to.yours", commitInfo.second - 1);
//
//    ApplicationManager.getApplication().invokeLater(() -> {
//      STANDARD
//        .createNotification(JpsCacheBundle.message("notification.title.compiler.caches.available"), notificationContent,
//                            NotificationType.INFORMATION)
//        .addAction(NotificationAction.createSimpleExpiring(
//          JpsCacheBundle.message("action.NotificationAction.JpsOutputLoaderManager.text.update.caches"),
//          () -> {
//            DOWNLOAD_THROUGH_NOTIFICATION_EVENT_ID.log();
//            load(false, false);
//          }))
//        .notify(myProject);
//    });
//  }
//
//  @Nullable
//  private Pair<String, Integer> getNearestCommit(boolean isForceUpdate, boolean verbose) {
//    Map<String, Set<String>> availableCommitsPerRemote = myServerClient.getCacheKeysPerRemote(myProject);
//
//    String previousCommitId = PropertiesComponent.getInstance().getValue(LATEST_COMMIT_ID);
//    List<GitCommitsIterator> repositoryList = GitRepositoryUtil.getCommitsIterator(myProject, availableCommitsPerRemote.keySet());
//    String commitId = "";
//    int commitsBehind = 0;
//    Set<String> availableCommitsForRemote = new HashSet<>();
//    for (GitCommitsIterator commitsIterator : repositoryList) {
//      availableCommitsForRemote = availableCommitsPerRemote.get(commitsIterator.getRemote());
//      if (availableCommitsForRemote.contains(commitId)) continue;
//      commitsBehind = 0;
//      while (commitsIterator.hasNext() && !availableCommitsForRemote.contains(commitId)) {
//        commitId = commitsIterator.next();
//        commitsBehind++;
//      }
//    }
//    var group = verbose ? STANDARD : EVENT_LOG;
//    if (!availableCommitsForRemote.contains(commitId)) {
//      String warning = JpsCacheBundle.message("notification.content.not.found.any.caches.for.latest.commits.in.branch");
//      LOG.warn(warning);
//      ApplicationManager.getApplication().invokeLater(() -> {
//        group.createNotification(JpsCacheBundle.message("notification.title.jps.caches.downloader"), warning, NotificationType.WARNING)
//          .notify(myProject);
//      });
//      return null;
//    }
//    if (previousCommitId != null && commitId.equals(previousCommitId) && !isForceUpdate) {
//      String info = JpsCacheBundle.message("notification.content.system.contains.up.to.date.caches");
//      LOG.info(info);
//      ApplicationManager.getApplication().invokeLater(() -> {
//        group.createNotification(JpsCacheBundle.message("notification.title.jps.caches.downloader"), info, NotificationType.INFORMATION)
//          .notify(myProject);
//      });
//      return null;
//    }
//    return Pair.create(commitId, commitsBehind);
//  }
//
//  private void startLoadingForCommit(@NotNull String commitId) {
//    long startTime = System.nanoTime();
//    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
//    indicator.setText(JpsCacheBundle.message("progress.text.fetching.cache.for.commit", commitId));
//
//    // Loading metadata for commit
//    Map<String, Map<String, BuildTargetState>> commitSourcesState = myMetadataLoader.loadMetadataForCommit(commitId);
//    if (commitSourcesState == null) {
//      LOG.warn("Couldn't load metadata for commit: " + commitId);
//      myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
//      return;
//    }
//
//    // Calculate downloads
//    Map<String, Map<String, BuildTargetState>> currentSourcesState = myMetadataLoader.loadCurrentProjectMetadata();
//    int totalDownloads =
//      getLoaders(myProject).stream().mapToInt(loader -> loader.calculateDownloads(commitSourcesState, currentSourcesState)).sum();
//    indicator.setFraction(0.01);
//
//    try {
//      // Computation with loaders results. If at least one of them failed rollback all job
//      initLoaders(commitId, indicator, totalDownloads, commitSourcesState, currentSourcesState).thenAccept(loaderStatus -> {
//        LOG.info("Loading finished with " + loaderStatus + " status");
//        try {
//          SegmentedProgressIndicatorManager indicatorManager =
//            new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
//          CompletableFuture.allOf(getLoaders(myProject).stream()
//                                    .map(loader -> applyChanges(loaderStatus, loader, indicator, indicatorManager))
//                                    .toArray(CompletableFuture[]::new))
//            .thenRun(() -> saveStateAndNotify(loaderStatus, commitId, startTime))
//            .get();
//        }
//        catch (InterruptedException | ExecutionException e) {
//          LOG.warn("Unexpected exception rollback all progress", e);
//          onFail();
//          getLoaders(myProject).forEach(loader -> loader.rollback());
//          myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
//          indicator.setText(JpsCacheBundle.message("progress.text.rolling.back.downloaded.caches"));
//        }
//      }).handle((result, ex) -> handleExceptions(result, ex, indicator)).get();
//    }
//    catch (InterruptedException | ExecutionException e) {
//      LOG.warn("Couldn't fetch jps compilation caches", e);
//      onFail();
//      myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
//    }
//  }
//
//  @Nullable
//  private static String getBuildOutDir(@NotNull Project project) {
//    VirtualFile projectFile = project.getProjectFile();
//    String projectBasePath = project.getBasePath();
//    if (projectFile == null || projectBasePath == null) {
//      LOG.warn("Project files doesn't exist");
//      return null;
//    }
//    String fileExtension = projectFile.getExtension();
//    if (fileExtension != null && fileExtension.equals("irp")) {
//      LOG.warn("File base project not supported");
//      return null;
//    }
//
//    Path configFile = Paths.get(FileUtil.toCanonicalPath(projectFile.getPath()));
//    Element componentTag = JDomSerializationUtil.findComponent(JpsLoaderBase.tryLoadRootElement(configFile), "ProjectRootManager");
//    if (componentTag == null) {
//      LOG.warn("Component tag in config file doesn't exist");
//      return null;
//    }
//    Element output = componentTag.getChild(OUTPUT_TAG);
//    if (output == null) {
//      LOG.warn("Output tag in config file doesn't exist");
//      return null;
//    }
//    String url = output.getAttributeValue(URL_ATTRIBUTE);
//    if (url == null) {
//      LOG.warn("URL attribute in output tag doesn't exist");
//      return null;
//    }
//    return JpsPathUtil.urlToPath(url).replace("$" + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$", projectBasePath);
//  }
//
//  private synchronized boolean canRunNewLoading() {
//    if (hasRunningTask.get()) {
//      LOG.warn("Jps cache loading already in progress, can't start the new one");
//      return false;
//    }
//    if (myBuildOutDir == null) {
//      LOG.warn("Build output dir is not configured for the project");
//      return false;
//    }
//    if (myWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE) {
//      LOG.warn("Project automatic build should be disabled, it can affect portable caches");
//      return false;
//    }
//    if (!JpsCacheStartupActivity.isLineEndingsConfiguredCorrectly()) {
//      LOG.warn("Git line-endings not configured correctly for the project");
//      return false;
//    }
//    hasRunningTask.set(true);
//    return true;
//  }
//
//  private <T> CompletableFuture<LoaderStatus> initLoaders(String commitId, ProgressIndicator indicator, int totalDownloads,
//                                                          Map<String, Map<String, BuildTargetState>> commitSourcesState,
//                                                          Map<String, Map<String, BuildTargetState>> currentSourcesState) {
//    List<JpsOutputLoader<?>> loaders = getLoaders(myProject);
//
//    // Create indicator with predefined segment size
//    SegmentedProgressIndicatorManager downloadIndicatorManager =
//      new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
//    SegmentedProgressIndicatorManager extractIndicatorManager =
//      new SegmentedProgressIndicatorManager(indicator, totalDownloads, SEGMENT_SIZE);
//    JpsLoaderContext loaderContext =
//      JpsLoaderContext.createNewContext(commitId, downloadIndicatorManager, commitSourcesState, currentSourcesState);
//
//    // Start loaders with own context
//    List<CompletableFuture<LoaderStatus>> completableFutures = ContainerUtil.map(loaders, loader ->
//      CompletableFuture.supplyAsync(() -> loader.extract(loader.load(loaderContext), extractIndicatorManager), INSTANCE));
//
//    // Reduce loaders statuses into the one
//    CompletableFuture<LoaderStatus> initialFuture = completableFutures.get(0);
//    if (completableFutures.size() > 1) {
//      for (int i = 1; i < completableFutures.size(); i++) {
//        initialFuture = initialFuture.thenCombine(completableFutures.get(i), JpsOutputLoaderManager::combine);
//      }
//    }
//    return initialFuture;
//  }
//
//  private static CompletableFuture<Void> applyChanges(LoaderStatus loaderStatus, JpsOutputLoader<?> loader, ProgressIndicator indicator,
//                                                      SegmentedProgressIndicatorManager indicatorManager) {
//    if (loaderStatus == LoaderStatus.FAILED) {
//      indicator.setText(JpsCacheBundle.message("progress.text.rolling.back"));
//      return CompletableFuture.runAsync(() -> loader.rollback(), INSTANCE);
//    }
//    return CompletableFuture.runAsync(() -> loader.apply(indicatorManager), INSTANCE);
//  }
//
//  private void saveStateAndNotify(LoaderStatus loaderStatus, String commitId, long startTime) {
//    if (loaderStatus == LoaderStatus.FAILED) {
//      onFail();
//      myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
//      return;
//    }
//
//    PropertiesComponent.getInstance().setValue(LATEST_COMMIT_ID, commitId);
//    BuildManager.getInstance().clearState(myProject);
//    long endTime = System.nanoTime() - startTime;
//    ApplicationManager.getApplication().invokeLater(() -> {
//      STANDARD
//        .createNotification(JpsCacheBundle.message("notification.title.compiler.caches.loader"),
//                            JpsCacheBundle.message("notification.content.update.compiler.caches.completed.successfully.in.s",
//                                                   endTime / 1_000_000_000),
//                            NotificationType.INFORMATION)
//        .notify(myProject);
//    });
//    DOWNLOAD_DURATION_EVENT_ID.log(endTime);
//    myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(true);
//    LOG.info("Loading finished");
//  }
//
//  private Void handleExceptions(Void result, Throwable ex, ProgressIndicator indicator) {
//    if (ex != null) {
//      Throwable cause = ex.getCause();
//      if (cause instanceof ProcessCanceledException) {
//        LOG.info("Jps caches download canceled");
//      }
//      else {
//        LOG.warn("Couldn't fetch jps compilation caches", ex);
//        onFail();
//      }
//      getLoaders(myProject).forEach(loader -> loader.rollback());
//      myProject.getMessageBus().syncPublisher(PortableCachesLoadListener.TOPIC).loadingFinished(false);
//      indicator.setText(JpsCacheBundle.message("progress.text.rolling.back.downloaded.caches"));
//    }
//    return result;
//  }
//
//  private List<JpsOutputLoader<?>> getLoaders(@NotNull Project project) {
//    if (myJpsOutputLoadersLoaders != null) return myJpsOutputLoadersLoaders;
//    myJpsOutputLoadersLoaders = Arrays.asList(new JpsCacheLoader(myServerClient, project),
//                                              new JpsCompilationOutputLoader(myServerClient, myBuildOutDir));
//    return myJpsOutputLoadersLoaders;
//  }
//
//  private static LoaderStatus combine(LoaderStatus firstStatus, LoaderStatus secondStatus) {
//    if (firstStatus == LoaderStatus.FAILED || secondStatus == LoaderStatus.FAILED) return LoaderStatus.FAILED;
//    return LoaderStatus.COMPLETE;
//  }
//
//  private void onFail() {
//    ApplicationManager.getApplication().invokeLater(() -> {
//      ATTENTION.createNotification(JpsCacheBundle.message("notification.title.compiler.caches.loader"),
//                                   JpsCacheBundle.message("notification.content.update.compiler.caches.failed"), NotificationType.WARNING)
//        .notify(myProject);
//    });
//  }
//}
