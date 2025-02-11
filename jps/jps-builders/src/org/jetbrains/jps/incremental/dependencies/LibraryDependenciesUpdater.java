// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
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
  private final Map<Path, Collection<String>> myLibraryNameIndex = new HashMap<>(); // libraryRoot -> collection of library names, which include the root
  private final Set<Path> myProcessedRoots = new HashSet<>();

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

    if (!JavaBuilderUtil.isTrackLibraryDependenciesEnabled() || context.isCanceled()) {
      return true;
    }

    ProjectDescriptor pd = context.getProjectDescriptor();
    BuildDataManager dataManager = pd.dataManager;
    GraphConfiguration graphConfig = Objects.requireNonNull(dataManager.getDependencyGraph());

    DependencyGraph graph = graphConfig.getGraph();
    NodeSourcePathMapper pathMapper = graphConfig.getPathMapper();
    boolean isFullRebuild = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context);

    Set<Path> deletedLibRoots;
    Map<Path, String> libsRootsToUpdate = new HashMap<>();
    LibraryRoots libraryRoots = dataManager.getLibraryRoots();
    
    if (myIsDeletedLibrariesProcessed) {
      deletedLibRoots = new SmartHashSet<>();
    }
    else {
      myIsDeletedLibrariesProcessed = true;
      deletedLibRoots = libraryRoots.getRoots(new HashSet<>());
      Set<Path> presentPaths = new HashSet<>();
      for (JpsLibrary library : JpsJavaExtensionService.dependencies(pd.getProject()).getLibraries()) { // all libraries currently used in the project
        for (Path libRoot : filter(library.getPaths(JpsOrderRootType.COMPILED), LibraryDef::isLibraryPath)) {
          presentPaths.add(libRoot);
          myLibraryNameIndex.computeIfAbsent(libRoot, p -> new SmartHashSet<>()).add(library.getName());
        }
      }
      for (Path libRoot : presentPaths) {
        String oldLibName = libraryRoots.getLibraryName(libRoot);
        if (oldLibName != null) { // the root existed before
          String presentLibName = getCompoundLibraryName(libRoot);
          if (!oldLibName.equals(presentLibName)) { // the root is now associated with a different set of libraries
            libsRootsToUpdate.put(libRoot, presentLibName);
          }
        }
      }

      deletedLibRoots.removeAll(presentPaths);
      for (Path deletedPath : deletedLibRoots) {
        libraryRoots.remove(deletedPath);
      }
    }

    for (JpsLibrary library : uniqueBy(flat(map(chunk.getModules(), m -> JpsJavaExtensionService.dependencies(m).recursivelyExportedOnly().getLibraries())), () -> {
      Set<String> processed = new HashSet<>();
      return lib -> processed.add(lib.getName());
    })) {
      for (Path libRoot : flat(filter(library.getPaths(JpsOrderRootType.COMPILED), LibraryDef::isLibraryPath), Set.copyOf(libsRootsToUpdate.keySet()))) {
        if (!myProcessedRoots.add(libRoot)) {
          continue;
        }
        BasicFileAttributes attribs = getFileAttributes(libRoot);
        if (attribs != null) {
          if (attribs.isRegularFile()) {
            long currentStamp = FSOperations.lastModified(libRoot, attribs);
            String libName = libsRootsToUpdate.get(libRoot); // might be already marked for update
            if (libName == null) {
              libName = getCompoundLibraryName(libRoot);
            }
            if (libraryRoots.update(libRoot, libName, currentStamp)) {
              // if actually exists, is not a directory and is not up-to-date
              libsRootsToUpdate.put(libRoot, libName);
            }
          }
        }
        else {
          // the library is defined in the project, but does not exist on disk => is effectively deleted
          if (libraryRoots.remove(libRoot)) {
            libsRootsToUpdate.remove(libRoot); // might be already marked for update
            deletedLibRoots.add(libRoot);
          }
        }
      }
    }

    if (libsRootsToUpdate.isEmpty() && isEmpty(deletedLibRoots)) {
      return true;
    }

    context.processMessage(new ProgressMessage(
      JpsBuildBundle.message("progress.message.updating.library.state", libsRootsToUpdate.size(), count(deletedLibRoots), chunk.getPresentableShortName()))
    );

    try {
      Delta delta = graph.createDelta(map(libsRootsToUpdate.keySet(), pathMapper::toNodeSource), map(deletedLibRoots, pathMapper::toNodeSource), false);
      LibraryNodesBuilder nodesBuilder = new LibraryNodesBuilder(graphConfig);
      for (Map.Entry<Path, String> entry : libsRootsToUpdate.entrySet()) {
        Path libRoot = entry.getKey();
        NodeSource src = pathMapper.toNodeSource(libRoot);
        Set<NodeSource> sources = Set.of(src);
        int nodeCount = 0;
        for (Node<?, ?> node : nodesBuilder.processLibraryRoot(entry.getValue(), src)) {
          nodeCount++;
          delta.associate(node, sources);
        }
        context.processMessage(new ProgressMessage(
          JpsBuildBundle.message("progress.message.processing.library", libRoot.getFileName(), nodeCount)
        ));
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

        markAffectedFilesDirty(context, chunk, map(affectedSources, pathMapper::toPath));
      }

      graph.integrate(diffResult);

      return diffResult.isIncremental();
    }
    catch (Throwable e) {
      for (Path path : libsRootsToUpdate.keySet()) {
        // data from these libraries can be updated only partially
        // ensure they will be parsed next time
        libraryRoots.remove(path);
      }
      throw e;
    }
  }

  private @NotNull String getCompoundLibraryName(Path libRoot) {
    List<String> libNames = collect(myLibraryNameIndex.getOrDefault(libRoot, List.of()), new SmartList<>());
    Collections.sort(libNames);
    HashStream64 hash = Hashing.komihash5_0().hashStream();
    for (String name : libNames) {
      hash.putString(name);
    }
    return Long.toUnsignedString(hash.getAsLong(), Character.MAX_RADIX);
  }

  private BasicFileAttributes getFileAttributes(Path libRoot) {
    BasicFileAttributes attribs = myFileAttributesCache.get(libRoot);
    return attribs == NULL_ATTRIBUTES? null : attribs;
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

}
