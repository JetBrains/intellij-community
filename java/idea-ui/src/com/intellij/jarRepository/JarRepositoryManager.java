// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository;

import com.intellij.CommonBundle;
import com.intellij.jarRepository.services.MavenRepositoryServicesManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.aether.ArtifactDependencyNode;
import org.jetbrains.idea.maven.aether.ArtifactKind;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class JarRepositoryManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler");

  private static final String MAVEN_REPOSITORY_MACRO = "$MAVEN_REPOSITORY$";
  private static final String DEFAULT_REPOSITORY_PATH = ".m2/repository";
  private static final AtomicInteger ourTasksInProgress = new AtomicInteger();

  private static final Map<String, OrderRootType> ourClassifierToRootType = new HashMap<>();

  static {
    ourClassifierToRootType.put(ArtifactKind.ARTIFACT.getClassifier(), OrderRootType.CLASSES);
    ourClassifierToRootType.put(ArtifactKind.JAVADOC.getClassifier(), JavadocOrderRootType.getInstance());
    ourClassifierToRootType.put(ArtifactKind.SOURCES.getClassifier(), OrderRootType.SOURCES);
    ourClassifierToRootType.put(ArtifactKind.ANNOTATIONS.getClassifier(), AnnotationOrderRootType.getInstance());
  }

  private static class JobExecutor {
    static final ExecutorService INSTANCE = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("RemoteLibraryDownloader");
  }

  public static boolean hasRunningTasks() {
    return ourTasksInProgress.get() > 0;   // todo: count tasks on per-project basis?
  }

  @Nullable
  public static NewLibraryConfiguration chooseLibraryAndDownload(@NotNull Project project, @Nullable String initialFilter, JComponent parentComponent) {
    RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, initialFilter, RepositoryAttachDialog.Mode.DOWNLOAD);
    if (!dialog.showAndGet()) {
      return null;
    }

    final String coord = dialog.getCoordinateText();
    final boolean attachSources = dialog.getAttachSources();
    final boolean attachJavaDoc = dialog.getAttachJavaDoc();
    final boolean attachAnnotations = dialog.getAttachExternalAnnotations();
    boolean includeTransitiveDependencies = dialog.getIncludeTransitiveDependencies();
    final String copyTo = dialog.getDirectoryPath();

    final EnumSet<ArtifactKind> artifactKinds = kindsOf(attachSources, attachJavaDoc);
    if (attachAnnotations) {
      artifactKinds.add(ArtifactKind.ANNOTATIONS);
    }

    final NewLibraryConfiguration config = resolveAndDownload(
      project, coord, artifactKinds, includeTransitiveDependencies, copyTo, RemoteRepositoriesConfiguration.getInstance(project).getRepositories()
    );
    if (config == null) {
      Messages.showErrorDialog(parentComponent, "No files were downloaded for " + coord, CommonBundle.getErrorTitle());
    }
    return config;
  }

  private static NewLibraryConfiguration resolveAndDownload(Project project,
                                                            String coord,
                                                            EnumSet<ArtifactKind> kinds,
                                                            boolean includeTransitiveDependencies,
                                                            String copyTo,
                                                            Collection<RemoteRepositoryDescription> repositories) {
    String packaging = JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING;
    for (ArtifactKind kind : kinds) {
      if (kind.getClassifier().isEmpty()) {
        packaging = kind.getExtension(); // correct packaging according to the requested artifact kind
        break;
      }
    }
    RepositoryLibraryProperties props = new RepositoryLibraryProperties(coord, packaging, includeTransitiveDependencies);
    final JpsMavenRepositoryLibraryDescriptor libDescriptor = props.getRepositoryLibraryDescriptor();
    final Collection<OrderRoot> roots = ContainerUtil.newArrayList();
    if (libDescriptor.getMavenId() != null) {
      roots.addAll(loadDependenciesModal(project, libDescriptor, kinds, repositories, copyTo));
    }

    if (!roots.isEmpty()) {
      notifyArtifactsDownloaded(project, roots);
      return createNewLibraryConfiguration(props, roots);
    }
    return null;
  }

  @Nullable
  public static NewLibraryConfiguration resolveAndDownload(@NotNull Project project,
                                                            String coord,
                                                            boolean attachSources,
                                                            boolean attachJavaDoc,
                                                            boolean includeTransitiveDependencies,
                                                            String copyTo,
                                                            Collection<RemoteRepositoryDescription> repositories) {
    return resolveAndDownload(project, coord, attachSources, attachJavaDoc, JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING,
                              includeTransitiveDependencies, copyTo, repositories);
  }

  @Nullable
  public static NewLibraryConfiguration resolveAndDownload(@NotNull Project project,
                                                            String coord,
                                                            boolean attachSources,
                                                            boolean attachJavaDoc,
                                                            String packaging,
                                                            boolean includeTransitiveDependencies,
                                                            String copyTo,
                                                            Collection<RemoteRepositoryDescription> repositories) {
    return resolveAndDownload(project, coord, kindsOf(attachSources, attachJavaDoc, packaging), includeTransitiveDependencies, copyTo, repositories);
  }

  @NotNull
  protected static NewLibraryConfiguration createNewLibraryConfiguration(RepositoryLibraryProperties props, Collection<? extends OrderRoot> roots) {
    return new NewLibraryConfiguration(
      RepositoryLibraryDescription.findDescription(props).getDisplayName(props.getVersion()),
      RepositoryLibraryType.getInstance(),
      props) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        editor.addRoots(roots);
      }
    };
  }


  private static volatile File ourLocalRepositoryPath;
  @NotNull
  public static File getLocalRepositoryPath() {
    File repoPath = ourLocalRepositoryPath;
    if (repoPath != null) {
      return repoPath;
    }
    final String expanded = PathMacroManager.getInstance(ApplicationManager.getApplication()).expandPath(MAVEN_REPOSITORY_MACRO);
    if (!MAVEN_REPOSITORY_MACRO.equals(expanded)) {
      repoPath = new File(expanded);
      if (repoPath.exists()) {
        try {
          repoPath = repoPath.getCanonicalFile();
        }
        catch (IOException ignored) {
        }
        ourLocalRepositoryPath = repoPath;
        return repoPath;
      }
    }
    final String userHome = System.getProperty("user.home", null);
    repoPath = userHome != null ? new File(userHome, DEFAULT_REPOSITORY_PATH) : new File(DEFAULT_REPOSITORY_PATH);
    ourLocalRepositoryPath = repoPath;
    return repoPath;
  }

  @TestOnly
  static void setLocalRepositoryPath(File localRepo) {
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
      EnumSet<ArtifactKind> kinds = kindsOf(loadSources, loadJavadoc, libraryProps.getPackaging());
      return loadDependenciesModal(project, libDescriptor, kinds, repositories, copyTo);
    }
    return Collections.emptyList();
  }

  public static Collection<OrderRoot> loadDependenciesModal(@NotNull Project project,
                                                            @NotNull JpsMavenRepositoryLibraryDescriptor desc,
                                                            final Set<ArtifactKind> artifactKinds,
                                                            @Nullable Collection<RemoteRepositoryDescription> repositories,
                                                            @Nullable String copyTo) {
    Collection<RemoteRepositoryDescription> effectiveRepos = addDefaultsIfEmpty(project, repositories);
    return submitModalJob(
      project, "Resolving Maven dependencies...", newOrderRootResolveJob(desc, artifactKinds, effectiveRepos, copyTo)
    );
  }


  public static Promise<List<OrderRoot>> loadDependenciesAsync(@NotNull Project project,
                                                               RepositoryLibraryProperties libraryProps,
                                                               boolean loadSources,
                                                               boolean loadJavadoc,
                                                               @Nullable List<RemoteRepositoryDescription> repos,
                                                               @Nullable String copyTo) {
    EnumSet<ArtifactKind> kinds = kindsOf(loadSources, loadJavadoc, libraryProps.getPackaging());
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
    Collection<RemoteRepositoryDescription> effectiveRepos = addDefaultsIfEmpty(project, repos);
    return submitBackgroundJob(newOrderRootResolveJob(desc, artifactKinds, effectiveRepos, copyTo));
  }

  @Nullable
  public static List<OrderRoot> loadDependenciesSync(@NotNull Project project,
                                                               JpsMavenRepositoryLibraryDescriptor desc,
                                                               final Set<ArtifactKind> artifactKinds,
                                                               @Nullable List<RemoteRepositoryDescription> repos,
                                                               @Nullable String copyTo) {
    Collection<RemoteRepositoryDescription> effectiveRepos = addDefaultsIfEmpty(project, repos);
    return submitSyncJob(newOrderRootResolveJob(desc, artifactKinds, effectiveRepos, copyTo));
  }

  @NotNull
  protected static Collection<RemoteRepositoryDescription> addDefaultsIfEmpty(@NotNull Project project,
                                                                              @Nullable Collection<RemoteRepositoryDescription> repositories) {
    if (repositories == null || repositories.isEmpty()) {
      repositories = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    }
    return repositories;
  }

  protected static EnumSet<ArtifactKind> kindsOf(boolean loadSources, boolean loadJavadoc, String... artifactPackaging) {
    final EnumSet<ArtifactKind> kinds = ArtifactKind.kindsOf(loadSources, loadJavadoc);
    if (artifactPackaging.length == 0) {
      kinds.add(ArtifactKind.ARTIFACT);
    }
    else {
      for (String packaging : artifactPackaging) {
        final ArtifactKind artifact = ArtifactKind.find(ArtifactKind.ARTIFACT.getClassifier(), packaging);
        if (artifact != null) {
          kinds.add(artifact);
        }
      }
    }
    return kinds;
  }

  @NotNull
  public static Promise<Collection<String>> getAvailableVersions(@NotNull Project project, @NotNull RepositoryLibraryDescription libraryDescription) {
    List<RemoteRepositoryDescription> repos = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    return submitBackgroundJob(new VersionResolveJob(libraryDescription, repos));
  }

  @Nullable
  public static ArtifactDependencyNode loadDependenciesTree(@NotNull RepositoryLibraryDescription description, @NotNull String version, Project project) {
    List<RemoteRepositoryDescription> repositories = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    return submitModalJob(project, "Resolving Maven Dependencies", new AetherJob<ArtifactDependencyNode>(repositories) {
      @Override
      protected String getProgressText() {
        return "Loading dependencies of " + description.getMavenCoordinates(version);
      }

      @Override
      protected ArtifactDependencyNode perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception {
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
    final String title = "The following files were downloaded:";
    for (OrderRoot root : roots) {
      sb.append("<p/>");
      sb.append(root.getFile().getName());
    }
    Notifications.Bus.notify(new Notification("Repository", title, sb.toString(), NotificationType.INFORMATION), project);
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
    if (coord == null || coord.length() == 0) {
      return;
    }
    final RepositoryArtifactDescription template;
    if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
      template = new RepositoryArtifactDescription(null, null, null, packaging, null, coord, null);
    }
    else {
      template = new RepositoryArtifactDescription(new RepositoryLibraryProperties(coord, packaging, true), null);
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final List<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>> resultList = new ArrayList<>();
        try {
          for (String serviceUrl : MavenRepositoryServicesManager.getServiceUrls(project)) {
            try {
              final List<RepositoryArtifactDescription> artifacts = MavenRepositoryServicesManager.findArtifacts(template, serviceUrl);
              if (!artifacts.isEmpty()) {
                final List<RemoteRepositoryDescription> repositories = MavenRepositoryServicesManager.getRepositories(serviceUrl);
                final Map<String, RemoteRepositoryDescription> map = new THashMap<>();
                for (RemoteRepositoryDescription repository : repositories) {
                  map.put(repository.getId(), repository);
                }
                for (RepositoryArtifactDescription artifact : artifacts) {
                  final RemoteRepositoryDescription repository = map.get(artifact.getRepositoryId());
                  // if the artifact is provided by an unsupported repository just skip it
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
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {
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

  @Nullable
  private static <T> T submitSyncJob(@NotNull Function<? super ProgressIndicator, ? extends T> job) {
    try {
      ourTasksInProgress.incrementAndGet();
      ProgressIndicator indicator = new EmptyProgressIndicator(ModalityState.defaultModalityState());
      return ProgressManager.getInstance().runProcess(() -> job.apply(indicator), indicator);
    }
    finally {
      ourTasksInProgress.decrementAndGet();
    }
  }

  @Nullable
  private static <T> T submitModalJob(@Nullable Project project, String title, Function<? super ProgressIndicator, ? extends T> job) {
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

  @NotNull
  private static <T> Promise<T> submitBackgroundJob(@NotNull Function<? super ProgressIndicator, ? extends T> job) {
    ModalityState startModality = ModalityState.defaultModalityState();
    AsyncPromise<T> promise = new AsyncPromise<>();
    JobExecutor.INSTANCE.submit(() -> {
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

  @NotNull
  private static Collection<String> lookupVersionsImpl(String groupId, String artifactId, @NotNull ArtifactRepositoryManager manager) throws Exception {
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

  private static abstract class AetherJob<T> implements Function<ProgressIndicator, T> {
    @NotNull
    private final Collection<? extends RemoteRepositoryDescription> myRepositories;

    AetherJob(@NotNull Collection<? extends RemoteRepositoryDescription> repositories) {
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

        final ArrayList<RemoteRepository> remotes = new ArrayList<>();
        for (RemoteRepositoryDescription repository : myRepositories) {
          remotes.add(ArtifactRepositoryManager.createRemoteRepository(repository.getId(), repository.getUrl()));
        }
        try {
          return perform(indicator, new ArtifactRepositoryManager(getLocalRepositoryPath(), remotes, new ProgressConsumer() {
            @Override
            public void consume(String message) {
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

    protected abstract String getProgressText();
    protected abstract T perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception;
    protected abstract T getDefaultResult();
  }

  private static Function<ProgressIndicator, List<OrderRoot>> newOrderRootResolveJob(@NotNull JpsMavenRepositoryLibraryDescriptor desc,
                                                                                     @NotNull Set<ArtifactKind> kinds,
                                                                                     @NotNull Collection<RemoteRepositoryDescription> repositories,
                                                                                     @Nullable String copyTo) {
    return new LibraryResolveJob(desc, kinds, repositories).andThen(
      resolved -> resolved.isEmpty() ? Collections.<OrderRoot>emptyList() : WriteAction.computeAndWait(() -> createRoots(resolved, copyTo)));
  }

  private static List<OrderRoot> createRoots(@NotNull Collection<? extends Artifact> artifacts, @Nullable String copyTo) {
    final List<OrderRoot> result = new ArrayList<>();
    final VirtualFileManager manager = VirtualFileManager.getInstance();
    for (Artifact each : artifacts) {
      try {
        File repoFile = each.getFile();
        File toFile = repoFile;
        if (copyTo != null) {
          toFile = new File(copyTo, repoFile.getName());
          if (repoFile.exists()) {
            FileUtil.copy(repoFile, toFile);
          }
        }
        // search for jar file first otherwise lib root won't be found!
        manager.refreshAndFindFileByUrl(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(toFile.getPath())));
        final String url = VfsUtil.getUrlForLibraryRoot(toFile);
        final VirtualFile file = manager.refreshAndFindFileByUrl(url);
        if (file != null) {
          OrderRootType rootType = ourClassifierToRootType.getOrDefault(each.getClassifier(), OrderRootType.CLASSES);

          result.add(new OrderRoot(file, rootType));
        }
      }
      catch (IOException e) {
        LOG.warn(e);
      }
    }
    return result;
  }

  private static class LibraryResolveJob extends AetherJob<Collection<Artifact>> {
    @NotNull
    private final JpsMavenRepositoryLibraryDescriptor myDesc;
    @NotNull
    private final Set<ArtifactKind> myKinds;

    LibraryResolveJob(@NotNull JpsMavenRepositoryLibraryDescriptor desc,
                      @NotNull Set<ArtifactKind> kinds,
                      @NotNull Collection<RemoteRepositoryDescription> repositories) {
      super(repositories);
      myDesc = desc;
      myKinds = kinds;
    }

    @Override
    protected boolean canStart() {
      return super.canStart() && myDesc.getMavenId() != null;
    }

    @Override
    protected String getProgressText() {
      return "Loading " + RepositoryLibraryDescription.findDescription(myDesc).getDisplayName();
    }

    @Override
    protected Collection<Artifact> getDefaultResult() {
      return Collections.emptyList();
    }

    @Override
    protected Collection<Artifact> perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception {
      final String version = myDesc.getVersion();
      try {
        return manager.resolveDependencyAsArtifact(myDesc.getGroupId(), myDesc.getArtifactId(), version, myKinds,
                                                   myDesc.isIncludeTransitiveDependencies(), myDesc.getExcludedDependencies());
      }
      catch (TransferCancelledException e) {
        throw new ProcessCanceledException(e);
      }
      catch (RepositoryOfflineException e) {
        throw e;
      }
      catch (Exception e) {
        final String resolvedVersion = resolveVersion(manager, version);
        if (Comparing.equal(version, resolvedVersion)) { // no changes
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
    }

    @Nullable
    private String resolveVersion(final ArtifactRepositoryManager manager, final String version) throws Exception {
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
    @NotNull
    private final RepositoryLibraryDescription myDescription;

    VersionResolveJob(@NotNull RepositoryLibraryDescription repositoryLibraryDescription, @NotNull List<RemoteRepositoryDescription> repositories) {
      super(repositories);

      myDescription = repositoryLibraryDescription;
    }

    @Override
    protected String getProgressText() {
      return "Loading " + myDescription.getDisplayName() + " versions";
    }

    @Override
    protected Collection<String> perform(ProgressIndicator progress, @NotNull ArtifactRepositoryManager manager) throws Exception {
      return lookupVersionsImpl(myDescription.getGroupId(), myDescription.getArtifactId(), manager);
    }

    @Override
    protected Collection<String> getDefaultResult() {
      return Collections.emptyList();
    }
  }
}