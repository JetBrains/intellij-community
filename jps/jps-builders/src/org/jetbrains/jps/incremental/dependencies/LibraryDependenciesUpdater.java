// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.JpsBuildBundle;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
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
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * Update state of libraries used in the given project. Detect API changes and mark sources depending on changed APIs for recompilation
 */
public final class LibraryDependenciesUpdater {
  private static final Logger LOG = Logger.getInstance(LibraryDependenciesUpdater.class);
  
  private static final String MODULE_INFO_FILE = "module-info.java";

  private static final BasicFileAttributes NULL_ATTRIBUTES =
    (BasicFileAttributes)Proxy.newProxyInstance(LibraryDependenciesUpdater.class.getClassLoader(), new Class[]{BasicFileAttributes.class}, (proxy, method, args) -> null);
  private static final int ATTRIBUTES_CACHE_SIZE = 1024;
  private final LoadingCache<Path, BasicFileAttributes> myFileAttributesCache;

  private boolean myIsDeletedLibrariesProcessed;
  private final Set<Pair<String, NodeSource>> myProcessedSources = new HashSet<>();

  public LibraryDependenciesUpdater() {
    myFileAttributesCache = Caffeine.newBuilder().maximumSize(ATTRIBUTES_CACHE_SIZE).build(path -> {
      BasicFileAttributes attr = FSOperations.getAttributes(path);
      return attr != null? attr : NULL_ATTRIBUTES;
    });
  }

  /**
   * @return true if can continue incrementally, false if non-incremental
   */
  public synchronized boolean update(CompileContext context, ModuleChunk chunk, Predicate<? super NodeSource> chunkStructureFilter) throws IOException {

    if (!JavaBuilderUtil.isTrackLibraryDependenciesEnabled()) {
      return true;
    }

    ProjectDescriptor pd = context.getProjectDescriptor();
    BuildDataManager dataManager = pd.dataManager;
    GraphConfiguration graphConfig = Objects.requireNonNull(dataManager.getDependencyGraph());

    DependencyGraph graph = graphConfig.getGraph();
    NodeSourcePathMapper pathMapper = graphConfig.getPathMapper();
    boolean isFullRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);

    Iterable<NodeSource> deletedLibRoots;
    Map<Pair<String, NodeSource>, Long> libsToUpdate = new HashMap<>();
    LibraryRoots libraryRoots = dataManager.getLibraryRoots();
    
    if (myIsDeletedLibrariesProcessed) {
      deletedLibRoots = Set.of();
    }
    else {
      myIsDeletedLibrariesProcessed = true;
      if (isFullRebuild) {
        deletedLibRoots = Set.of();
      }
      else {
        Set<Path> deletedPaths = libraryRoots.getRoots();
        deletedPaths.removeAll( // present paths
          collect(filter(flat(map(JpsJavaExtensionService.dependencies(pd.getProject()).getLibraries(), lib -> lib.getPaths(JpsOrderRootType.COMPILED))), LibraryDef::isLibraryPath), new HashSet<>())
        );
        deletedLibRoots = map(deletedPaths, pathMapper::toNodeSource);
        for (Path deletedPath : deletedPaths) {
          libraryRoots.remove(deletedPath);
        }
      }
    }

    for (JpsLibrary library : uniqueBy(flat(map(chunk.getModules(), m -> JpsJavaExtensionService.dependencies(m).recursivelyExportedOnly().getLibraries())), () -> {
      Set<String> processed = new HashSet<>();
      return lib -> processed.add(lib.getName());
    })) {
      for (Path libRoot : filter(library.getPaths(JpsOrderRootType.COMPILED), LibraryDef::isLibraryPath)) {
        NodeSource src = pathMapper.toNodeSource(libRoot);
        BasicFileAttributes attribs = getFileAttributes(libRoot);
        if (attribs != null) {
          if (attribs.isRegularFile()) {
            long currentStamp = FSOperations.lastModified(libRoot, attribs);
            if (libraryRoots.update(libRoot, currentStamp) != currentStamp) {
              // if actually exists, is not a directory and is not up-to-date
              libsToUpdate.put(Pair.create(library.getName(), src), currentStamp);
            }
          }
        }
        else {
          // the library is defined in the project, but does not exist on disk
          libsToUpdate.put(Pair.create(library.getName(), src), -1L);
        }
      }
    }

    if (libsToUpdate.isEmpty() && isEmpty(deletedLibRoots)) {
      return true;
    }

    context.processMessage(new ProgressMessage(
      JpsBuildBundle.message("progress.message.updating.library.state", libsToUpdate.size(), count(deletedLibRoots), chunk.getPresentableShortName()))
    );

    Delta delta = graph.createDelta(map(filter(libsToUpdate.keySet(), p -> !myProcessedSources.contains(p)), p -> p.getSecond()), deletedLibRoots, false);
    LibraryNodesBuilder nodesBuilder = new LibraryNodesBuilder(graphConfig);
    for (Map.Entry<Pair<String, NodeSource>, Long> entry : libsToUpdate.entrySet()) {
      Pair<String, NodeSource> nameRoot = entry.getKey();
      if (!myProcessedSources.add(nameRoot) || entry.getValue() < 0L) {
        continue; // skip non-existing roots or already processed ones
      }
      String libName = nameRoot.getFirst();
      NodeSource libRoot = nameRoot.getSecond();
      Set<NodeSource> src = Set.of(libRoot);
      for (Node<?, ?> node : nodesBuilder.processLibraryRoot(libName, libRoot)) {
        delta.associate(node, src);
      }
    }

    DifferentiateParameters diffParams = DifferentiateParametersBuilder.create("libraries of " + chunk.getName())
      .withAffectionFilter(s -> !LibraryDef.isLibraryPath(s))
      .calculateAffected(!isFullRebuild)
      .withChunkStructureFilter(chunkStructureFilter)
      .get();
    DifferentiateResult diffResult = graph.differentiate(delta, diffParams);

    if (!diffResult.isIncremental()) {
      if (!isFullRebuild) {
        final String messageText = JpsBuildBundle.message("progress.message.marking.0.and.direct.dependants.for.recompilation", chunk.getPresentableShortName());
        LOG.info("Non-incremental mode: " + messageText);
        context.processMessage(new ProgressMessage(messageText));
        FSOperations.markDirtyRecursively(context, CompilationRound.CURRENT, chunk, null);
      }
    }
    else if (diffParams.isCalculateAffected()) {
      Iterable<NodeSource> affectedSources = diffResult.getAffectedSources();
      final String infoMessage = JpsBuildBundle.message("progress.message.dependency.analysis.found.0.affected.files", count(affectedSources));
      LOG.info(infoMessage);
      context.processMessage(new ProgressMessage(infoMessage));

      markAffectedFilesDirty(context, map(affectedSources, ns -> pathMapper.toPath(ns)));
    }

    graph.integrate(diffResult);

    return diffResult.isIncremental();
  }

  private BasicFileAttributes getFileAttributes(Path libRoot) {
    BasicFileAttributes attribs = myFileAttributesCache.get(libRoot);
    return attribs == NULL_ATTRIBUTES? null : attribs;
  }

  private static void markAffectedFilesDirty(CompileContext context, Iterable<? extends Path> affectedFiles) throws IOException {
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
    for (ModuleBuildTarget target : targetsToMark) {
      context.markNonIncremental(target);
      FSOperations.markDirty(context, CompilationRound.CURRENT, target, null);
    }
  }

}
