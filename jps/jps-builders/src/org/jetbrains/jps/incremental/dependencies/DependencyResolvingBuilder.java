// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsPathVariablesConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene Zhuravlev
 */
public class DependencyResolvingBuilder extends ModuleLevelBuilder{
  private static final Logger LOG = Logger.getInstance(DependencyResolvingBuilder.class);
  private static final String NAME = "Maven Dependency Resolver";
  private static final String MAVEN_REPOSITORY_PATH_VAR = "MAVEN_REPOSITORY";
  private static final String DEFAULT_MAVEN_REPOSITORY_PATH = ".m2/repository";

  private static final Key<ArtifactRepositoryManager> MANAGER_KEY = Key.create("_artifact_repository_manager_");
  private static final Key<Exception> RESOLVE_ERROR_KEY = Key.create("_artifact_repository_resolve_error_");

  public DependencyResolvingBuilder() {
    super(BuilderCategory.INITIAL);
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull String getPresentableName() {
    return NAME;
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
    final StringBuilder builder = new StringBuilder().append("Error resolving dependencies for ").append(placePresentableName);
    Throwable th = error;
    final Set<Throwable> processed = new HashSet<>();
    final Set<String> detailsMessage = new HashSet<>();
    while (th != null && processed.add(th)) {
      String details = th.getMessage();
      if (th instanceof UnknownHostException) {
        details = "Unknown host: " + details; // hack for UnknownHostException
      }
      if (details != null && detailsMessage.add(details)) {
        builder.append(":\n").append(details);
      }
      th = th.getCause();
    }
    final String msg = builder.toString();
    LOG.info(msg, error);
    context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, msg));
    return ExitCode.ABORT;
  }

  static void resolveMissingDependencies(CompileContext context, Collection<? extends JpsModule> modules,
                                         BuildTargetChunk currentTargets) throws Exception {
    Collection<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> libs = getRepositoryLibraries(modules);
    if (!libs.isEmpty()) {
      final ArtifactRepositoryManager repoManager = getRepositoryManager(context);
      for (JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> lib : libs) {
        final JpsMavenRepositoryLibraryDescriptor descriptor = lib.getProperties().getData();
        final ResourceGuard guard = ResourceGuard.get(context, descriptor);
        if (guard.requestProcessing(context.getCancelStatus())) {
          try {
            final Collection<File> required = lib.getFiles(JpsOrderRootType.COMPILED);
            for (Iterator<File> it = required.iterator(); it.hasNext(); ) {
              if (it.next().exists()) {
                it.remove(); // leaving only non-existing stuff requiring synchronization
              }
            }
            if (!required.isEmpty()) {
              context.processMessage(new ProgressMessage("Resolving '" + lib.getName() + "' library...", currentTargets));
              LOG.debug("Downloading missing files for " + lib.getName() + " library: " + required);
              final Collection<File> resolved = repoManager.resolveDependency(descriptor.getGroupId(), descriptor.getArtifactId(),
                                                                              descriptor.getVersion(), descriptor.isIncludeTransitiveDependencies(),
                                                                              descriptor.getExcludedDependencies());
              if (!resolved.isEmpty()) {
                syncPaths(required, resolved);
              }
              else {
                LOG.info("No artifacts were resolved for repository dependency " + descriptor.getMavenId());
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
    }
  }

  private static void syncPaths(final Collection<? extends File> required, @NotNull Collection<? extends File> resolved) throws Exception {
    final THashSet<File> libFiles = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
    libFiles.addAll(required);
    libFiles.removeAll(resolved);

    if (!libFiles.isEmpty()) {
      final Map<String, File> nameToArtifactMap = new THashMap<>(FileUtil.PATH_HASHING_STRATEGY);
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

  private static final class ResourceGuard {
    private static final Key<ConcurrentMap<JpsMavenRepositoryLibraryDescriptor, ResourceGuard>> CONTEXT_KEY = GlobalContextKey.create("_artifact_repository_resolved_libraries_");
    private static final byte INITIAL = 0;
    private static final byte PROGRESS = 1;
    private static final byte FINISHED = 2;
    private byte myState = INITIAL;

    synchronized boolean requestProcessing(final CanceledStatus cancelStatus) {
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

    synchronized void finish() {
      if (myState != FINISHED) {
        myState = FINISHED;
        this.notifyAll();
      }
    }

    static void init(CompileContext context) {
      context.putUserData(CONTEXT_KEY, new ConcurrentHashMap<JpsMavenRepositoryLibraryDescriptor, ResourceGuard>());
    }

    static @NotNull ResourceGuard get(CompileContext context, JpsMavenRepositoryLibraryDescriptor descriptor) {
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

  public static ArtifactRepositoryManager getRepositoryManager(final CompileContext context) {
    ArtifactRepositoryManager manager = MANAGER_KEY.get(context);
    if (manager == null) {

      final List<RemoteRepository> repositories = new SmartList<>();
      for (JpsRemoteRepositoryDescription repo : JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(context.getProjectDescriptor().getProject())
          .getRepositories()) {
        repositories.add(ArtifactRepositoryManager.createRemoteRepository(repo.getId(), repo.getUrl()));
      }
      manager = new ArtifactRepositoryManager(getLocalRepoDir(context), repositories, new ProgressConsumer() {
        @Override
        public void consume(String message) {
          context.processMessage(new ProgressMessage(message));
        }

        @Override
        public boolean isCanceled() {
          return context.getCancelStatus().isCanceled();
        }
      });
      // further init manager here
      MANAGER_KEY.set(context, manager);
    }
    return manager;
  }

  private static @NotNull File getLocalRepoDir(CompileContext context) {
    final JpsPathVariablesConfiguration pvConfig = JpsModelSerializationDataService.getPathVariablesConfiguration(context.getProjectDescriptor().getModel().getGlobal());
    final String localRepoPath = pvConfig != null? pvConfig.getUserVariableValue(MAVEN_REPOSITORY_PATH_VAR) : null;
    if (localRepoPath != null) {
      return new File(localRepoPath);
    }
    final String root = System.getProperty("user.home", null);
    return root != null ? new File(root, DEFAULT_MAVEN_REPOSITORY_PATH) : new File(DEFAULT_MAVEN_REPOSITORY_PATH);
  }
}
