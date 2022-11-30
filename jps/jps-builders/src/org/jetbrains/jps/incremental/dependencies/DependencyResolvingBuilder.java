// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.SmartHashSet;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.*;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.jetbrains.jps.util.JpsChecksumUtil.getSha256Checksum;

/**
 * Downloads missing Maven repository libraries on which a module depends. IDE should download them automatically when the project is opened,
 * so this builder does nothing in normal cases. However it's needed when the build process is started in standalone mode (not from IDE) or
 * if build is triggered before IDE downloads all required dependencies.
 */
public final class DependencyResolvingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(DependencyResolvingBuilder.class);
  private static final String MAVEN_REPOSITORY_PATH_VAR = "MAVEN_REPOSITORY";
  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";

  private static final Key<ArtifactRepositoryManager> MANAGER_KEY = GlobalContextKey.create("_artifact_repository_manager_");
  public static final Key<Map<String, ArtifactRepositoryManager>> NAMED_MANAGERS_KEY = Key.create("_named_artifact_repository_manager_");

  private static final Key<Exception> RESOLVE_ERROR_KEY = Key.create("_artifact_repository_resolve_error_");
  public static final String RESOLUTION_PARALLELISM_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.parallelism";
  public static final String RESOLUTION_RETRY_ENABLED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.enabled";
  public static final String RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.max.attempts";
  public static final String RESOLUTION_RETRY_DELAY_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.delay.ms";
  public static final String RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.backoff.limit.ms";
  public static final String RESOLUTION_VERIFICATION_ENABLED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.verification.enabled";
  public static final String RESOLUTION_VERIFICATION_SHA256SUM_REQUIRED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.verification.sha256sum.required";
  public static final String RESOLUTION_USE_REPO_ID_FROM_LIB_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.use.repo.id.from.library";
  public static final String RESOLUTION_REPO_ID_FROM_LIB_REQUIRED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.repo.id.from.library.required";

  public DependencyResolvingBuilder() {
    super(BuilderCategory.INITIAL);
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return emptyList();
  }

  @Override
  public @NotNull String getPresentableName() {
    return getBuilderName();
  }

  @Override
  public void buildStarted(CompileContext context) {
    ResourceGuard.init(context);
  }

  @Override
  public void chunkBuildStarted(CompileContext context, ModuleChunk chunk) {
    try {
      resolveMissingDependencies(context, chunk.getModules(), BuildTargetChunk.forModulesChunk(chunk));
    }
    catch (Exception e) {
      context.putUserData(RESOLVE_ERROR_KEY, e);
    }
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {

    final Exception error = context.getUserData(RESOLVE_ERROR_KEY);
    if (error != null) {
      return reportError(context, chunk.getPresentableShortName(), error);
    }

    return ExitCode.OK;
  }

  static @NotNull ExitCode reportError(CompileContext context, String placePresentableName, Exception error) {
    @Nls StringBuilder builder = new StringBuilder().append(JpsBuildBundle.message("build.message.error.resolving.dependencies.for",
                                                                                   placePresentableName));
    Throwable th = error;
    final Set<Throwable> processed = new HashSet<>();
    final Set<String> detailsMessage = new HashSet<>();
    while (th != null && processed.add(th)) {
      String details = th.getMessage();
      if (th instanceof UnknownHostException) {
        details = JpsBuildBundle.message("build.message.unknown.host.0", details); // hack for UnknownHostException
      }
      if (details != null && detailsMessage.add(details)) {
        builder.append(":\n").append(details);
      }
      th = th.getCause();
    }
    final String msg = builder.toString();
    LOG.info(msg, error);
    context.processMessage(new CompilerMessage(getBuilderName(), BuildMessage.Kind.ERROR, msg));
    return ExitCode.ABORT;
  }

  @SuppressWarnings("RedundantThrows")
  static void resolveMissingDependencies(CompileContext context, Collection<? extends JpsModule> modules,
                                         BuildTargetChunk currentTargets) throws Exception {
    Collection<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> libs = getRepositoryLibraries(modules);
    if (!libs.isEmpty()) {
      final ArtifactRepositoryManager repoManager = getRepositoryManager(context);
      final boolean enableVerification = SystemProperties.getBooleanProperty(RESOLUTION_VERIFICATION_ENABLED_PROPERTY, false);
      final boolean sha256sumRequired = SystemProperties.getBooleanProperty(RESOLUTION_VERIFICATION_SHA256SUM_REQUIRED_PROPERTY, false);
      final boolean useRepoIdFromLibraryDescriptor = SystemProperties.getBooleanProperty(RESOLUTION_USE_REPO_ID_FROM_LIB_PROPERTY, false);
      final boolean repoIdInLibraryDescriptorRequired = SystemProperties.getBooleanProperty(RESOLUTION_REPO_ID_FROM_LIB_REQUIRED_PROPERTY, false);
      resolveMissingDependencies(libs, lib -> {
        try {
          resolveMissingDependency(context, currentTargets, lib, repoManager, enableVerification, sha256sumRequired,
                                   useRepoIdFromLibraryDescriptor, repoIdInLibraryDescriptorRequired);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  private static void resolveMissingDependencies(
    Collection<? extends JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> libs,
    Consumer<? super JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> resolveAction
  ) throws Exception {
    int parallelism = SystemProperties.getIntProperty(RESOLUTION_PARALLELISM_PROPERTY, 1);
    if (parallelism < 2 || libs.size() < 2) {
      libs.forEach(resolveAction);
    }
    else {
      ExecutorService executorService = Executors.newFixedThreadPool(parallelism);
      try {
        List<Future<?>> futures = ContainerUtil.map(libs, lib -> executorService.submit(() -> resolveAction.accept(lib)));
        for (Future<?> future : futures) future.get();
      }
      finally {
        executorService.shutdown();
      }
    }
  }

  private static void resolveMissingDependency(CompileContext context, BuildTargetChunk currentTargets,
                                               JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> lib,
                                               ArtifactRepositoryManager globalRepoManager, boolean enableVerification,
                                               boolean verificationSha256SumRequired,
                                               boolean useRepoIdFromLibraryDescriptor,
                                               boolean repoIdInLibraryDescriptorRequired) throws Exception {
    final JpsMavenRepositoryLibraryDescriptor descriptor = lib.getProperties().getData();
    final ResourceGuard guard = ResourceGuard.get(context, descriptor);
    if (guard.requestProcessing(context.getCancelStatus())) {
      try {
        final Collection<File> required = lib.getFiles(JpsOrderRootType.COMPILED);
        int compiledRootsNumber = required.size();
        for (Iterator<File> it = required.iterator(); it.hasNext(); ) {
          if (globalRepoManager.isValidArchive(it.next())) {
            it.remove(); // leaving only non-existing stuff requiring synchronization
          }
        }
        if (!required.isEmpty()) {
          context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.resolving.0.library", lib.getName()), currentTargets));
          ArtifactRepositoryManager effectiveRepoManager;
          if (useRepoIdFromLibraryDescriptor) {
            String repositoryId = descriptor.getJarRepositoryId();
            if (repoIdInLibraryDescriptorRequired && repositoryId == null) {
              throw new RuntimeException("Repository ID is not set for library: " + lib.getName());
            }
            effectiveRepoManager = getRepositoryManager(context, descriptor.getJarRepositoryId());
          } else {
            effectiveRepoManager = globalRepoManager;
          }

          LOG.debug("Downloading missing files for " + lib.getName() + " library: " + required);
          final Collection<File> resolved = effectiveRepoManager.resolveDependency(descriptor.getGroupId(), descriptor.getArtifactId(),
                                                                                   descriptor.getVersion(),
                                                                                   descriptor.isIncludeTransitiveDependencies(), descriptor.getExcludedDependencies());
          if (!resolved.isEmpty()) {
            syncPaths(required, resolved);
          }
          else {
            LOG.info("No artifacts were resolved for repository dependency " + descriptor.getMavenId());
          }
        }

        if (enableVerification) {
          List<String> problems = verifyResolvedArtifacts(descriptor, compiledRootsNumber, verificationSha256SumRequired);
          if (!problems.isEmpty()) {
            throw new RuntimeException("Verification failed for '" + lib.getName()+ "': " + String.join(", ", problems));
          }
        }
      }
      catch (TransferCancelledException e) {
        context.checkCanceled();
      }
      finally {
        guard.finish();
      }
    }
  }

  private static void syncPaths(final Collection<? extends File> required, @NotNull Collection<? extends File> resolved) throws Exception {
    Set<File> libFiles = FileCollectionFactory.createCanonicalFileSet();
    libFiles.addAll(required);
    libFiles.removeAll(resolved);

    if (!libFiles.isEmpty()) {
      final Map<String, File> nameToArtifactMap = CollectionFactory.createFilePathMap();
      for (File f : resolved) {
        final File prev = nameToArtifactMap.put(f.getName(), f);
        if (prev != null) {
          throw new Exception("Ambiguous artifacts with the same name: " + prev.getPath() + " and " + f.getPath());
        }
      }
      for (File file : libFiles) {
        final File resolvedArtifact = nameToArtifactMap.get(file.getName());
        if (resolvedArtifact != null) {
          FileUtil.copy(resolvedArtifact, file);
        }
      }
    }
  }

  /**
   * Verify resolved artifacts
   * @param descriptor Library descriptor with verification settings
   * @param verificationSha256SumRequired Interpret disabled SHA256 checksum as problem
   * @return List of found problems (wrong or missing checksums)
   */
  private static List<String> verifyResolvedArtifacts(@NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                      int compiledRootNumber,
                                                      boolean verificationSha256SumRequired) {
    boolean verifySha256Checksum = descriptor.isVerifySha256Checksum();
    if (!verifySha256Checksum) {
      return verificationSha256SumRequired ? singletonList("SHA256 checksum is required, but not enabled") : emptyList();
    }

    List<String> problems = new ArrayList<>();
    List<ArtifactVerification> artifactsVerification = descriptor.getArtifactsVerification();
    if (artifactsVerification.size() != compiledRootNumber &&
        !"LATEST".equals(descriptor.getVersion()) &&
        !"RELEASE".equals(descriptor.getVersion()) &&
        !descriptor.getVersion().endsWith("-SNAPSHOT")) {
      problems.add("artifacts verification entries number '" + artifactsVerification.size() +
                   "' not equal to compiled roots number '" + compiledRootNumber + "' for " + descriptor.getMavenId());
    }

    for (var verification : descriptor.getArtifactsVerification()) {
      Path artifact = JpsPathUtil.urlToFile(verification.getUrl()).toPath();
      try {
        String expectedSha256Sum = verification.getSha256sum();
        String actualSha256Sum = getSha256Checksum(artifact);
        if (!Objects.equals(expectedSha256Sum, actualSha256Sum)) {
          problems.add("bad checksum for " + artifact.getFileName() + ": expected " + expectedSha256Sum + ", actual " + actualSha256Sum);
        }
      }
      catch (IOException e) {
        problems.add("Unable to build checksum for " + artifact.getFileName() + ", cause: " + e.getMessage());
      }
    }
    return problems;
  }

  private static final class ResourceGuard {
    private static final Key<ConcurrentMap<JpsMavenRepositoryLibraryDescriptor, ResourceGuard>> CONTEXT_KEY = GlobalContextKey.create("_artifact_repository_resolved_libraries_");
    private static final byte INITIAL = 0;
    private static final byte PROGRESS = 1;
    private static final byte FINISHED = 2;
    private byte myState = INITIAL;
    private CanceledStatus myStatus;

    private synchronized boolean requestProcessing(final CanceledStatus cancelStatus) {
      myStatus = cancelStatus;
      if (myState == INITIAL) {
        myState = PROGRESS;
        return true;
      }
      while (myState == PROGRESS) {
        if (cancelStatus.isCanceled()) {
          break;
        }
        try {
          this.wait(1000L);
        }
        catch (InterruptedException ignored) {
        }
      }
      return false;
    }

    private synchronized void finish() {
      if (myState != FINISHED) {
        myState = FINISHED;
        this.notifyAll();
      }
    }

    private static void init(CompileContext context) {
      context.putUserData(CONTEXT_KEY, new ConcurrentHashMap<>());
    }

    private static @NotNull ResourceGuard get(CompileContext context, JpsMavenRepositoryLibraryDescriptor descriptor) {
      final ConcurrentMap<JpsMavenRepositoryLibraryDescriptor, ResourceGuard> map = context.getUserData(CONTEXT_KEY);
      assert map != null;
      final ResourceGuard g = new ResourceGuard();
      final ResourceGuard existing = map.putIfAbsent(descriptor, g);
      return existing != null? existing : g;
    }
  }

  private static @NotNull Collection<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> getRepositoryLibraries(Collection<? extends JpsModule> modules) {
    final Collection<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> result = new SmartHashSet<>();
    for (JpsModule module : modules) {
      for (JpsDependencyElement dep : module.getDependenciesList().getDependencies()) {
        if (dep instanceof JpsLibraryDependency) {
          final JpsLibrary _lib = ((JpsLibraryDependency)dep).getLibrary();
          final JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> lib = _lib != null ? _lib.asTyped(JpsRepositoryLibraryType.INSTANCE) : null;
          if (lib != null) {
            result.add(lib);
          }
        }
      }
    }
    return result;
  }

  public static synchronized ArtifactRepositoryManager getRepositoryManager(final CompileContext context) {
    try {
      return getRepositoryManager(context, null);
    }
    catch (RemoteRepositoryNotFoundException e) {
      LOG.error("RemoteRepositoryNotFoundException should not be thrown here", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Get remote repository manager. Creates corresponding repository manager if it hasn't been done before.
   * @param context Compile context to store manager with {@link GlobalContextKey}
   * @param remoteRepositoryId ID of remote repository to use within managers. If null, manager with all available repositories is returned.
   * @return An instance of ArtifactRepositoryManager with requested remote repositories.
   * @throws RemoteRepositoryNotFoundException If {@code remoteRepositoryId} is not null and remote repository with {@code id == remoteRepositoryId} is not found.
   */
  private static synchronized ArtifactRepositoryManager getRepositoryManager(final CompileContext context, @Nullable String remoteRepositoryId)
    throws RemoteRepositoryNotFoundException {
    ArtifactRepositoryManager manager = MANAGER_KEY.get(context);
    Map<String, ArtifactRepositoryManager> namedManagers = NAMED_MANAGERS_KEY.get(context);

    if (manager == null || namedManagers == null) {
      final List<RemoteRepository> repositories = new SmartList<>();
      for (JpsRemoteRepositoryDescription repo : JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(context.getProjectDescriptor().getProject())
          .getRepositories()) {
        repositories.add(
          ArtifactRepositoryManager.createRemoteRepository(repo.getId(), repo.getUrl(), obtainAuthenticationData(repo.getUrl()))
        );
      }
      Retry retry = RetryProvider.disabled();
      if (SystemProperties.getBooleanProperty(RESOLUTION_RETRY_ENABLED_PROPERTY, false)) {
        long retryInitialDelay = SystemProperties.getLongProperty(RESOLUTION_RETRY_DELAY_MS_PROPERTY, 1000);
        long retryBackoffLimit = SystemProperties.getLongProperty(RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY, TimeUnit.MINUTES.toMillis(15));
        int retryMaxAttempts = SystemProperties.getIntProperty(RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, 10);
        retry = RetryProvider.withExponentialBackOff(retryInitialDelay, retryBackoffLimit, retryMaxAttempts);
      }

      File localRepositoryRoot = getLocalArtifactRepositoryRoot(context.getProjectDescriptor().getModel().getGlobal());

      ProgressConsumer progressConsumer = new ProgressConsumer() {
        @Override
        public void consume(@NlsSafe String message) {
          context.processMessage(new ProgressMessage(message));
        }

        @Override
        public boolean isCanceled() {
          return context.getCancelStatus().isCanceled();
        }
      };

      if (manager == null) {
        manager = new ArtifactRepositoryManager(localRepositoryRoot, repositories, progressConsumer, retry);
        // further init manager here
        MANAGER_KEY.set(context, manager);
      }

      if (namedManagers == null) {
        Map<String, ArtifactRepositoryManager> managers = new ConcurrentHashMap<>();
        for (var repository : repositories) {
          managers.put(repository.getId(),
                       new ArtifactRepositoryManager(localRepositoryRoot, singletonList(repository), progressConsumer, retry));
        }
        namedManagers = Collections.unmodifiableMap(managers);
      }
    }

    if (remoteRepositoryId == null) {
      return manager;
    }

    ArtifactRepositoryManager found = namedManagers.getOrDefault(remoteRepositoryId, null);
    if (found == null) {
      throw new RemoteRepositoryNotFoundException(remoteRepositoryId);
    }
    return found;
  }

  private static ArtifactRepositoryManager.ArtifactAuthenticationData obtainAuthenticationData(String url) {
    for (DependencyAuthenticationDataProvider provider : JpsServiceManager.getInstance()
      .getExtensions(DependencyAuthenticationDataProvider.class)) {
      DependencyAuthenticationDataProvider.AuthenticationData authData = provider.provideAuthenticationData(url);
      if (authData != null) {
        return new ArtifactRepositoryManager.ArtifactAuthenticationData(authData.getUserName(), authData.getPassword());
      }
    }
    return null;
  }

  private static @NotNull File getLocalArtifactRepositoryRoot(@NotNull JpsGlobal global) {
    final JpsPathVariablesConfiguration pvConfig = JpsModelSerializationDataService.getPathVariablesConfiguration(global);
    final String localRepoPath = pvConfig != null ? pvConfig.getUserVariableValue(MAVEN_REPOSITORY_PATH_VAR) : null;
    if (localRepoPath != null) {
      return new File(localRepoPath);
    }
    final String root = System.getProperty("user.home", null);
    return root != null ? new File(root, DEFAULT_MAVEN_REPOSITORY_PATH) : new File(DEFAULT_MAVEN_REPOSITORY_PATH);
  }

  @NotNull
  private static @Nls String getBuilderName() {
    return JpsBuildBundle.message("builder.name.maven.dependency.resolver");
  }

  private static class RemoteRepositoryNotFoundException extends Exception {
    private RemoteRepositoryNotFoundException(String remoteRepositoryId) {
      super("Unable to find remote repository with id=" + remoteRepositoryId);
    }
  }
}
