/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.jarRepository;

import com.intellij.CommonBundle;
import com.intellij.jarRepository.services.MavenRepositoryServicesManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.ui.DialogWrapper;
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
import gnu.trove.THashMap;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JarRepositoryManager {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.utils.library.RepositoryAttachHandler");

  private static final String MAVEN_REPOSITORY_MACRO = "$MAVEN_REPOSITORY$";
  private static final String DEFAULT_REPOSITORY_PATH = ".m2/repository";
  private static final AtomicInteger ourTasksInProgress = new AtomicInteger();

  private static class JobExecutor {
    static final ExecutorService INSTANCE = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("RemoteLibraryDownloader");
  }

  public static boolean hasRunningTasks() {
    return ourTasksInProgress.get() > 0;   // todo: count tasks on per-project basis?
  }

  @Nullable
  public static NewLibraryConfiguration chooseLibraryAndDownload(final @NotNull Project project, final @Nullable String initialFilter, JComponent parentComponent) {
    RepositoryAttachDialog dialog = new RepositoryAttachDialog(project, initialFilter);
    dialog.setTitle("Download Library From Maven Repository");
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return null;
    }

    final String coord = dialog.getCoordinateText();
    final boolean attachSources = dialog.getAttachSources();
    final boolean attachJavaDoc = dialog.getAttachJavaDoc();
    final String copyTo = dialog.getDirectoryPath();

    final NewLibraryConfiguration config = resolveAndDownload(
      project, coord, attachSources, attachJavaDoc, copyTo, RemoteRepositoriesConfiguration.getInstance(project).getRepositories()
    );
    if (config == null) {
      Messages.showErrorDialog(parentComponent, "No files were downloaded for " + coord, CommonBundle.getErrorTitle());
    }
    return config;
  }

  @Nullable
  public static NewLibraryConfiguration resolveAndDownload(@NotNull Project project,
                                                            String coord,
                                                            boolean attachSources,
                                                            boolean attachJavaDoc,
                                                            String copyTo,
                                                            Collection<RemoteRepositoryDescription> repositories) {
    RepositoryLibraryProperties props = new RepositoryLibraryProperties(coord);
    final Collection<OrderRoot> roots = loadDependenciesModal(
      project, props, attachSources, attachJavaDoc, copyTo, repositories
    );

    if (roots != null && !roots.isEmpty()) {
      notifyArtifactsDownloaded(project, roots);
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
    return null;
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
        ourLocalRepositoryPath = repoPath;
        return repoPath;
      }
    }
    final String userHome = System.getProperty("user.home", null);
    repoPath = userHome != null ? new File(userHome, DEFAULT_REPOSITORY_PATH) : new File(DEFAULT_REPOSITORY_PATH);
    ourLocalRepositoryPath = repoPath;
    return repoPath;
  }

  public static Collection<OrderRoot> loadDependenciesModal(@NotNull Project project,
                                                            @NotNull RepositoryLibraryProperties libraryProps,
                                                            boolean loadSources,
                                                            boolean loadJavadoc,
                                                            @Nullable String copyTo,
                                                            @Nullable Collection<RemoteRepositoryDescription> repositories) {
    return loadDependenciesImpl(project, libraryProps, loadSources, loadJavadoc, copyTo, repositories, true);
  }

  
  /**
   * Warning! Suitable to be used from non-AWT thread only. When called from UI thread, may lead to a deadlock
   * Use loadDependenciesModal() or loadDependenciesAsync() instead
   */
  @Deprecated
  public static Collection<OrderRoot> loadDependencies(@NotNull Project project,
                                                       @NotNull RepositoryLibraryProperties libraryProps,
                                                       boolean loadSources,
                                                       boolean loadJavadoc,
                                                       @Nullable String copyTo,
                                                       @Nullable Collection<RemoteRepositoryDescription> repositories) {
    return loadDependenciesImpl(project, libraryProps, loadSources, loadJavadoc, copyTo, repositories, false);
  }

  private static Collection<OrderRoot> loadDependenciesImpl(@NotNull Project project,
                                                            @NotNull RepositoryLibraryProperties libraryProps,
                                                            boolean loadSources,
                                                            boolean loadJavadoc,
                                                            @Nullable String copyTo,
                                                            @Nullable Collection<RemoteRepositoryDescription> repositories, boolean modal) {
    final JpsMavenRepositoryLibraryDescriptor libDescriptor = new JpsMavenRepositoryLibraryDescriptor(libraryProps.getGroupId(), libraryProps.getArtifactId(), libraryProps.getVersion());
    if (libDescriptor.getMavenId() != null) {
      if (repositories == null || repositories.isEmpty()) {
        repositories = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
      }
      if (!repositories.isEmpty()) {
        final EnumSet<ArtifactKind> kinds = EnumSet.of(ArtifactKind.ARTIFACT);
        if (loadSources) {
          kinds.add(ArtifactKind.SOURCES);
        }
        if (loadJavadoc) {
          kinds.add(ArtifactKind.JAVADOC);
        }
        try {
          if (modal) {
            return submitModalJob(
              project, "Resolving Maven dependencies...", newOrderRootResolveJob(libDescriptor, kinds, repositories, copyTo)
            );
          }
          return submitBackgroundJob(
            project, "Resolving Maven dependencies...", newOrderRootResolveJob(libDescriptor, kinds, repositories, copyTo)
          ).get();
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.info(e);
        }
      }
    }
    return Collections.emptyList();
  }

  public static void loadDependenciesAsync(@NotNull Project project,
                                           RepositoryLibraryProperties libraryProps,
                                           boolean loadSources,
                                           boolean loadJavadoc,
                                           @Nullable List<RemoteRepositoryDescription> repos,
                                           @Nullable String copyTo,
                                           Consumer<Collection<OrderRoot>> resultProcessor) {
    final EnumSet<ArtifactKind> kinds = EnumSet.of(ArtifactKind.ARTIFACT);
    if (loadSources) {
      kinds.add(ArtifactKind.SOURCES);
    }
    if (loadJavadoc) {
      kinds.add(ArtifactKind.JAVADOC);
    }
    loadDependenciesAsync(
      project,
      new JpsMavenRepositoryLibraryDescriptor(libraryProps.getGroupId(), libraryProps.getArtifactId(), libraryProps.getVersion()),
      kinds, repos, copyTo, resultProcessor
    );
  }
  
  public static void loadDependenciesAsync(@NotNull Project project, JpsMavenRepositoryLibraryDescriptor desc, final Set<ArtifactKind> artifactKinds, @Nullable List<RemoteRepositoryDescription> repos, @Nullable String copyTo, Consumer<Collection<OrderRoot>> resultProcessor) {
    if (repos == null || repos.isEmpty()) {
      repos = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    }
    submitBackgroundJob(
      project, "Resolving Maven dependencies...", newOrderRootResolveJob(desc, artifactKinds, repos, copyTo).andThen(
        roots -> {
          resultProcessor.accept(roots);
          return roots;
        })
    );
  }

  public static void getAvailableVersionsAsync(Project project,  RepositoryLibraryDescription libraryDescription, Consumer<Collection<String>> resultProcessor) {
    final List<RemoteRepositoryDescription> repos = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    submitBackgroundJob(project, "Looking up available versions for " + libraryDescription.getDisplayName(), new VersionResolveJob(libraryDescription, repos)
      .andThen(
        versions -> {
          resultProcessor.accept(versions);
          return versions;
        }
      ));
  }

  @NotNull
  public static Future<Collection<String>> getAvailableVersions(Project project, RepositoryLibraryDescription libraryDescription) {
    final List<RemoteRepositoryDescription> repos = RemoteRepositoriesConfiguration.getInstance(project).getRepositories();
    return submitBackgroundJob(project, "Looking up available versions for " + libraryDescription.getDisplayName(), new VersionResolveJob(libraryDescription, repos));
  }

  private static void notifyArtifactsDownloaded(Project project, Collection<OrderRoot> roots) {
    final StringBuilder sb = new StringBuilder();
    final String title = "The following files were downloaded:";
    sb.append("<ol>");
    for (OrderRoot root : roots) {
      sb.append("<li>");
      sb.append(root.getFile().getName());
      sb.append("</li>");
    }
    sb.append("</ol>");
    Notifications.Bus.notify(new Notification("Repository", title, sb.toString(), NotificationType.INFORMATION), project);
  }

  public static void searchArtifacts(final Project project, String coord, final Consumer<Collection<Pair<RepositoryArtifactDescription, RemoteRepositoryDescription>>> resultProcessor) {
    if (coord == null || coord.length() == 0) {
      return;
    }
    final RepositoryArtifactDescription template;
    if (coord.indexOf(':') == -1 && Character.isUpperCase(coord.charAt(0))) {
      template = new RepositoryArtifactDescription(null, null, null, "jar", null, coord, null);
    }
    else {
      template = new RepositoryArtifactDescription(new RepositoryLibraryProperties(coord), "jar", null);
    }
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

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

  public static void searchRepositories(final Project project, final Collection<String> serviceUrls, final Processor<Collection<RemoteRepositoryDescription>> resultProcessor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Maven", false) {

      public void run(@NotNull ProgressIndicator indicator) {
        final Ref<List<RemoteRepositoryDescription>> result = Ref.create(Collections.<RemoteRepositoryDescription>emptyList());
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
  private static <T> T submitModalJob(@Nullable final Project project, final String title, final Function<ProgressIndicator, T> job){
    final Ref<T> result = Ref.create(null);
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

  private static <T> Future<T> submitBackgroundJob(@Nullable final Project project, final String title, final Function<ProgressIndicator, T> job){
    final ModalityState startModality = ModalityState.defaultModalityState();
    return JobExecutor.INSTANCE.submit(() -> {
      try {
        ourTasksInProgress.incrementAndGet();
        final ProgressIndicator indicator = new EmptyProgressIndicator(startModality);
        return ProgressManager.getInstance().runProcess(() -> job.apply(indicator), indicator);
      }
      catch (ProcessCanceledException ignored){
      }
      catch (Throwable e) {
        LOG.info(e);
      }
      finally {
        ourTasksInProgress.decrementAndGet();
      }
      return null;
    });
  }

  private static Collection<String> lookupVersionsImpl(final String groupId, final String artifactId, ArtifactRepositoryManager manager) throws Exception {
    try {
      final List<Version> result = manager.getAvailableVersions(groupId, artifactId, "[0,)", ArtifactKind.ARTIFACT);
      return result.stream().sorted(Comparator.reverseOrder()).map(Version::toString).collect(
        Collectors.toCollection(() -> new ArrayList<>(result.size()))
      );
    }
    catch (TransferCancelledException e) {
      throw new ProcessCanceledException(e);
    }
  }

  private static abstract class AetherJob<T> implements Function<ProgressIndicator, T> {
    @NotNull
    private final Collection<RemoteRepositoryDescription> myRepositories;

    public AetherJob(@NotNull Collection<RemoteRepositoryDescription> repositories) {
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
    protected abstract T perform(ProgressIndicator progress, ArtifactRepositoryManager manager) throws Exception;
    protected abstract T getDefaultResult();
  }

  private static Function<ProgressIndicator, Collection<OrderRoot>> newOrderRootResolveJob(@NotNull JpsMavenRepositoryLibraryDescriptor desc, @NotNull Set<ArtifactKind> kinds, @NotNull Collection<RemoteRepositoryDescription> repositories, @Nullable String copyTo) {
    return new LibraryResolveJob(desc, kinds, repositories).andThen(
      resolved -> resolved.isEmpty() ? Collections.emptyList() : new WriteAction<Collection<OrderRoot>>() {
        @Override
        protected void run(@NotNull Result<Collection<OrderRoot>> result) throws Throwable {
          result.setResult(createRoots(resolved, copyTo));
        }
      }.execute().getResultObject());
  }

  private static Collection<OrderRoot> createRoots(@NotNull Collection<Artifact> artifacts, @Nullable String copyTo) {
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
          OrderRootType rootType;
          if (ArtifactKind.JAVADOC.getClassifier().equals(each.getClassifier())) {
            rootType = JavadocOrderRootType.getInstance();
          }
          else if (ArtifactKind.SOURCES.getClassifier().equals(each.getClassifier())) {
            rootType = OrderRootType.SOURCES;
          }
          else {
            rootType = OrderRootType.CLASSES;
          }
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

    public LibraryResolveJob(@NotNull JpsMavenRepositoryLibraryDescriptor desc, @NotNull Set<ArtifactKind> kinds, @NotNull Collection<RemoteRepositoryDescription> repositories) {
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
    protected Collection<Artifact> perform(ProgressIndicator progress, ArtifactRepositoryManager manager) throws Exception {
      final String version = myDesc.getVersion();
      try {
        return manager.resolveDependencyAsArtifact(myDesc.getGroupId(), myDesc.getArtifactId(), version, myKinds);
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
          return manager.resolveDependencyAsArtifact(myDesc.getGroupId(), myDesc.getArtifactId(), resolvedVersion, myKinds);
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
    private final RepositoryLibraryDescription myDesc;

    public VersionResolveJob(@NotNull RepositoryLibraryDescription repositoryLibraryDescription, @NotNull List<RemoteRepositoryDescription> repositories) {
      super(repositories);
      myDesc = repositoryLibraryDescription;
    }

    @Override
    protected String getProgressText() {
      return "Loading " + myDesc.getDisplayName() + " versions";
    }

    @Override
    protected Collection<String> perform(ProgressIndicator progress, ArtifactRepositoryManager manager) throws Exception {
      return lookupVersionsImpl(myDesc.getGroupId(), myDesc.getArtifactId(), manager);
    }

    @Override
    protected Collection<String> getDefaultResult() {
      return Collections.emptyList();
    }

  }
}
