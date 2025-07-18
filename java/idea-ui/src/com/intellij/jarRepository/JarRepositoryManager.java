// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository;

import com.intellij.CommonBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.services.MavenRepositoryServicesManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.eel.EelMachine;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.version.Version;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.aether.ArtifactDependencyNode;
import org.jetbrains.idea.maven.aether.ArtifactKind;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.jarRepository.JarRepositoryAuthenticationDataProviderKt.obtainAuthenticationData;

public final class JarRepositoryManager {
  private static final Logger LOG = Logger.getInstance(JarRepositoryManager.class);

  /**
   * * @deprecated Do not use it. See description for getLocalRepositoryPath
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public static final String MAVEN_REPOSITORY_MACRO = "$MAVEN_REPOSITORY$";

  private static final String DEFAULT_REPOSITORY_PATH = ".m2/repository";
  private static final AtomicInteger ourTasksInProgress = new AtomicInteger();

  private static final Map<String, OrderRootType> ourClassifierToRootType = new HashMap<>();

  // slf4j logger handles format strings with a throwable in the end
  private static final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(JarRepositoryManager.class);

  static {
    ourClassifierToRootType.put(ArtifactKind.ARTIFACT.getClassifier(), OrderRootType.CLASSES);
    ourClassifierToRootType.put(ArtifactKind.JAVADOC.getClassifier(), JavadocOrderRootType.getInstance());
    ourClassifierToRootType.put(ArtifactKind.SOURCES.getClassifier(), OrderRootType.SOURCES);
    ourClassifierToRootType.put(ArtifactKind.ANNOTATIONS.getClassifier(), AnnotationOrderRootType.getInstance());
  }

  static final ExecutorService DOWNLOADER_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor("RemoteLibraryDownloader",
                                                                                                          ProcessIOExecutorService.INSTANCE,
                                                                                                          4);

  // used in integration tests
  private static final boolean DO_REFRESH = SystemProperties.getBooleanProperty("idea.do.refresh.after.jps.library.downloaded", true);

  public static @NotNull NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Repository");
  }

  public static boolean hasRunningTasks() {
    return ourTasksInProgress.get() > 0;   // todo: count tasks on per-project basis?
  }

  public static @Nullable NewLibraryConfiguration chooseLibraryAndDownload(@NotNull Project project,
                                                                           @Nullable String initialFilter,
                                                                           JComponent parentComponent) {
    RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, initialFilter, RepositoryAttachDialog.Mode.DOWNLOAD);
    if (!dialog.showAndGet()) {
      return null;
    }

    final boolean attachSources = dialog.getAttachSources();
    final boolean attachJavaDoc = dialog.getAttachJavaDoc();
    final boolean attachAnnotations = dialog.getAttachExternalAnnotations();
    final String copyTo = dialog.getDirectoryPath();
    JpsMavenRepositoryLibraryDescriptor libraryDescriptor = dialog.getSelectedLibraryDescriptor();

    final EnumSet<ArtifactKind> artifactKinds = ArtifactKind.kindsOf(attachSources, attachJavaDoc, libraryDescriptor.getPackaging());
    if (attachAnnotations) {
      artifactKinds.add(ArtifactKind.ANNOTATIONS);
    }

    final NewLibraryConfiguration config = resolveAndDownload(
      project, libraryDescriptor, artifactKinds, copyTo, RemoteRepositoriesConfiguration.getInstance(project).getRepositories()
    );
    if (config == null) {
      Messages.showErrorDialog(parentComponent,
                               JavaUiBundle.message("error.message.no.files.were.downloaded.for.0", libraryDescriptor.getMavenId()),
                               CommonBundle.getErrorTitle());
    }
    return config;
  }

  public static @Nullable NewLibraryConfiguration resolveAndDownload(@NotNull Project project,
                                                                     @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                                     Set<ArtifactKind> kinds,
                                                                     String copyTo,
                                                                     Collection<RemoteRepositoryDescription> repositories) {
    final Collection<OrderRoot> roots = new ArrayList<>();
    if (descriptor.getMavenId() != null) {
      roots.addAll(loadDependenciesModal(project, descriptor, kinds, repositories, copyTo));
    }
    if (!roots.isEmpty()) {
      notifyArtifactsDownloaded(project, roots);
      return createNewLibraryConfiguration(new RepositoryLibraryProperties(descriptor), roots);
    }
    return null;
  }

  public static @Nullable NewLibraryConfiguration resolveAndDownload(@NotNull Project project,
                                                                     String coord,
                                                                     boolean attachSources,
                                                                     boolean attachJavaDoc,
                                                                     boolean includeTransitiveDependencies,
                                                                     String copyTo,
                                                                     Collection<RemoteRepositoryDescription> repositories) {
    JpsMavenRepositoryLibraryDescriptor libraryDescriptor =
      new JpsMavenRepositoryLibraryDescriptor(coord, includeTransitiveDependencies, Collections.emptyList());
    return resolveAndDownload(
      project, libraryDescriptor, ArtifactKind.kindsOf(attachSources, attachJavaDoc, libraryDescriptor.getPackaging()), copyTo, repositories
    );
  }

  private static @NotNull NewLibraryConfiguration createNewLibraryConfiguration(RepositoryLibraryProperties props,
                                                                                Collection<OrderRoot> roots) {
    return new NewLibraryConfiguration(
      suggestLibraryName(props),
      RepositoryLibraryType.getInstance(),
      props) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        editor.addRoots(roots);
      }
    };
  }

  private static String suggestLibraryName(RepositoryLibraryProperties props) {
    return
      Stream.of(props.getGroupId(), props.getArtifactId())
        .flatMap(s -> Stream.of(s.split("[.:-]")))
        .distinct()
        .filter(s -> !s.equals("com") && !s.equals("org"))
        .collect(Collectors.joining("."));
  }


  /**
   * this method is quickfix until we planning to support compilation with JPS, avoid to use it
   */
  @ApiStatus.Experimental
  public static @NotNull Path getJPSLocalMavenRepositoryForIdeaProject(@NotNull Project project) {
    String expanded = PathMacroManager.getInstance(project).expandPath(MAVEN_REPOSITORY_MACRO);
    if (!MAVEN_REPOSITORY_MACRO.equals(expanded) && !expanded.isEmpty()) {
      slf4jLogger.debug("RepositoryPath: return expanded macros: " + expanded);
      return Path.of(expanded);
    }

    return getLocalRepositoryPath().toPath();
  }

  /**
   * @deprecated Do not use it. See description for getLocalRepositoryPath
   */
  @Deprecated
  private static volatile File ourLocalRepositoryPath;

  /**
   * @deprecated Do not use it. There is no such thing like Local repository path without a project.
   * Actually, there is no such thing even for idea project, should be per-linked project
   * use getJPSLocalMavenRepositoryForIdeaProject
   */
  @Deprecated
  public static @NotNull File getLocalRepositoryPath() {
    File repoPath = ourLocalRepositoryPath;
    if (repoPath != null) {
      slf4jLogger.debug("RepositoryPath: return cached application-wide repo: " + repoPath );
      return repoPath;
    }
    final String expanded = PathMacroManager.getInstance(ApplicationManager.getApplication()).expandPath(MAVEN_REPOSITORY_MACRO);
    if (!MAVEN_REPOSITORY_MACRO.equals(expanded) && !expanded.isEmpty()) {
      repoPath = new File(expanded);
      if (repoPath.exists()) {
        try {
          repoPath = repoPath.getCanonicalFile();
        }
        catch (IOException ignored) {
        }
      }
      ourLocalRepositoryPath = repoPath;
      slf4jLogger.debug("RepositoryPath: return expanded application-wide repo: " + repoPath );
      return repoPath;
    }


    ourLocalRepositoryPath = getDefaultMavenLocalRepositoryPathNoRespectToSettings();
    slf4jLogger.debug("RepositoryPath: return default application-wide repo: " + repoPath );
    return ourLocalRepositoryPath;
  }

  private static File getDefaultMavenLocalRepositoryPathNoRespectToSettings() {
    final String userHome = System.getProperty("user.home", null);
    return userHome != null ? new File(userHome, DEFAULT_REPOSITORY_PATH) : new File(DEFAULT_REPOSITORY_PATH);
  }

  /**
   * @deprecated Do not use it. See description for getLocalRepositoryPath
   */
  @TestOnly
  @ApiStatus.Internal
  @Deprecated
  public static void setLocalRepositoryPath(File localRepo) {
    ourLocalRepositoryPath = localRepo;
  }

  public static Collection<OrderRoot> loadDependenciesModal(@NotNull Project project,
                                                            @NotNull RepositoryLibraryProperties libraryProps,
                                                            boolean loadSources,
                                                            boolean loadJavadoc,
                                                            @Nullable String copyTo,
                                                            @Nullable Collection<RemoteRepositoryDescription> repositories) {
    final JpsMavenRepositoryLibraryDescriptor libDescriptor = libraryProps.getRepositoryLibraryDescriptor();
    if (libDescriptor.getMavenId() != null) {
      EnumSet<ArtifactKind> kinds = ArtifactKind.kindsOf(loadSources, loadJavadoc, libraryProps.getPackaging());
      return loadDependenciesModal(project, libDescriptor, kinds, repositories, copyTo);
    }
    return Collections.emptyList();
  }

  public static Collection<OrderRoot> loadDependenciesModal(@NotNull Project project,
                                                            @NotNull JpsMavenRepositoryLibraryDescriptor desc,
                                                            final Set<ArtifactKind> artifactKinds,
                                                            @Nullable Collection<RemoteRepositoryDescription> repositories,
                                                            @Nullable String copyTo) {
    Collection<RemoteRepositoryDescription> effectiveRepos = selectRemoteRepositories(project, desc, repositories);
    return submitModalJob(
      project, JavaUiBundle.message("jar.repository.manager.dialog.resolving.dependencies.title", 1),
      newOrderRootResolveJob(project, desc, artifactKinds, effectiveRepos, copyTo)
    );
  }


  public static Promise<List<OrderRoot>> loadDependenciesAsync(@NotNull Project project,
                                                               RepositoryLibraryProperties libraryProps,
                                                               boolean loadSources,
                                                               boolean loadJavadoc,
                                                               @Nullable List<RemoteRepositoryDescription> repos,
                                                               @Nullable String copyTo) {
    EnumSet<ArtifactKind> kinds = ArtifactKind.kindsOf(loadSources, loadJavadoc, libraryProps.getPackaging());
    return loadDependenciesAsync(
      project,
      libraryProps.getRepositoryLibraryDescriptor(),
      kinds, repos, copyTo
    );
  }

  public static Promise<List<OrderRoot>> loadDependenciesAsync(@NotNull Project project,
                                                               JpsMavenRepositoryLibraryDescriptor desc,
                                                               final Set<ArtifactKind> artifactKinds,
                                                               @Nullable List<RemoteRepositoryDescription> repos,
                                                               @Nullable String copyTo) {
    Collection<RemoteRepositoryDescription> effectiveRepos = selectRemoteRepositories(project, desc, repos);
    return submitBackgroundJob(newOrderRootResolveJob(project, desc, artifactKinds, effectiveRepos, copyTo));
  }

  public static @Nullable List<OrderRoot> loadDependenciesSync(@NotNull Project project,
                                                               JpsMavenRepositoryLibraryDescriptor desc,
                                                               final Set<ArtifactKind> artifactKinds,
                                                               @Nullable List<RemoteRepositoryDescription> repos,
                                                               @Nullable String copyTo) {
    Collection<RemoteRepositoryDescription> effectiveRepos = selectRemoteRepositories(project, desc, repos);
    return submitSyncJob(newOrderRootResolveJob(project, desc, artifactKinds, effectiveRepos, copyTo));
  }

  /**
   * Load dependencies within the caller thread with the progress being tracked by the directly provided {@code progressIndicator}.
   */
  @ApiStatus.Internal
  public static @NotNull Collection<OrderRoot> loadDependenciesSync(@NotNull Project project,
                                                                    @NotNull RepositoryLibraryProperties libraryProps,
                                                                    boolean loadSources,
                                                                    boolean loadJavadoc,
                                                                    @Nullable String copyTo,
                                                                    @Nullable Collection<RemoteRepositoryDescription> repositories,
                                                                    @NotNull ProgressIndicator progressIndicator) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    final JpsMavenRepositoryLibraryDescriptor libDescriptor = libraryProps.getRepositoryLibraryDescriptor();
    if (libDescriptor.getMavenId() != null) {
      EnumSet<ArtifactKind> kinds = ArtifactKind.kindsOf(loadSources, loadJavadoc, libraryProps.getPackaging());
      Collection<RemoteRepositoryDescription> effectiveRepos = selectRemoteRepositories(project, libDescriptor, repositories);
      return newOrderRootResolveJob(project, libDescriptor, kinds, effectiveRepos, copyTo).apply(progressIndicator);
    }
    return Collections.emptyList();
  }

  /**
   * Get a list of remote repositories meeting the priority:
   * <ol>
   * <li>from {@code repositories} param if not null and not empty</li>
   * <li>from {@code desc} library descriptor found by {@link JpsMavenRepositoryLibraryDescriptor#getJarRepositoryId} if present</li>
   * <li>from {@link RemoteRepositoriesConfiguration#getRepositories()}</li>
   * </ol>
   *
   * @param project      Project instance
   * @param desc         Library descriptor
   * @param repositories Repositories to override any other values
   * @return Collection of remote repositories chosen from params.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public static List<RemoteRepositoryDescription> selectRemoteRepositories(@NotNull Project project,
                                                                    @Nullable JpsMavenRepositoryLibraryDescriptor desc,
                                                                    @Nullable Collection<RemoteRepositoryDescription> repositories) {
    if (repositories != null && !repositories.isEmpty()) {
      return repositories.stream().toList();
    }

    RemoteRepositoryDescription repositoryFromDescriptor = getRemoteRepositoryFromLibrary(project, desc);
    if (repositoryFromDescriptor != null) {
      return List.of(repositoryFromDescriptor);
    }

    return RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
  }

  private static @Nullable RemoteRepositoryDescription getRemoteRepositoryFromLibrary(@NotNull Project project,
                                                                                      @Nullable JpsMavenRepositoryLibraryDescriptor desc) {
    if (desc == null) {
      return null;
    }

    String repositoryId = desc.getJarRepositoryId();
    if (repositoryId == null) {
      return null;
    }

    return ContainerUtil.find(RemoteRepositoriesConfiguration.getInstance(project).getRepositories(),
                              it -> it.getId().equals(repositoryId));
  }

  public static @NotNull Promise<Collection<String>> getAvailableVersions(@NotNull Project project,
                                                                          @NotNull RepositoryLibraryDescription libraryDescription) {
    return getAvailableVersions(project, libraryDescription, Collections.emptyList());
  }

  public static @NotNull Promise<Collection<String>> getAvailableVersions(@NotNull Project project,
                                                                          @NotNull RepositoryLibraryDescription libraryDescription,
                                                                          @NotNull List<RemoteRepositoryDescription> repositories) {
    List<RemoteRepositoryDescription> repos = selectRemoteRepositories(project, null, repositories).stream().toList();
    return submitBackgroundJob(new VersionResolveJob(project, libraryDescription, repos));
  }

  public static @Nullable ArtifactDependencyNode loadDependenciesTree(@NotNull RepositoryLibraryDescription description,
                                                                      @NotNull String version,
                                                                      Project project) {
    List<RemoteRepositoryDescription> repositories = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    return submitModalJob(project, JavaUiBundle.message("jar.repository.manager.dialog.resolving.dependencies.title", 0),
                          new AetherJob<>(project, repositories) {
                            @Override
                            protected String getProgressText() {
                              return JavaUiBundle.message("jar.repository.manager.progress.text.loading.dependencies",
                                                          description.getMavenCoordinates(version));
                            }

                            @Override
                            protected ArtifactDependencyNode perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager)
                              throws Exception {
                              return manager.collectDependencies(description.getGroupId(), description.getArtifactId(), version);
                            }

                            @Override
                            protected ArtifactDependencyNode getDefaultResult() {
                              return null;
                            }
                          });
  }

  private static void notifyArtifactsDownloaded(Project project, Collection<OrderRoot> roots) {
    final StringBuilder sb = new StringBuilder();
    final String title = JavaUiBundle.message("jar.repository.manager.notification.title.downloaded");
    for (OrderRoot root : roots) {
      sb.append("<p/>");
      sb.append(root.getFile().getName());
    }
    final @NlsSafe String content = sb.toString();
    Notifications.Bus.notify(getNotificationGroup().createNotification(title, content, NotificationType.INFORMATION), project);
  }

  public static void searchArtifacts(Project project,
                                     String coord,
                                     Consumer<? super Collection<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>>> resultProcessor) {
    searchArtifacts(project, coord, JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING, resultProcessor);
  }

  public static void searchArtifacts(Project project,
                                     String coord,
                                     String packaging,
                                     Consumer<? super Collection<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>>> resultProcessor) {
    if (coord == null || coord.isEmpty()) {
      return;
    }
    final RepositoryArtifactDescription template;
    if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
      template = new RepositoryArtifactDescription(null, null, null, packaging, null, coord, null);
    }
    else {
      template = new RepositoryArtifactDescription(new RepositoryLibraryProperties(coord, packaging, true), null);
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaPsiBundle.message("task.background.title.maven"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final List<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>> resultList = new ArrayList<>();
        try {
          for (String serviceUrl : MavenRepositoryServicesManager.getServiceUrls(project)) {
            try {
              final List<RepositoryArtifactDescription> artifacts = MavenRepositoryServicesManager.findArtifacts(template, serviceUrl);
              if (!artifacts.isEmpty()) {
                final List<RemoteRepositoryDescription> repositories = MavenRepositoryServicesManager.getRepositories(serviceUrl);
                final Map<String, RemoteRepositoryDescription> map = new HashMap<>();
                for (RemoteRepositoryDescription repository : repositories) {
                  map.put(repository.getId(), repository);
                }
                for (RepositoryArtifactDescription artifact : artifacts) {
                  final RemoteRepositoryDescription repository = map.get(artifact.getRepositoryId());
                  // if the artifact is provided by an unsupported repository, just skip it
                  // because it won't be resolved anyway
                  if (repository != null) {
                    resultList.add(Pair.create(artifact, repository));
                  }
                }
              }
            }
            catch (Exception e) {
              LOG.error(e);
            }
          }
        }
        finally {
          ApplicationManager.getApplication().invokeLater(() -> resultProcessor.accept(resultList));
        }
      }
    });
  }

  public static void searchRepositories(Project project,
                                        Collection<String> serviceUrls,
                                        Processor<? super Collection<RemoteRepositoryDescription>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaPsiBundle.message("task.background.title.maven"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<RemoteRepositoryDescription>> result = Ref.create(Collections.emptyList());
        try {
          final ArrayList<RemoteRepositoryDescription> repoList = new ArrayList<>();
          for (String url : serviceUrls) {
            final List<RemoteRepositoryDescription> repositories;
            try {
              repositories = MavenRepositoryServicesManager.getRepositories(url);
            }
            catch (Exception ex) {
              LOG.warn("Accessing Service at: " + url, ex);
              continue;
            }
            repoList.addAll(repositories);
          }
          result.set(repoList);
        }
        catch (Exception e) {
          LOG.error(e);
        }
        finally {
          ApplicationManager.getApplication().invokeLater(() -> resultProcessor.process(result.get()));
        }
      }
    });
  }

  private static @Nullable <T> T submitSyncJob(@NotNull Function<? super ProgressIndicator, ? extends T> job) {
    try {
      ourTasksInProgress.incrementAndGet();
      ProgressIndicator parentIndicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      ProgressIndicator indicator = parentIndicator == null ? new EmptyProgressIndicator(ModalityState.defaultModalityState())
                                                            : ProgressWrapper.wrap(parentIndicator);
      return ProgressManager.getInstance().runProcess(() -> job.apply(indicator), indicator);
    }
    finally {
      ourTasksInProgress.decrementAndGet();
    }
  }

  private static @Nullable <T> T submitModalJob(@Nullable Project project,
                                                @NlsContexts.DialogTitle String title,
                                                Function<? super ProgressIndicator, ? extends T> job) {
    Ref<T> result = Ref.create(null);
    new Task.Modal(project, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          ourTasksInProgress.incrementAndGet();
          result.set(job.apply(indicator));
        }
        finally {
          ourTasksInProgress.decrementAndGet();
        }
      }
    }.queue();
    return result.get();
  }

  public static @NotNull <T> Promise<T> submitBackgroundJob(@NotNull Function<? super ProgressIndicator, ? extends T> job) {
    ModalityState startModality = ModalityState.defaultModalityState();
    AsyncPromise<T> promise = new AsyncPromise<>();
    DOWNLOADER_EXECUTOR.execute(() -> {
      if (promise.isCancelled()) {
        return;
      }

      try {
        ourTasksInProgress.incrementAndGet();
        final ProgressIndicator indicator = new EmptyProgressIndicator(startModality);
        T result = ProgressManager.getInstance().runProcess(() -> job.apply(indicator), indicator);
        promise.setResult(result);
      }
      catch (ProcessCanceledException ignored) {
        promise.cancel();
      }
      catch (Throwable e) {
        LOG.info(e);
        promise.setError(e);
      }
      finally {
        ourTasksInProgress.decrementAndGet();
      }
    });
    return promise;
  }

  private static @NotNull Collection<String> lookupVersionsImpl(String groupId,
                                                                String artifactId,
                                                                @NotNull ArtifactRepositoryManager manager) throws Exception {
    try {
      List<Version> versions = new ArrayList<>(manager.getAvailableVersions(groupId, artifactId, "[0,)", ArtifactKind.ARTIFACT));
      ArrayList<String> strings = new ArrayList<>(versions.size());
      for (int i = versions.size() - 1; i >= 0; i--) {
        strings.add(versions.get(i).toString());
      }
      return strings;
    }
    catch (TransferCancelledException e) {
      throw new ProcessCanceledException(e);
    }
  }

  private abstract static class AetherJob<T> implements Function<ProgressIndicator, T> {
    @NotNull private final Project myProject;
    private final @NotNull Collection<RemoteRepositoryDescription> myRepositories;

    AetherJob(@NotNull Project project, @NotNull Collection<RemoteRepositoryDescription> repositories) {
      myProject = project;
      myRepositories = repositories;
    }

    protected boolean canStart() {
      return !myRepositories.isEmpty();
    }

    @Override
    public final T apply(ProgressIndicator indicator) {
      if (canStart()) {
        indicator.setText(getProgressText());
        indicator.setIndeterminate(true);

        List<RemoteRepository> remotes = createRemoteRepositories(myRepositories);
        try {
          return perform(indicator, new ArtifactRepositoryManager(getJPSLocalMavenRepositoryForIdeaProject(myProject).toFile(), remotes,
                                                                  new ProgressConsumer() {
                                                                    @Override
                                                                    public void consume(@NlsContexts.ProgressText String message) {
                                                                      indicator.setText(message);
                                                                    }

                                                                    @Override
                                                                    public boolean isCanceled() {
                                                                      return indicator.isCanceled();
                                                                    }
                                                                  }));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }
      return getDefaultResult();
    }

    private static List<RemoteRepository> createRemoteRepositories(Collection<RemoteRepositoryDescription> repositoryDescriptions) {
      ArrayList<RemoteRepository> remotes = new ArrayList<>();
      for (RemoteRepositoryDescription repository : repositoryDescriptions) {
        ArtifactRepositoryManager.ArtifactAuthenticationData authData = obtainAuthenticationData(repository);
        remotes.add(
          ArtifactRepositoryManager.createRemoteRepository(repository.getId(), repository.getUrl(), authData, repository.isAllowSnapshots())
        );
      }
      return remotes;
    }

    protected abstract @NlsContexts.ProgressText String getProgressText();

    protected abstract T perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception;

    protected abstract T getDefaultResult();
  }

  private static Function<ProgressIndicator, List<OrderRoot>> newOrderRootResolveJob(@NotNull Project project,
                                                                                     @NotNull JpsMavenRepositoryLibraryDescriptor desc,
                                                                                     @NotNull Set<ArtifactKind> kinds,
                                                                                     @NotNull Collection<RemoteRepositoryDescription> repositories,
                                                                                     @Nullable String copyTo) {
    return new LibraryResolveJob(project, desc, kinds, repositories).andThen(
      resolved -> resolved.isEmpty() ? Collections.emptyList() : copyAndRefreshFiles(project, resolved, copyTo));
  }

  static List<OrderRoot> copyAndRefreshFiles(@NotNull Project project, @NotNull Collection<Artifact> artifacts, @Nullable String copyTo) {
    final List<OrderRoot> result = new ArrayList<>();
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    String repositoryPath = getJPSLocalMavenRepositoryForIdeaProject(project).toString();
    final EelMachine targetRepositoryMachine = EelProviderUtil.getEelDescriptor(Path.of(repositoryPath)).getMachine();
    for (Artifact each : artifacts) {
      long ms = System.currentTimeMillis();
      try {
        File repoFile = each.getFile();
        File toFile = repoFile;
        if (copyTo != null) {
          toFile = new File(copyTo, repoFile.getName());
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
        }
        else if (!targetRepositoryMachine.equals(EelProviderUtil.getEelDescriptor(Path.of(each.getFile().getPath())).getMachine())) {
          // if .m2 is located remotely, then we need to copy the files to the remote location
          String suffix = repoFile.getAbsolutePath().substring(repositoryPath.length());
          String actualPath = repositoryPath + suffix;
          toFile = new File(actualPath);
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
        }

        if (DO_REFRESH) {
          // search for a jar file first otherwise lib root won't be found!
          manager.refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(toFile.getPath()));
          final String url = VfsUtil.getUrlForLibraryRoot(toFile);
          final VirtualFile file = manager.refreshAndFindFileByUrl(url);
          if (file != null) {
            OrderRootType rootType = ourClassifierToRootType.getOrDefault(each.getClassifier(), OrderRootType.CLASSES);

            result.add(new OrderRoot(file, rootType));
          }
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      finally {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Artifact " + each.toString() + " refreshed in " + (System.currentTimeMillis() - ms) + "ms");
        }
      }
    }
    return result;
  }

  private static class LibraryResolveJob extends AetherJob<Collection<Artifact>> {
    private final @NotNull JpsMavenRepositoryLibraryDescriptor myDesc;
    private final @NotNull Set<ArtifactKind> myKinds;

    LibraryResolveJob(@NotNull Project project,
                      @NotNull JpsMavenRepositoryLibraryDescriptor desc,
                      @NotNull Set<ArtifactKind> kinds,
                      @NotNull Collection<RemoteRepositoryDescription> repositories) {
      super(project, repositories);
      myDesc = desc;
      myKinds = kinds;
    }

    @Override
    protected boolean canStart() {
      return super.canStart() && myDesc.getMavenId() != null;
    }

    @Override
    protected String getProgressText() {
      return JavaUiBundle.message("jar.repository.manager.library.resolve.progress.text",
                                  RepositoryLibraryDescription.findDescription(myDesc).getDisplayName());
    }

    @Override
    protected Collection<Artifact> getDefaultResult() {
      return Collections.emptyList();
    }

    @Override
    protected Collection<Artifact> perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception {
      var startTime = Instant.now();

      slf4jLogger.debug("LibraryResolveJob({}) #{} started", myDesc.getMavenId(), Thread.currentThread().getId());

      final String version = myDesc.getVersion();
      try {
        Collection<Artifact> artifacts = manager.resolveDependencyAsArtifact(myDesc.getGroupId(), myDesc.getArtifactId(), version, myKinds,
                                                                             myDesc.isIncludeTransitiveDependencies(),
                                                                             myDesc.getExcludedDependencies());

        slf4jLogger.debug("LibraryResolveJob({}) #{} successfully finished in {}ms with {} artifacts", myDesc.getMavenId(),
                          Thread.currentThread().getId(), Duration.between(startTime, Instant.now()).toMillis(),
                          artifacts.size());

        return artifacts;
      }
      catch (TransferCancelledException e) {
        slf4jLogger.debug("LibraryResolveJob({}) #{} failed in {}ms", myDesc.getMavenId(), Thread.currentThread().getId(),
                          Duration.between(startTime, Instant.now()).toMillis(), e);

        throw new ProcessCanceledException(e);
      }
      catch (RepositoryOfflineException e) {
        slf4jLogger.debug("LibraryResolveJob({}) #{} failed in {}ms", myDesc.getMavenId(), Thread.currentThread().getId(),
                          Duration.between(startTime, Instant.now()).toMillis(), e);

        throw e;
      }
      catch (Exception e) {
        slf4jLogger.warn("LibraryResolveJob({}) #{} failed in {}ms", myDesc.getMavenId(), Thread.currentThread().getId(),
                         Duration.between(startTime, Instant.now()).toMillis(), e);

        final String resolvedVersion = resolveVersion(manager, version);
        if (Objects.equals(version, resolvedVersion)) { // no changes
          throw e;
        }
        try {
          return manager.resolveDependencyAsArtifact(myDesc.getGroupId(), myDesc.getArtifactId(), resolvedVersion, myKinds,
                                                     myDesc.isIncludeTransitiveDependencies(), myDesc.getExcludedDependencies());
        }
        catch (TransferCancelledException e1) {
          throw new ProcessCanceledException(e1);
        }
      }
      finally {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Artifact " + myDesc + " resolved in " + Duration.between(startTime, Instant.now()).toMillis() + "ms");
        }
      }
    }

    private @Nullable String resolveVersion(final ArtifactRepositoryManager manager, final String version) throws Exception {
      final boolean isLatest = RepositoryLibraryDescription.LatestVersionId.equals(version);
      final boolean isRelease = RepositoryLibraryDescription.ReleaseVersionId.equals(version);
      if (isLatest || isRelease) {
        try {
          for (String ver : lookupVersionsImpl(myDesc.getGroupId(), myDesc.getArtifactId(), manager)) {
            if (!isRelease || !ver.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) {
              return ver;
            }
          }
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.error("Got unexpected exception while resolving artifact versions", e);
        }
      }
      return version;
    }
  }

  private static class VersionResolveJob extends AetherJob<Collection<String>> {
    private final @NotNull RepositoryLibraryDescription myDescription;

    VersionResolveJob(@NotNull Project project,
                      @NotNull RepositoryLibraryDescription repositoryLibraryDescription,
                      @NotNull List<RemoteRepositoryDescription> repositories) {
      super(project, repositories);

      myDescription = repositoryLibraryDescription;
    }

    @Override
    protected String getProgressText() {
      return JavaUiBundle.message("jar.repository.manager.version.resolve.progress.text", myDescription.getDisplayName());
    }

    @Override
    protected Collection<String> perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception {
      var startTime = Instant.now();

      slf4jLogger.debug("VersionResolveJob({}:{}) #{} started", myDescription.getGroupId(), myDescription.getArtifactId(),
                        Thread.currentThread().getId());
      try {
        return lookupVersionsImpl(myDescription.getGroupId(), myDescription.getArtifactId(), manager);
      }
      finally {
        slf4jLogger.debug("VersionResolveJob({}:{}) #{} finished in {}ms", myDescription.getGroupId(), myDescription.getArtifactId(),
                          Thread.currentThread().getId(), Duration.between(startTime, Instant.now()).toMillis());
      }
    }

    @Override
    protected Collection<String> getDefaultResult() {
      return Collections.emptyList();
    }
  }
}