// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.*;

/**
 * Update state of libraries used in the given project. Detect API changes and mark sources depending on changed APIs for recompilation
 *
 */
// todo: implement as a Builder?
@ApiStatus.Internal
public final class LibraryDependenciesUpdater {
  private static final Logger LOG = Logger.getInstance(LibraryDependenciesUpdater.class);
  
  private static final String MODULE_INFO_FILE = "module-info.java";

  private static final GlobalContextKey<Set<BuildTarget<?>>> PROCESSED_TARGETS_KEY = GlobalContextKey.create("__library_deps_updater_processed_targets__");
  private boolean myIsInitialized;
  private final Map<String, List<Path>> myDeletedRoots = new HashMap<>(); // namespace -> collection of roots, not associated with any module in the project
  private final Map<Path, String> myNamespaces = new HashMap<>(); // library root -> namespace
  private final Set<Path> myProcessedRoots = new HashSet<>();
  private long myTotalTimeNano = 0L;

  /**
   * @return true if can continue incrementally, false if non-incremental
   */
  public synchronized boolean update(CompileContext context, ModuleChunk chunk) throws IOException {

    if (!JavaBuilderUtil.isTrackLibraryDependenciesEnabled() || context.isCanceled()) {
      return true;
    }
    long start = System.nanoTime();

    ProjectDescriptor pd = context.getProjectDescriptor();
    BuildDataManager dataManager = pd.dataManager;
    GraphConfiguration graphConfig = Objects.requireNonNull(dataManager.getDependencyGraph());

    DependencyGraph graph = graphConfig.getGraph();
    NodeSourcePathMapper pathMapper = graphConfig.getPathMapper();
    boolean isFullRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);

    Set<Path> deletedRoots = new SmartHashSet<>();
    Set<Path> updatedRoots = new SmartHashSet<>();
    LibraryRoots libraryRoots = dataManager.getLibraryRoots();
    
    boolean errorsDetected = false;
    Set<ModuleBuildTarget> chunkTargets = chunk.getTargets();
    try {
      if (!myIsInitialized) {
        myIsInitialized = true;

        Map<Path, List<String>> present = new HashMap<>(); // libraryRoot -> collection of library names, which include the root
        for (JpsLibrary library : JpsJavaExtensionService.dependencies(pd.getProject()).getLibraries()) { // all libraries currently used in the project
          for (Path libRoot : filter(library.getPaths(JpsOrderRootType.COMPILED), LibraryDef::isLibraryPath)) {
            present.computeIfAbsent(libRoot, k -> new SmartList<>()).add(library.getName());
          }
        }

        HashStream64 hash = null;
        for (Map.Entry<Path, List<String>> entry : present.entrySet()) {
          if (hash == null) {
            hash = Hashing.komihash5_0().hashStream();
          }
          else {
            hash.reset();
          }
          List<String> libNames = entry.getValue();
          Collections.sort(libNames);
          for (String name : libNames) {
            hash.putString(name);
          }
          myNamespaces.put(entry.getKey(), Long.toUnsignedString(hash.getAsLong(), Character.MAX_RADIX));
        }

        Set<Path> past = libraryRoots.getRoots(new HashSet<>());
        past.removeAll(present.keySet());
        for (Path deletedRoot : past) {
          myDeletedRoots.computeIfAbsent(libraryRoots.getNamespace(deletedRoot), k -> new SmartList<>()).add(deletedRoot);
        }

        Set<String> deletedNamespaces = new HashSet<>(myDeletedRoots.keySet());
        deletedNamespaces.removeAll(myNamespaces.values());
        // add all deleted roots that won't fit in any namespace
        for (String ns : deletedNamespaces) {
          deletedRoots.addAll(myDeletedRoots.remove(ns));
        }

        LOG.info("LibraryDependencyUpdater initialized in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms");
      }

      for (JpsLibrary library : uniqueBy(flat(map(chunk.getModules(), m -> JpsJavaExtensionService.dependencies(m).recursivelyExportedOnly().getLibraries())), () -> {
        Set<String> processed = new HashSet<>();
        return lib -> processed.add(lib.getName());
      })) {
        for (Path libRoot : filter(library.getPaths(JpsOrderRootType.COMPILED), LibraryDef::isLibraryPath)) {
          if (!myProcessedRoots.add(libRoot)) {
            continue;
          }
          String namespace = myNamespaces.get(libRoot);

          // include in the Delta those deleted roots that produced nodes for the same namespace, where libsRootsToUpdate are going to contribute to
          Collection<Path> deleted = myDeletedRoots.remove(namespace);
          if (deleted != null) {
            deletedRoots.addAll(deleted);
          }

          BasicFileAttributes attribs = FSOperations.getAttributes(libRoot);
          if (attribs != null) {
            if (attribs.isRegularFile() && libraryRoots.update(libRoot, namespace, FSOperations.lastModified(libRoot, attribs))) {
              // if actually exists, is not a directory and has at lest namespace or timestamp changed
              updatedRoots.add(libRoot);
            }
          }
          else {
            // the library is defined in the project, but does not exist on disk => is effectively deleted
            deletedRoots.add(libRoot);
          }
        }
      }

      if (updatedRoots.isEmpty() && isEmpty(deletedRoots)) {
        return true;
      }

      sendProgress(
        context, JpsBuildBundle.message("progress.message.updating.library.state", updatedRoots.size(), count(deletedRoots), chunk.getPresentableShortName())
      );

      List<Pair<Path, NodeSource>> toUpdate = collect(map(updatedRoots, root -> Pair.create(root, pathMapper.toNodeSource(root))), new ArrayList<>());
      Delta delta = graph.createDelta(map(toUpdate, p -> p.getSecond()), map(deletedRoots, pathMapper::toNodeSource), false);
      LibraryNodesBuilder nodesBuilder = new LibraryNodesBuilder(graphConfig);
      for (var pair : toUpdate) {
        Path libRoot = pair.getFirst();
        NodeSource src = pair.getSecond();
        Set<NodeSource> sources = Set.of(src);
        int nodeCount = 0;
        for (Node<?, ?> node : nodesBuilder.processLibraryRoot(myNamespaces.get(libRoot), src)) {
          nodeCount++;
          delta.associate(node, sources);
        }
        sendProgress(
          context, JpsBuildBundle.message("progress.message.processing.library", libRoot.getFileName(), nodeCount)
        );
      }
      Set<BuildTarget<?>> processedTargets = Collections.unmodifiableSet(getProcessedTargets(context));
      DifferentiateParameters diffParams = DifferentiateParametersBuilder.create("libraries of " + chunk.getName())
        // affect project files that do not belong to already processed targets
        .withAffectionFilter(excludedFrom(processedTargets, context, pathMapper))
        .calculateAffected(!isFullRebuild)
        .withChunkStructureFilter(includedIn(chunkTargets, context, pathMapper))
        .get();
      DifferentiateResult diffResult = graph.differentiate(delta, diffParams);

      if (!diffResult.isIncremental()) {
        if (!isFullRebuild) {
          sendProgress(
            context, "Non-incremental mode: " + JpsBuildBundle.message("progress.message.marking.0.and.direct.dependants.for.recompilation", chunk.getPresentableShortName())
          );
          FSOperations.markDirtyRecursively(context, CompilationRound.CURRENT, chunk, null);
        }
      }
      else if (diffParams.isCalculateAffected()) {
        Iterable<NodeSource> affectedSources = diffResult.getAffectedSources();
        sendProgress(
          context, JpsBuildBundle.message("progress.message.dependency.analysis.found.0.affected.files", count(affectedSources))
        );
        markAffectedFilesDirty(context, chunk, map(affectedSources, pathMapper::toPath));
      }

      graph.integrate(diffResult);

      for (Path deletedRoot : deletedRoots) {
        libraryRoots.remove(deletedRoot);
      }

      return diffResult.isIncremental();
    }
    catch (Throwable e) {
      errorsDetected = true;
      for (Path path : updatedRoots) {
        // data from these libraries can be updated only partially
        // ensure they will be parsed next time
        libraryRoots.remove(path);
      }
      throw e;
    }
    finally {
      if (!errorsDetected) {
        getProcessedTargets(context).addAll(chunkTargets);
      }
      myTotalTimeNano += (System.nanoTime() - start);
      if (LOG.isDebugEnabled()) {
        LOG.debug("LibraryDependencyUpdater took " + TimeUnit.NANOSECONDS.toSeconds(myTotalTimeNano) + " seconds so far");
      }
    }
  }

  private static Set<BuildTarget<?>> getProcessedTargets(CompileContext context) {
    return PROCESSED_TARGETS_KEY.getOrCreate(context, () -> new HashSet<>());
  }

  private static Predicate<? super NodeSource> excludedFrom(Set<? extends BuildTarget<?>> targets, CompileContext context, NodeSourcePathMapper pathMapper) {
    return s -> {
      BuildTarget<?> fileTarget = getFileTarget(context, pathMapper.toPath(s));
      return fileTarget != null && !targets.contains(fileTarget);
    };
  }

  private static Predicate<? super NodeSource> includedIn(Set<? extends BuildTarget<?>> targets, CompileContext context, NodeSourcePathMapper pathMapper) {
    return s -> {
      BuildTarget<?> fileTarget = getFileTarget(context, pathMapper.toPath(s));
      return fileTarget != null && targets.contains(fileTarget);
    };
  }

  private static @Nullable BuildTarget<?> getFileTarget(CompileContext context, Path path) {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, path.toFile());
    return rd != null? rd.target : null;
  }

  private static void markAffectedFilesDirty(CompileContext context, ModuleChunk chunk, Iterable<? extends Path> affectedFiles) throws IOException {
    if (isEmpty(affectedFiles)) {
      return;
    }
    ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
    BuildRootIndex buildRootIndex = projectDescriptor.getBuildRootIndex();
    JavaModuleIndex moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(projectDescriptor.getProject());
    Set<ModuleBuildTarget> targetsToMark = new SmartHashSet<>();
    for (Path path : affectedFiles) {
      if (MODULE_INFO_FILE.equals(path.getFileName().toString())) {
        var asFile = path.toFile();
        final JavaSourceRootDescriptor rootDescr = buildRootIndex.findJavaRootDescriptor(context, asFile);
        if (rootDescr != null) {
          ModuleBuildTarget target = rootDescr.getTarget();
          if (FileUtil.filesEqual(moduleIndex.getModuleInfoFile(target.getModule(), target.isTests()), asFile)) {
            targetsToMark.add(target);
          }
        }
      }
      else {
        FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, path);
      }
    }
    if (find(chunk.getTargets(), targetsToMark::contains) != null) {
      targetsToMark.addAll(chunk.getTargets()); // ensure all chunk's targets are compiled together
    }
    for (ModuleBuildTarget target : targetsToMark) {
      context.markNonIncremental(target);
      FSOperations.markDirty(context, CompilationRound.CURRENT, target, null);
    }
  }

  private static void sendProgress(CompileContext context, @NlsSafe String message) {
    LOG.info(message);
    context.processMessage(new ProgressMessage(message));
  }
}
