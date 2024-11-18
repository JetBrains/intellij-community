// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfiguration;
import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfigurationService;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.SmartHashSet;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager.ArtifactAuthenticationData;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.idea.maven.aether.Retry;
import org.jetbrains.idea.maven.aether.RetryProvider;
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
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsMavenSettings;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.JpsChecksumUtil;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Downloads missing Maven repository libraries on which a module depends. IDE should download them automatically when the project is opened,
 * so this builder does nothing in normal cases. However, it's needed when the build process is started in standalone mode (not from IDE) or
 * if build is triggered before IDE downloads all required dependencies.
 */
@ApiStatus.Internal
public final class DependencyResolvingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(DependencyResolvingBuilder.class);
  private static final String MAVEN_REPOSITORY_PATH_VAR = "MAVEN_REPOSITORY";
  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";

  private static final Key<Pair<ArtifactRepositoryManager, Map<String, ArtifactRepositoryManager>>> MANAGERS_KEY = GlobalContextKey.create("_artifact_repository_manager_"); // pair[unnamedManager: {namedManagers}]

  private static final Key<Exception> RESOLVE_ERROR_KEY = Key.create("_artifact_repository_resolve_error_");
  public static final String REMOTE_REPOSITORY_AUTH_PROPERTY_PREFIX = "org.jetbrains.jps.incremental.dependencies.resolution.remote.repository.auth.";
  public static final String RESOLUTION_PARALLELISM_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.parallelism";
  public static final String RESOLUTION_RETRY_ENABLED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.enabled";
  public static final String RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.max.attempts";
  public static final String RESOLUTION_RETRY_DELAY_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.delay.ms";
  public static final String RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.backoff.limit.ms";
  public static final String RESOLUTION_SHA256_CHECKSUM_IGNORE_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.sha256.checksum.ignored";
  public static final String RESOLUTION_BIND_REPOSITORY_IGNORE_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.bind.repository.ignored";
  public static final String RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.download.corrupted.zip";

  /**
   * @deprecated Replace with {@link DependencyResolvingBuilder#RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_PROPERTY}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public static final String RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_LEGACY_PROPERTY = "org.jetbrains.idea.maven.aether.strictValidation";
  public static final String RESOLUTION_REPORT_CORRUPTED_ZIP_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.report.corrupted.zip";
  public static final String RESOLUTION_REPORT_INVALID_SHA256_CHECKSUM_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.report.invalid.sha256.checksum";
  public static final String RESOLUTION_CORRUPTED_ARTIFACTS_REPORTS_DIRECTORY_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.corrupted.artifacts.directory";

  public DependencyResolvingBuilder() {
    super(BuilderCategory.INITIAL);
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull String getPresentableName() {
    return getBuilderName();
  }

  @Override
  public void buildStarted(CompileContext context) {
    JpsLibraryResolveGuard.init(context);
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
                        OutputConsumer outputConsumer) {

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
      JpsDependencyResolverConfiguration resolverConfiguration = JpsDependencyResolverConfigurationService
        .getInstance()
        .getOrCreateDependencyResolverConfiguration(context.getProjectDescriptor().getProject());

      boolean ignoreChecksums = SystemProperties.getBooleanProperty(RESOLUTION_SHA256_CHECKSUM_IGNORE_PROPERTY, false);
      boolean ignoreBindRepository = SystemProperties.getBooleanProperty(RESOLUTION_BIND_REPOSITORY_IGNORE_PROPERTY, false);

      boolean verifySha256Checksums = !ignoreChecksums && resolverConfiguration.isSha256ChecksumVerificationEnabled();
      boolean useBindRepositories = !ignoreBindRepository && resolverConfiguration.isBindRepositoryEnabled();

      resolveMissingDependencies(libs, lib -> {
        try {
          resolveMissingDependency(context, currentTargets, lib, verifySha256Checksums, useBindRepositories);
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
                                               boolean verifySha256Checksums,
                                               boolean useBindRepositories) throws Exception {
    final JpsMavenRepositoryLibraryDescriptor descriptor = lib.getProperties().getData();
    List<File> compiledRoots = lib.getFiles(JpsOrderRootType.COMPILED);
    JpsLibraryResolveGuard.performUnderGuard(context, descriptor, compiledRoots, () -> {
      try {
        // list of missing roots needed to be resolved
        List<? extends File>
          required = ContainerUtil.filter(compiledRoots, root -> !verifyLibraryArtifact(context, lib.getName(), descriptor, root));

        // be strict and verify effective repo manager exists (will throw an exception if bind repository is required, but missing)
        ArtifactRepositoryManager effectiveRepoManager = getRepositoryManager(context, descriptor, lib.getName(), useBindRepositories);

        if (!required.isEmpty()) {
          context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.resolving.0.library", lib.getName()), currentTargets));
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

          if (LOG.isDebugEnabled()) {
            Set<String> missing = required.stream().map(it -> FileUtil.toCanonicalPath(it.getAbsolutePath())).collect(Collectors.toSet());
            resolved.forEach(it -> missing.remove(FileUtil.toCanonicalPath(it.getAbsolutePath())));
            if (!missing.isEmpty()) {
              LOG.debug("Files are not downloaded completely for library '" + lib.getName() + "'," +
                        " descriptor '" + descriptor.getMavenId() + "'." +
                        " Required " + required + " but resolved " + resolved +
                        ". Missing " + missing);
            }
          }
        }
        verifyLibraryRootsChecksums(context, lib.getName(), descriptor, compiledRoots, verifySha256Checksums);
      }
      catch (TransferCancelledException e) {
        context.checkCanceled();
      }
    });
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

  private static ArtifactRepositoryManager getRepositoryManager(@NotNull CompileContext context,
                                                                @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                                @NotNull String libraryName,
                                                                boolean useBindRepositories) throws RemoteRepositoryNotFoundException {
    if (!useBindRepositories) return getRepositoryManager(context);

    String repositoryId = descriptor.getJarRepositoryId();
    if (repositoryId == null) {
      throw new RemoteRepositoryNotFoundException(JpsBuildBundle.message("build.message.error.bind.repository.missing", libraryName));
    }
    return getRepositoryManager(context, repositoryId);
  }

  /**
   * Checks whether artifact is existing and valid zip (if zip/jar). Removes invalid zip/jar if any.
   *
   * @param context Compile context.
   * @param descriptor Library descriptor.
   * @param artifact Artifact file.
   * @return true if artifact exists and not corrupted, otherwise false.
   */
  private static boolean verifyLibraryArtifact(@NotNull CompileContext context,
                                               @NotNull String libraryName,
                                               @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                               @NotNull File artifact) {
    if (!artifact.exists()) return false;

    boolean zipCheckEnabled = SystemProperties.getBooleanProperty(RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_PROPERTY, false) ||
                              SystemProperties.getBooleanProperty(RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_LEGACY_PROPERTY, false);
    if (zipCheckEnabled && (artifact.getName().endsWith(".jar") || artifact.getName().endsWith(".zip"))) {
        long entriesCount = -1;
        try (ZipFile zip = new ZipFile(artifact)) {
          entriesCount = zip.size();
        }
        catch (IOException ignored) {
        }

        if (entriesCount <= 0) {
          try {
            Path compiledRootPath = artifact.toPath();
            reportCorruptedArtifactZip(context, libraryName, descriptor, compiledRootPath);
            context.processMessage(
              new ProgressMessage(JpsBuildBundle.message("progress.message.removing.invalid.artifact", libraryName, artifact))
            );
            Files.deleteIfExists(compiledRootPath);
            return false;
          }
          catch (IOException e) {
            throw new RuntimeException("Failed to delete invalid zip: " + artifact, e);
          }
        }
      }
    return true;
  }

  private static void verifyLibraryRootsChecksums(@NotNull CompileContext context,
                                                  @NotNull String libraryName,
                                                  @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                  @NotNull List<? extends File> compiledRoots,
                                                  boolean verifySha256Checksums) throws ArtifactVerificationException {
    // don't verify checksums if the library doesn't have a fixed version or when verification is disabled
    if (!verifySha256Checksums || !isLibraryVersionFixed(descriptor)) {
      return;
    }

    if (!isAllCompiledRootsVerificationPresent(descriptor, compiledRoots)) {
      throw new ArtifactVerificationException(JpsBuildBundle.message("build.message.error.compile.roots.verification.mismatch",
                                                                     libraryName));
    }

    List<Path> allCompiledRoots = ContainerUtil.map(compiledRoots, File::toPath);
    List<? extends Path> missingCompiledRoots = ContainerUtil.filter(allCompiledRoots, rootFile -> !Files.exists(rootFile));

    if (!missingCompiledRoots.isEmpty()) {
      reportMissingCompiledRootArtifacts(context, libraryName, descriptor, allCompiledRoots, missingCompiledRoots);
      throw new ArtifactVerificationException(
        JpsBuildBundle.message("build.message.error.missing.artifacts", libraryName, missingCompiledRoots.toString())
      );
    }

    Map<String, ArtifactVerification> absolutePathToVerificationMetadata = descriptor.getArtifactsVerification()
      .stream()
      .collect(Collectors.toMap(it -> JpsPathUtil.urlToFile(it.getUrl()).getAbsolutePath(), it -> it));

    for (File compiledRoot : compiledRoots) {
      ArtifactVerification verification = absolutePathToVerificationMetadata.get(compiledRoot.getAbsolutePath());
      checkSha256ChecksumValid(context, descriptor, libraryName, verification);
    }
  }


  private static boolean isLibraryVersionFixed(@NotNull JpsMavenRepositoryLibraryDescriptor descriptor) {
    return !"LATEST".equals(descriptor.getVersion())
           && !"RELEASE".equals(descriptor.getVersion())
           && !descriptor.getVersion().endsWith("-SNAPSHOT");
  }

  private static boolean isAllCompiledRootsVerificationPresent(@NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                               @NotNull List<? extends File> compiledRootsFiles) {
    if (compiledRootsFiles.size() != descriptor.getArtifactsVerification().size()) {
      return false;
    }

    Set<String> compiledRootsPaths = compiledRootsFiles.stream().map(File::getAbsolutePath).collect(Collectors.toSet());
    Set<String> verifiableArtifactsPaths = descriptor.getArtifactsVerification().stream()
      .map(ArtifactVerification::getUrl)
      .map(it -> JpsPathUtil.urlToFile(it).getAbsolutePath())
      .collect(Collectors.toSet());

    return compiledRootsPaths.equals(verifiableArtifactsPaths);
  }


  private static void checkSha256ChecksumValid(@NotNull CompileContext context,
                                               @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                               @NotNull String libraryName,
                                               @NotNull JpsMavenRepositoryLibraryDescriptor.ArtifactVerification verification)
    throws ArtifactVerificationException {
    Path path = JpsPathUtil.urlToFile(verification.getUrl()).toPath();

    String actualSha256Sum;
    try {
      actualSha256Sum = JpsChecksumUtil.getSha256Checksum(path);
    }
    catch (IOException e) {
      context.processMessage(CompilerMessage.createInternalBuilderError(getBuilderName(), e));
      throw new RuntimeException(e);
    }
    String expectedSha256Sum = verification.getSha256sum();

    if (!Objects.equals(expectedSha256Sum, actualSha256Sum)) {
      reportInvalidArtifactChecksum(context, libraryName, descriptor, path, actualSha256Sum);
      throw new ArtifactVerificationException(
        JpsBuildBundle.message("build.message.error.invalid.sha256.checksum", libraryName, path, expectedSha256Sum, actualSha256Sum)
      );
    }
  }

  private static void reportCorruptedArtifactZip(@NotNull CompileContext context,
                                                 @NotNull String libraryName,
                                                 @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                 @NotNull Path artifactFile) {
    if (SystemProperties.getBooleanProperty(RESOLUTION_REPORT_CORRUPTED_ZIP_PROPERTY, false)) {
      String sha256;
      try {
        // compute checksum to ensure the artifact is copied without errors
        sha256 = JpsChecksumUtil.getSha256Checksum(artifactFile);
      }
      catch (IOException e) {
        LOG.error("Failed to compute checksum for corrupted zip: " + artifactFile, e);
        sha256 = "failed_to_compute";
      }
      reportBadArtifact(context, libraryName, descriptor, artifactFile, sha256, "corrupted_zip");
    }
  }

  private static void reportInvalidArtifactChecksum(@NotNull CompileContext context,
                                                    @NotNull String libraryName,
                                                    @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                    @NotNull Path artifactFile,
                                                    @NotNull String sha256sum) {
    if (SystemProperties.getBooleanProperty(RESOLUTION_REPORT_INVALID_SHA256_CHECKSUM_PROPERTY, false)) {
      reportBadArtifact(context, libraryName, descriptor, artifactFile, sha256sum, "invalid_checksum");
    }
  }

  private static void reportBadArtifact(@NotNull CompileContext context,
                                        @NotNull String libraryName,
                                        @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                        @NotNull Path artifactFile,
                                        @NotNull String sha256sum,
                                        @NotNull String problemKind) {
    Path reportsDir = createVerificationProblemReport(context, libraryName, descriptor, problemKind,
                                                      metadata -> {
                                                        metadata.setProperty("sha256", sha256sum);
                                                        metadata.setProperty("filename", artifactFile.getFileName().toString());
                                                      });
    if (reportsDir == null) {
      return;
    }

    // Dump all artifacts in artifact's parent directory
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(artifactFile.getParent())) {
      for (Path sourcePath : directoryStream) {
        if (!Files.isRegularFile(sourcePath)) continue;

        Path targetPath = reportsDir.resolve(sourcePath.getFileName());
        try {
          Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        catch (IOException e) {
          LOG.error("Unable to copy bad artifact " + sourcePath, e);
        }
      }
    }
    catch (IOException e) {
      LOG.error("Failed to open directory stream for " + artifactFile.getParent(), e);
    }
  }

  private static void reportMissingCompiledRootArtifacts(@NotNull CompileContext context,
                                                         @NotNull String libraryName,
                                                         @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                         @NotNull List<? extends Path> allRoots,
                                                         @NotNull List<? extends Path> missingRoots) {
    if (missingRoots.isEmpty() || !SystemProperties.getBooleanProperty(RESOLUTION_REPORT_INVALID_SHA256_CHECKSUM_PROPERTY, false)) {
      return;
    }

    Path reportsDir = createVerificationProblemReport(context, libraryName, descriptor, "missing_artifact", reportMetadata -> {
      reportMetadata.setProperty("all_roots_list", allRoots.toString());
      reportMetadata.setProperty("missing_roots_list", missingRoots.toString());
    });
    if (reportsDir == null) {
      return;
    }

    // publish .pom files to investigate why some files are not downloaded
    for (Path artifact : allRoots) {
      String possiblePomFileName = FileUtilRt.getNameWithoutExtension(artifact.getFileName().toString()) + ".pom";
      Path pomFile = artifact.getParent().resolve(possiblePomFileName);

      if (Files.exists(pomFile)) {
        try {
          Path pomFileCopy = reportsDir.resolve(pomFile.getFileName());
          Files.copy(pomFile, pomFileCopy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
        catch (IOException e) {
          LOG.error("Unable to copy pom file " + pomFile, e);
        }
      }
    }
  }

  private static @Nullable Path createVerificationProblemReport(@NotNull CompileContext context,
                                                                @NotNull String libraryName,
                                                                @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                                                @NotNull String problemKind,
                                                                @NotNull Consumer<? super Properties> metadataWriter) {
    String outputDirPath = System.getProperty(RESOLUTION_CORRUPTED_ARTIFACTS_REPORTS_DIRECTORY_PROPERTY, null);
    if (outputDirPath == null) {
      return null;
    }
    PathRelativizerService relativizerService = context.getProjectDescriptor().dataManager.getRelativizer();
    Path corruptedArtifactsReportsDir = Path.of(relativizerService.toFull(outputDirPath));

    Path libraryReportDir;
    try {
      Files.createDirectories(corruptedArtifactsReportsDir);
      libraryReportDir = Files.createTempDirectory(corruptedArtifactsReportsDir, problemKind + "_");
    } catch (IOException e) {
      throw new RuntimeException("Failed to create reports directory", e);
    }

    Properties metadata = new Properties();
    metadata.setProperty("libraryName", libraryName);
    metadata.setProperty("mavenId", descriptor.getMavenId());
    metadata.setProperty("problem", problemKind);
    metadataWriter.accept(metadata);

    try {
      Path artifactCopyDescriptionPath = Files.createFile(libraryReportDir.resolve("report.properties"));
      try (OutputStream os = Files.newOutputStream(artifactCopyDescriptionPath)) {
        metadata.store(os, null);
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to write report.properties", e);
    }
    return libraryReportDir;
  }

  private static final class JpsLibraryResolveGuard {

    private static final Key<ConcurrentHashMap<Object, Guard>> CONTEXT_KEY = GlobalContextKey.create("_dependency_resolving_builder_guards_");

    @FunctionalInterface
    private interface ThrowingRunnable {
      void run() throws Exception;
    }

    private static void init(CompileContext context) {
      context.putUserData(CONTEXT_KEY, new ConcurrentHashMap<>());
    }

    private static void performUnderGuard(@NotNull CompileContext context,
                                          @NotNull JpsMavenRepositoryLibraryDescriptor descriptor,
                                          @NotNull List<? extends File> roots,
                                          @NotNull ThrowingRunnable action) throws Exception {
      Stream<Guard> descriptorGuard = Stream.of(getDescriptorGuard(context, descriptor));
      Stream<Guard> rootsGuards = roots.stream()
        .map(rootFile -> Path.of(FileUtil.toCanonicalPath(rootFile.getAbsolutePath())))
        // sort and filter equal guards to acquire locks always in similar order and prevent deadlocks
        .sorted()
        .distinct()
        .map(rootAbsolutePath -> getRootGuard(context, rootAbsolutePath));

      List<Guard> guards = Stream.concat(descriptorGuard, rootsGuards).collect(Collectors.toList());
      int lockedGuardsCounter = 0;
      try {
        for (Guard guard : guards) {
          if (!guard.requestProcessing(context.getCancelStatus())) {
            return;
          }
          lockedGuardsCounter++;
        }

        action.run();
      }
      finally {
        // release only guards we're holding; release in reversed order to prevent deadlocks
        for (Guard guard : ContainerUtil.reverse(guards.subList(0, lockedGuardsCounter))) {
          guard.finish();
        }
      }
    }

    private static Guard getDescriptorGuard(@NotNull CompileContext context, @NotNull JpsMavenRepositoryLibraryDescriptor descriptor) {
      Map<Object, Guard> map = context.getUserData(CONTEXT_KEY);
      assert map != null;
      return map.computeIfAbsent(descriptor, (ignored) ->
        new Guard(true) // descriptor must not be processed more than once
      );
    }

    private static Guard getRootGuard(@NotNull CompileContext context, @NotNull Path rootPath) {
      Map<Object, Guard> map = context.getUserData(CONTEXT_KEY);
      assert map != null;
      return map.computeIfAbsent(rootPath, (ignored) ->
        new Guard(false) // root can be processed more than once (similar dependency root can be used in multiple libraries)
      );
    }

    private static final class Guard {
      private static final byte INITIAL = 0;
      private static final byte PROGRESS = 1;
      private static final byte FINISHED = 2;
      private byte myState = INITIAL;
      private final boolean mySingleProcessingGuard;

      /**
       * Create new guard.
       * @param isSingleProcessingGuard If true and {@link Guard#requestProcessing(CanceledStatus)} will allow processing (return true)
       *                                only once, otherwise multiple processing repeats is allowed.
       */
      private Guard(boolean isSingleProcessingGuard) {
        mySingleProcessingGuard = isSingleProcessingGuard;
      }

      private synchronized boolean requestProcessing(CanceledStatus canceledStatus) {
        if (canceledStatus.isCanceled()) return false;

        if (myState == INITIAL) {
          myState = PROGRESS;
          return true;
        }

        // wait until another thread completes its work with the guarded object
        while (myState == PROGRESS && !canceledStatus.isCanceled()) {
          try {
            wait(100L);
          } catch (InterruptedException ignored) {
          }
        }

        // allow processing only if not cancelled, and myState is reset from PROGRESS to INITIAL
        return myState == INITIAL && !canceledStatus.isCanceled();
      }

      private synchronized void finish() {
        if (myState != FINISHED) {
          myState = mySingleProcessingGuard ? FINISHED : INITIAL;
          notifyAll();
        }
      }
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

  @ApiStatus.Internal
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
    
    Pair<ArtifactRepositoryManager, Map<String, ArtifactRepositoryManager>> managers = MANAGERS_KEY.get(context);
    if (managers == null) {
      Map<String, JpsMavenSettings.RemoteRepositoryAuthentication> mavenSettingsXmlAuth = JpsMavenSettings.loadAuthenticationSettings(
        JpsMavenSettings.getGlobalMavenSettingsXml(),
        JpsMavenSettings.getUserMavenSettingsXml()
      );
      final List<RemoteRepository> repositories = new SmartList<>();
      for (JpsRemoteRepositoryDescription repo : JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(context.getProjectDescriptor().getProject())
          .getRepositories()) {
        ArtifactAuthenticationData authenticationData = obtainRemoteRepositoryAuthenticationData(repo, mavenSettingsXmlAuth);
        repositories.add(ArtifactRepositoryManager.createRemoteRepository(repo.getId(), repo.getUrl(), authenticationData));
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

      final ArtifactRepositoryManager unnamedManager = new ArtifactRepositoryManager(localRepositoryRoot, repositories, progressConsumer, retry);
      // further init manager here
      
      final Map<String, ArtifactRepositoryManager> namedManagers = new HashMap<>();
      for (RemoteRepository repository : repositories) {
        namedManagers.put(repository.getId(), new ArtifactRepositoryManager(localRepositoryRoot, Collections.singletonList(repository), progressConsumer, retry));
      }

      MANAGERS_KEY.set(context, managers = Pair.create(unnamedManager, Collections.unmodifiableMap(namedManagers)));
    }

    if (remoteRepositoryId == null) {
      return managers.getFirst();
    }
    final ArtifactRepositoryManager namedManager = managers.getSecond().get(remoteRepositoryId);
    if (namedManager == null) {
      throw new RemoteRepositoryNotFoundException(
        JpsBuildBundle.message("build.message.error.bind.repository.id.not.found", remoteRepositoryId)
      );
    }
    return namedManager;
  }

  /**
   * Obtain authentication for remote repository. Uses three sources for authentication data (ordered from higher to lower priority):
   * <ol>
   *   <li>System property with '{@link DependencyResolvingBuilder#REMOTE_REPOSITORY_AUTH_PROPERTY_PREFIX} + remote_repository_id' name and 'username:password' value.</li>
   *   <li>{@link DependencyAuthenticationDataProvider} extensions</li>
   *   <li>Maven's settings.xml file. See {@link JpsMavenSettings#loadAuthenticationSettings(File, File)}</li>
   * </ol>
   *
   * @param description          Remote repo
   * @param mavenSettingsXmlAuth Settings obtained from {@link JpsMavenSettings#loadAuthenticationSettings(File, File)}
   * @return Authentication data or null, if no suitable authentication is found.
   */
  private static @Nullable ArtifactAuthenticationData obtainRemoteRepositoryAuthenticationData(
    @NotNull JpsRemoteRepositoryDescription description,
    @NotNull Map<String, JpsMavenSettings.RemoteRepositoryAuthentication> mavenSettingsXmlAuth
  ) {
    ArtifactAuthenticationData fromProperty = loadRemoteRepositoryAuthenticationFromSystemProperty(description);
    if (fromProperty != null) {
      return fromProperty;
    }

    String url = description.getUrl();
    for (DependencyAuthenticationDataProvider provider : JpsServiceManager.getInstance()
      .getExtensions(DependencyAuthenticationDataProvider.class)) {
      DependencyAuthenticationDataProvider.AuthenticationData authData = provider.provideAuthenticationData(url);
      if (authData != null) {
        return new ArtifactRepositoryManager.ArtifactAuthenticationData(authData.getUserName(), authData.getPassword());
      }
    }

    JpsMavenSettings.RemoteRepositoryAuthentication fromMavenSettings = mavenSettingsXmlAuth.get(description.getId());
    if (fromMavenSettings != null) {
      return new ArtifactAuthenticationData(fromMavenSettings.getUsername(), fromMavenSettings.getPassword());
    }
    return null;
  }

  private static @Nullable ArtifactAuthenticationData loadRemoteRepositoryAuthenticationFromSystemProperty(
    @NotNull JpsRemoteRepositoryDescription description
  ) {
    String propertyName = REMOTE_REPOSITORY_AUTH_PROPERTY_PREFIX + description.getId();
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue == null) {
      return null;
    }

    // Expected 'username:password' property value.
    // Use of ':' as separator is OK - the same one is used in HTTP Basic Authentication (RFC 7617).
    String[] tokens = propertyValue.split(":");
    if (tokens.length != 2) {
      throw new IllegalArgumentException("Malformed remote repository authentication system property for repository with id '" +
                                         description.getId() + "'. " +
                                         "Expected system property format is '" + propertyName + "=username:password'");
    }

    String username = tokens[0];
    String password = tokens[1];
    return new ArtifactAuthenticationData(username, password);
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

  private static @NotNull @Nls String getBuilderName() {
    return JpsBuildBundle.message("builder.name.maven.dependency.resolver");
  }

  private static final class ArtifactVerificationException extends ProjectBuildException {
    ArtifactVerificationException(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message) {
      super(message);
    }
  }

  private static final class RemoteRepositoryNotFoundException extends ProjectBuildException {
    RemoteRepositoryNotFoundException(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message) {
      super(message);
    }
  }
}
