// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.GlobalOptions;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.impl.DifferentiateParametersBuilder;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.dependencies.LibraryDef;
import org.jetbrains.jps.incremental.dependencies.LibraryDependenciesUpdater;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.Iterators;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

public final class JavaBuilderUtil {

  private static final Logger LOG = Logger.getInstance(Builder.class);
  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");
  private static final Key<Set<File>> FILES_TO_COMPILE_KEY = Key.create("_files_to_compile_");
  private static final Key<Set<File>> COMPILED_WITH_ERRORS_KEY = Key.create("_compiled_with_errors_");
  private static final Key<Set<File>> SUCCESSFULLY_COMPILED_FILES_KEY = Key.create("_successfully_compiled_files_");
  private static final Key<List<FileFilter>> SKIP_MARKING_DIRTY_FILTERS_KEY = Key.create("_skip_marking_dirty_filters_");
  private static final Key<Pair<Mappings, Callbacks.Backend>> MAPPINGS_DELTA_KEY = Key.create("_mappings_delta_");
  private static final Key<BackendCallbackToGraphDeltaAdapter> GRAPH_DELTA_CALLBACK_KEY = Key.create("_graph_delta_");
  private static final Key<Set<NodeSource>> ALL_AFFECTED_NODE_SOURCES_KEY = Key.create("_all_compiled_node_sources_");
  private static final GlobalContextKey<LibraryDependenciesUpdater> LIBRARIES_STATE_UPDATER_KEY = GlobalContextKey.create("_libraries_state_updater_");

  private static final String MODULE_INFO_FILE = "module-info.java";

  public static boolean isDepGraphEnabled() {
    return Boolean.parseBoolean(System.getProperty(GlobalOptions.DEPENDENCY_GRAPH_ENABLED, "false"));
  }
  
  @ApiStatus.Internal
  public static boolean isTrackLibraryDependenciesEnabled() {
    return isDepGraphEnabled() && Boolean.parseBoolean(System.getProperty(GlobalOptions.TRACK_LIBRARY_DEPENDENCIES_ENABLED, "false"));
  }

  public static void registerFileToCompile(CompileContext context, File file) {
    registerFilesToCompile(context, Collections.singleton(file));
  }

  public static void registerFilesToCompile(CompileContext context, Collection<? extends File> files) {
    getFilesContainer(context, FILES_TO_COMPILE_KEY).addAll(files);
  }

  public static void registerFilesWithErrors(CompileContext context, Collection<? extends File> files) {
    getFilesContainer(context, COMPILED_WITH_ERRORS_KEY).addAll(files);
  }

  public static void registerSuccessfullyCompiled(CompileContext context, File file) {
    registerSuccessfullyCompiled(context, Collections.singleton(file));
  }

  public static void registerSuccessfullyCompiled(CompileContext context, Collection<? extends File> files) {
    getFilesContainer(context, SUCCESSFULLY_COMPILED_FILES_KEY).addAll(files);
  }

  /**
   * The files accepted by {@code filter} won't be marked dirty by {@link #updateMappings} method when this compilation round finishes.
   * Call this method from {@link ModuleLevelBuilder#build} to register a filter accepting files of your language if you compute and mark
   * as dirty affected files yourself.
   */
  public static void registerFilterToSkipMarkingAffectedFileDirty(@NotNull CompileContext context, @NotNull FileFilter filter) {
    List<FileFilter> filters = SKIP_MARKING_DIRTY_FILTERS_KEY.get(context);
    if (filters == null) {
      SKIP_MARKING_DIRTY_FILTERS_KEY.set(context, filters = new ArrayList<>());
    }
    filters.add(filter);
  }

  public static @NotNull Callbacks.Backend getDependenciesRegistrar(CompileContext context) {
    GraphConfiguration graphConfig = context.getProjectDescriptor().dataManager.getDependencyGraph();
    if (isDepGraphEnabled() && graphConfig != null) {
      BackendCallbackToGraphDeltaAdapter callback = GRAPH_DELTA_CALLBACK_KEY.get(context);
      if (callback == null) {
        GRAPH_DELTA_CALLBACK_KEY.set(context, callback = new BackendCallbackToGraphDeltaAdapter(graphConfig));
      }
      return callback;
    }

    Pair<Mappings, Callbacks.Backend> pair = MAPPINGS_DELTA_KEY.get(context);
    if (pair == null) {
      final Mappings delta = context.getProjectDescriptor().dataManager.getMappings().createDelta();
      pair = Pair.create(delta, delta.getCallback());
      MAPPINGS_DELTA_KEY.set(context, pair);
    }
    return pair.second;
  }

  @ApiStatus.Internal
  public static boolean updateMappingsOnRoundCompletion(
    CompileContext context, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, ModuleChunk chunk) throws IOException {

    BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    GraphConfiguration graphConfig = dataManager.getDependencyGraph();
    if(isDepGraphEnabled() && graphConfig != null) {
      Delta delta = null;
      Set<File> inputFiles = getFilesContainer(context, FILES_TO_COMPILE_KEY);
      Set<String> deletedFiles = getRemovedPaths(chunk, dirtyFilesHolder);
      BackendCallbackToGraphDeltaAdapter callback = GRAPH_DELTA_CALLBACK_KEY.get(context);
      if (callback != null || !inputFiles.isEmpty() || !deletedFiles.isEmpty()) {
        delta = graphConfig.getGraph().createDelta(
          Iterators.map(inputFiles, graphConfig.getPathMapper()::toNodeSource),
          Iterators.map(deletedFiles, graphConfig.getPathMapper()::toNodeSource),
          false
        );
        if (callback != null) {
          for (var nodeData : callback.getNodes()) {
            delta.associate(nodeData.getFirst(), nodeData.getSecond());
          }
        }
      }

      for (Key<?> key : List.of(GRAPH_DELTA_CALLBACK_KEY, FILES_TO_COMPILE_KEY, COMPILED_WITH_ERRORS_KEY, SUCCESSFULLY_COMPILED_FILES_KEY)) {
        key.set(context, null);
      }
      return delta != null && updateDependencyGraph(context, delta, chunk, CompilationRound.NEXT, createOrFilter(SKIP_MARKING_DIRTY_FILTERS_KEY.get(context)));
    }

    Mappings delta = null;

    final Pair<Mappings, Callbacks.Backend> pair = MAPPINGS_DELTA_KEY.get(context);
    if (pair != null) {
      MAPPINGS_DELTA_KEY.set(context, null);
      delta = pair.getFirst();
    }

    if (delta == null) {
      return false;
    }
    final Set<File> compiledFiles = getFilesContainer(context, FILES_TO_COMPILE_KEY);
    FILES_TO_COMPILE_KEY.set(context, null);
    final Set<File> successfullyCompiled = getFilesContainer(context, SUCCESSFULLY_COMPILED_FILES_KEY);
    SUCCESSFULLY_COMPILED_FILES_KEY.set(context, null);
    FileFilter filter = createOrFilter(SKIP_MARKING_DIRTY_FILTERS_KEY.get(context));
    return updateMappings(context, delta, dirtyFilesHolder, chunk, compiledFiles, successfullyCompiled, CompilationRound.NEXT, filter);
  }

  public static void clearDataOnRoundCompletion(CompileContext context) {
    //during next compilation round ModuleLevelBuilders may register filters again so we need to remove old ones to avoid duplicating instances
    SKIP_MARKING_DIRTY_FILTERS_KEY.set(context, null);
  }

  /**
   * @deprecated this method isn't supposed to be called by plugins anymore, the mappings are updated
   * by the build process infrastructure automatically. Use {@link #getDependenciesRegistrar(CompileContext)},
   * {@link #registerFilesToCompile(CompileContext, Collection)}, or
   * {@link #registerSuccessfullyCompiled(CompileContext, Collection)} instead.
   */
  @Deprecated
  @ApiStatus.Internal
  public static boolean updateMappings(CompileContext context,
                                       final Mappings delta,
                                       DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                       ModuleChunk chunk,
                                       Collection<? extends File> filesToCompile,
                                       Collection<? extends File> successfullyCompiled) throws IOException {
    return updateMappings(context, delta, dirtyFilesHolder, chunk, filesToCompile, successfullyCompiled, CompilationRound.NEXT, null);
  }

  public static void markDirtyDependenciesForInitialRound(CompileContext context, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dfh, ModuleChunk chunk) throws IOException {
    BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    GraphConfiguration graphConfig = dataManager.getDependencyGraph();
    if (isDepGraphEnabled() && graphConfig != null) {
      NodeSourcePathMapper mapper = graphConfig.getPathMapper();

      boolean incremental = LIBRARIES_STATE_UPDATER_KEY.getOrCreate(context, LibraryDependenciesUpdater::new).update(context, chunk);
      if (!incremental) {
        // for now conservative approach; no reaction
        LOG.warn("Libraries update for " + chunk.getPresentableShortName() + " returned non-incremental exitcode");
      }

      if (context.isCanceled()) {
        return;
      }

      Set<NodeSource> toCompile = new HashSet<>();
      dfh.processDirtyFiles((target, file, root) -> toCompile.add(mapper.toNodeSource(file)));
      if (!toCompile.isEmpty() || hasRemovedPaths(chunk, dfh)) {
        Delta delta = graphConfig.getGraph().createDelta(
          toCompile, Iterators.map(getRemovedPaths(chunk, dfh), mapper::toNodeSource), true
        );
        updateDependencyGraph(context, delta, chunk, CompilationRound.CURRENT, null);
      }
    }
    else {
      if (hasRemovedPaths(chunk, dfh)) {
        final Mappings delta = dataManager.getMappings().createDelta();
        final Set<File> empty = Collections.emptySet();
        updateMappings(context, delta, dfh, chunk, empty, empty, CompilationRound.CURRENT, null);
      }
    }
  }

  /**
   * @param filesToCompile   files compiled in this round
   * @param markDirtyRound   compilation round at which dirty files should be visible to builders
   * @return true if additional compilation pass is required, false otherwise
   */
  private static boolean updateMappings(CompileContext context,
                                        final Mappings delta,
                                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                        ModuleChunk chunk,
                                        Collection<? extends File> filesToCompile,
                                        Collection<? extends File> successfullyCompiled,
                                        final CompilationRound markDirtyRound,
                                        @Nullable FileFilter skipMarkingDirtyFilter) throws IOException {
    try {
      boolean performIntegrate = true;
      boolean additionalPassRequired = false;

      final Set<String> removedPaths = getRemovedPaths(chunk, dirtyFilesHolder);

      final Mappings globalMappings = context.getProjectDescriptor().dataManager.getMappings();

      final boolean errorsDetected = Utils.errorsDetected(context);
      if (!isForcedRecompilationAllJavaModules(context)) {
        if (context.shouldDifferentiate(chunk)) {
          context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.checking.dependencies.0", chunk.getPresentableShortName())));
          final Set<File> allCompiledFiles = getFilesContainer(context, ALL_COMPILED_FILES_KEY);
          final Set<File> allAffectedFiles = getFilesContainer(context, ALL_AFFECTED_FILES_KEY);

          // mark as affected all files that were dirty before compilation
          allAffectedFiles.addAll(filesToCompile);
          // accumulate all successfully compiled in this round
          allCompiledFiles.addAll(successfullyCompiled);
          // unmark as affected all successfully compiled
          allAffectedFiles.removeAll(successfullyCompiled);

          final Set<File> affectedBeforeDif = FileCollectionFactory.createCanonicalFileSet();
          affectedBeforeDif.addAll(allAffectedFiles);

          final Set<File> compiledWithErrors = getFilesContainer(context, COMPILED_WITH_ERRORS_KEY);
          COMPILED_WITH_ERRORS_KEY.set(context, null);

          final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
          final boolean incremental = globalMappings.differentiateOnIncrementalMake(
            delta, removedPaths, filesToCompile, compiledWithErrors, allCompiledFiles, allAffectedFiles, moduleBasedFilter
          );

          if (LOG.isDebugEnabled()) {
            LOG.debug("Differentiate Results:");
            LOG.debug("   Compiled Files:");
            for (final File c : allCompiledFiles) {
              LOG.debug("      " + c.getAbsolutePath());
            }
            LOG.debug("   Affected Files:");
            for (final File c : allAffectedFiles) {
              LOG.debug("      " + c.getAbsolutePath());
            }
            LOG.debug("End Of Differentiate Results.");
          }

          final boolean compilingIncrementally = isCompileJavaIncrementally(context);
          if (incremental) {
            final Set<File> newlyAffectedFiles = new HashSet<>(allAffectedFiles);
            newlyAffectedFiles.removeAll(affectedBeforeDif);

            final String infoMessage = JpsBuildBundle.message("progress.message.dependency.analysis.found.0.affected.files", newlyAffectedFiles.size());
            LOG.info(infoMessage);
            context.processMessage(new ProgressMessage(infoMessage));

            removeFilesAcceptedByFilter(newlyAffectedFiles, skipMarkingDirtyFilter);

            if (!newlyAffectedFiles.isEmpty()) {

              if (LOG.isDebugEnabled()) {
                for (File file : newlyAffectedFiles) {
                  LOG.debug("affected file: " + file.getPath());
                }
                final List<Pair<File, JpsModule>> wrongFiles =
                  checkAffectedFilesInCorrectModules(context, newlyAffectedFiles, moduleBasedFilter);
                if (!wrongFiles.isEmpty()) {
                  LOG.debug("Wrong affected files for module chunk " + chunk.getName() + ": ");
                  for (Pair<File, JpsModule> pair : wrongFiles) {
                    final String name = pair.second != null ? pair.second.getName() : "null";
                    LOG.debug("\t[" + name + "] " + pair.first.getPath());
                  }
                }
              }

              Set<ModuleBuildTarget> targetsToMark = null;
              final JavaModuleIndex moduleIndex = getJavaModuleIndex(context);
              for (File file : newlyAffectedFiles) {
                if (MODULE_INFO_FILE.equals(file.getName())) {
                  final JavaSourceRootDescriptor rootDescr = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
                  if (rootDescr != null) {
                    final ModuleBuildTarget target = rootDescr.getTarget();
                    final File targetModuleInfo = moduleIndex.getModuleInfoFile(target.getModule(), target.isTests());
                    if (FileUtil.filesEqual(targetModuleInfo, file)) {
                      if (targetsToMark == null) {
                        targetsToMark = new HashSet<>(); // lazy init
                      }
                      targetsToMark.add(target);
                    }
                  }
                }
                else {
                  FSOperations.markDirtyIfNotDeleted(context, markDirtyRound, file.toPath());
                }
              }

              boolean currentChunkAfected = false;
              if (targetsToMark != null) {
                for (ModuleBuildTarget target : targetsToMark) {
                  if (chunk.getTargets().contains(target)) {
                    currentChunkAfected = true;
                  }
                  else {
                    FSOperations.markDirty(context, markDirtyRound, target, null);
                  }
                }
                if (currentChunkAfected) {
                  if (compilingIncrementally) {
                    // turn on non-incremental mode for targets from the current chunk, if at least one of them was affected.
                    for (ModuleBuildTarget target : chunk.getTargets()) {
                      context.markNonIncremental(target);
                    }
                  }
                  FSOperations.markDirty(context, markDirtyRound, chunk, null);
                }
              }
              additionalPassRequired = compilingIncrementally && (currentChunkAfected || moduleBasedFilter.containsFilesFromCurrentTargetChunk(newlyAffectedFiles));
            }
          }
          else {
            // non-incremental mode
            final String messageText = JpsBuildBundle.message("progress.message.marking.0.and.direct.dependants.for.recompilation", chunk.getPresentableShortName());
            LOG.info("Non-incremental mode: " + messageText);
            context.processMessage(new ProgressMessage(messageText));

            final boolean alreadyMarkedDirty = FSOperations.isMarkedDirty(context, chunk);
            additionalPassRequired = compilingIncrementally && !alreadyMarkedDirty;

            if (alreadyMarkedDirty) {
              // need this to make sure changes data stored in Delta is complete
              globalMappings.differentiateOnNonIncrementalMake(delta, removedPaths, filesToCompile);
            }
            else {
              performIntegrate = false;
            }

            FileFilter toBeMarkedFilter = skipMarkingDirtyFilter == null ? null : new NegationFileFilter(skipMarkingDirtyFilter);
            FSOperations.markDirtyRecursively(context, markDirtyRound, chunk, toBeMarkedFilter);
          }
        }
        else {
          if (!errorsDetected) { // makes sense only if we are going to integrate changes
            globalMappings.differentiateOnNonIncrementalMake(delta, removedPaths, filesToCompile);
          }
        }
      }
      else {
        if (!errorsDetected) { // makes sense only if we are going to integrate changes
          globalMappings.differentiateOnRebuild(delta);
        }
      }

      if (errorsDetected) {
        // important: perform dependency analysis and mark found dependencies even if there were errors during the first phase of make.
        // Integration of changes should happen only if the corresponding phase of make succeeds
        // In case of errors this wil ensure that all dependencies marked after the first phase
        // will be compiled during the first phase of the next make
        return false;
      }

      if (performIntegrate) {
        context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.updating.dependency.information.0", chunk.getPresentableShortName())));
        globalMappings.integrate(delta);
      }

      return additionalPassRequired;
    }
    catch (BuildDataCorruptedException e) {
      throw e.getCause();
    }
    finally {
      context.processMessage(new ProgressMessage("")); // clean progress messages
    }
  }

  /**
   * @param context        compilation context
   * @param delta          registered delta files in this round
   * @param markDirtyRound compilation round at which dirty files should be visible to builders
   * @return true if additional compilation pass is required, false otherwise
   */
  private static boolean updateDependencyGraph(CompileContext context, Delta delta, ModuleChunk chunk, final CompilationRound markDirtyRound, @Nullable FileFilter skipMarkDirtyFilter) throws IOException {
    final boolean errorsDetected = Utils.errorsDetected(context);
    boolean performIntegrate = !errorsDetected;
    boolean additionalPassRequired = false;
    BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    GraphConfiguration graphConfig = Objects.requireNonNull(dataManager.getDependencyGraph());
    DependencyGraph dependencyGraph = graphConfig.getGraph();
    NodeSourcePathMapper pathMapper = graphConfig.getPathMapper();
    
    final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
    DifferentiateParametersBuilder params = DifferentiateParametersBuilder.create(chunk.getPresentableShortName())
      .compiledWithErrors(errorsDetected)
      .calculateAffected(context.shouldDifferentiate(chunk) && !isForcedRecompilationAllJavaModules(context))
      .processConstantsIncrementally(dataManager.isProcessConstantsIncrementally())
      .withAffectionFilter(s -> moduleBasedFilter.accept(pathMapper.toPath(s).toFile()) && !LibraryDef.isLibraryPath(s))
      .withChunkStructureFilter(s -> moduleBasedFilter.belongsToCurrentTargetChunk(pathMapper.toPath(s).toFile()));
    DifferentiateParameters differentiateParams = params.get();
    DifferentiateResult diffResult = dependencyGraph.differentiate(delta, differentiateParams);

    final boolean compilingIncrementally = isCompileJavaIncrementally(context);

    if (compilingIncrementally && !errorsDetected && differentiateParams.isCalculateAffected() && diffResult.isIncremental()) {
      // some compilers (and compiler plugins) may produce different outputs for the same set of inputs.
      // This might cause corresponding graph Nodes to be considered as always 'changed'. In some scenarios this may lead to endless build loops
      // This fallback logic detects such loops and recompiles the whole module chunk instead.
      Set<NodeSource> affectedForChunk = Iterators.collect(Iterators.filter(diffResult.getAffectedSources(), differentiateParams.belongsToCurrentCompilationChunk()::test), new HashSet<>());
      if (!affectedForChunk.isEmpty() && !getOrCreate(context, ALL_AFFECTED_NODE_SOURCES_KEY, HashSet::new).addAll(affectedForChunk)) {
        // all affected files in this round have already been affected in previous rounds. This might indicate a build cycle => recompiling whole chunk
        LOG.info("Build cycle detected for " + chunk.getName() + "; recompiling whole module chunk");
        // turn on non-incremental mode for all targets from the current chunk => next time the whole chunk is recompiled and affected files won't be calculated anymore
        for (ModuleBuildTarget target : chunk.getTargets()) {
          context.markNonIncremental(target);
        }
        FSOperations.markDirty(context, markDirtyRound, chunk, null);
        return true;
      }
    }

    if (diffResult.isIncremental()) {
      final Set<File> affectedFiles = Iterators.collect(
        Iterators.filter(Iterators.map(diffResult.getAffectedSources(), src -> pathMapper.toPath(src).toFile()), f -> skipMarkDirtyFilter == null || !skipMarkDirtyFilter.accept(f)),
        new HashSet<>()
      );

      if (differentiateParams.isCalculateAffected()) {
        final String infoMessage = JpsBuildBundle.message("progress.message.dependency.analysis.found.0.affected.files", affectedFiles.size());
        LOG.info(infoMessage);
        context.processMessage(new ProgressMessage(infoMessage));
      }

      if (!affectedFiles.isEmpty()) {
        
        if (LOG.isDebugEnabled()) {
          for (File file : affectedFiles) {
            LOG.debug("affected file: " + file.getPath());
          }
          final List<Pair<File, JpsModule>> wrongFiles =
            checkAffectedFilesInCorrectModules(context, affectedFiles, moduleBasedFilter);
          if (!wrongFiles.isEmpty()) {
            LOG.debug("Wrong affected files for module chunk " + chunk.getName() + ": ");
            for (Pair<File, JpsModule> pair : wrongFiles) {
              final String name = pair.second != null ? pair.second.getName() : "null";
              LOG.debug("\t[" + name + "] " + pair.first.getPath());
            }
          }
        }

        Set<ModuleBuildTarget> targetsToMark = null;
        final JavaModuleIndex moduleIndex = getJavaModuleIndex(context);
        for (File file : affectedFiles) {
          if (MODULE_INFO_FILE.equals(file.getName())) {
            final JavaSourceRootDescriptor rootDescr = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
            if (rootDescr != null) {
              final ModuleBuildTarget target = rootDescr.getTarget();
              final File targetModuleInfo = moduleIndex.getModuleInfoFile(target.getModule(), target.isTests());
              if (FileUtil.filesEqual(targetModuleInfo, file)) {
                if (targetsToMark == null) {
                  targetsToMark = new HashSet<>(); // lazy init
                }
                targetsToMark.add(target);
              }
            }
          }
          else {
            FSOperations.markDirtyIfNotDeleted(context, markDirtyRound, file.toPath());
          }
        }

        boolean currentChunkAfected = false;
        if (targetsToMark != null) {
          for (ModuleBuildTarget target : targetsToMark) {
            if (chunk.getTargets().contains(target)) {
              currentChunkAfected = true;
            }
            else {
              FSOperations.markDirty(context, markDirtyRound, target, null);
            }
          }
          if (currentChunkAfected) {
            if (compilingIncrementally) {
              // turn on non-incremental mode for targets from the current chunk, if at least one of them was affected.
              for (ModuleBuildTarget target : chunk.getTargets()) {
                context.markNonIncremental(target);
              }
            }
            FSOperations.markDirty(context, markDirtyRound, chunk, null);
          }
        }
        additionalPassRequired = compilingIncrementally && (currentChunkAfected || moduleBasedFilter.containsFilesFromCurrentTargetChunk(affectedFiles));
      }
    }
    else {
      // non-incremental mode
      final String messageText = JpsBuildBundle.message("progress.message.marking.0.and.direct.dependants.for.recompilation", chunk.getPresentableShortName());
      LOG.info("Non-incremental mode: " + messageText);
      context.processMessage(new ProgressMessage(messageText));

      final boolean alreadyMarkedDirty = FSOperations.isMarkedDirty(context, chunk);
      additionalPassRequired = compilingIncrementally && !alreadyMarkedDirty;

      if (!alreadyMarkedDirty) {
        performIntegrate = false;
      }

      FileFilter toBeMarkedFilter = skipMarkDirtyFilter == null? null : new NegationFileFilter(skipMarkDirtyFilter);
      FSOperations.markDirtyRecursively(context, markDirtyRound, chunk, toBeMarkedFilter);
    }

    if (!additionalPassRequired) {
      ALL_AFFECTED_NODE_SOURCES_KEY.set(context, null); // cleanup
    }

    if (performIntegrate) {
      context.processMessage(new ProgressMessage(JpsBuildBundle.message("progress.message.updating.dependency.information.0", chunk.getPresentableShortName())));
      dependencyGraph.integrate(diffResult);
    }

    return additionalPassRequired;
  }

  public static @Nullable File findModuleInfoFile(CompileContext context, ModuleBuildTarget target) {
    return getJavaModuleIndex(context).getModuleInfoFile(target.getModule(), target.isTests());
  }

  private static JavaModuleIndex getJavaModuleIndex(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    return JpsJavaExtensionService.getInstance().getJavaModuleIndex(project);
  }

  private static FileFilter createOrFilter(final List<? extends FileFilter> filters) {
    if (filters == null || filters.isEmpty()) return null;
    return pathname -> {
      for (FileFilter filter : filters) {
        if (filter.accept(pathname)) {
          return true;
        }
      }
      return false;
    };
  }

  private static void removeFilesAcceptedByFilter(@NotNull Set<File> files, @Nullable FileFilter filter) {
    if (filter != null) {
      for (Iterator<File> it = files.iterator(); it.hasNext();) {
        if (filter.accept(it.next())) {
          it.remove();
        }
      }
    }
  }

  public static boolean isForcedRecompilationAllJavaModules(CompileContext context) {
    return isForcedRecompilationAllJavaModules(context.getScope());
  }

  public static boolean isForcedRecompilationAllJavaModules(CompileScope scope) {
    return scope.isBuildForcedForAllTargets(JavaModuleBuildTargetType.PRODUCTION) &&
           scope.isBuildForcedForAllTargets(JavaModuleBuildTargetType.TEST);
  }

  public static boolean isCompileJavaIncrementally(CompileContext context) {
    CompileScope scope = context.getScope();
    return scope.isBuildIncrementally(JavaModuleBuildTargetType.PRODUCTION) || scope.isBuildIncrementally(JavaModuleBuildTargetType.TEST);
  }

  private static @NotNull @Unmodifiable List<Pair<File, JpsModule>> checkAffectedFilesInCorrectModules(
    CompileContext context,
    Collection<File> affected,
    ModulesBasedFileFilter moduleBasedFilter
  ) {
    if (affected.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Pair<File, JpsModule>> result = new ArrayList<>();
    final BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    for (File file : affected) {
      if (!moduleBasedFilter.accept(file)) {
        final JavaSourceRootDescriptor moduleAndRoot = rootIndex.findJavaRootDescriptor(context, file);
        result.add(new Pair<>(file, moduleAndRoot != null ? moduleAndRoot.target.getModule() : null));
      }
    }
    return result;
  }

  private static @NotNull Set<File> getFilesContainer(CompileContext context, final Key<Set<File>> dataKey) {
    return getOrCreate(context, dataKey, FileCollectionFactory::createCanonicalFileSet);
  }

  private static @NotNull <T> T getOrCreate(CompileContext context, Key<T> dataKey, Supplier<T> factory) {
    T value = dataKey.get(context, null);
    if (value == null) {
      dataKey.set(context, value = factory.get());
    }
    return value;
  }

  private static @NotNull Set<String> getRemovedPaths(ModuleChunk chunk,
                                                      DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) {
    if (!dirtyFilesHolder.hasRemovedFiles()) {
      return Set.of();
    }

    Set<String> removed = CollectionFactory.createFilePathSet();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      for (Path file : dirtyFilesHolder.getRemoved(target)) {
        removed.add(file.toString());
      }
    }
    return removed;
  }

  private static boolean hasRemovedPaths(ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) {
    if (dirtyFilesHolder.hasRemovedFiles()) {
      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (!dirtyFilesHolder.getRemoved(target).isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  public static void cleanupChunkResources(CompileContext context) {
    ALL_AFFECTED_FILES_KEY.set(context, null);
    ALL_COMPILED_FILES_KEY.set(context, null);
  }

  public static @NotNull JpsSdk<JpsDummyElement> ensureModuleHasJdk(JpsModule module, CompileContext context, final @Nls String compilerName) throws ProjectBuildException {
    JpsSdkReference<JpsDummyElement> reference = module.getSdkReference(JpsJavaSdkType.INSTANCE);
    if (reference == null) {
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                 JpsBuildBundle.message("build.message.jdk.isn.t.specified.for.module.0", module.getName())));
      throw new StopBuildException();
    }

    JpsTypedLibrary<JpsSdk<JpsDummyElement>> sdkLibrary = reference.resolve();
    if (sdkLibrary == null) {
      JpsLibrary library = context.getProjectDescriptor().getModel().getGlobal().getLibraryCollection().findLibrary(reference.getSdkName());
      JpsSdkType sdkType = library != null ? ObjectUtils.tryCast(library.getType(), JpsSdkType.class) : null;
      String errorMessage;
      if (sdkType == null) {
        errorMessage = JpsBuildBundle.message("build.message.cannot.find.jdk.0.for.module.1", reference.getSdkName(), module.getName());
      }
      else {
        errorMessage = JpsBuildBundle.message("build.message.cannot.find.jdk.for.module.0.1.points.to.2", module.getName(), reference.getSdkName(),
                                              sdkType.getPresentableName());
      }
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR, errorMessage));
      throw new StopBuildException();
    }
    return sdkLibrary.getProperties();
  }

  public static @Nullable JavaCompilingTool findCompilingTool(@NotNull String compilerId) {
    for (JavaCompilingTool tool : JpsServiceManager.getInstance().getExtensions(JavaCompilingTool.class)) {
      if (compilerId.equals(tool.getId()) || compilerId.equals(tool.getAlternativeId())) {
        return tool;
      }
    }
    return null;
  }

  private static final class ModulesBasedFileFilter implements Mappings.DependentFilesFilter {
    private static final Key<Map<BuildTarget<?>, Set<BuildTarget<?>>>> TARGETS_CACHE_KEY = Key.create("__recursive_target-deps_cache__");
    private final CompileContext myContext;
    private final Set<? extends BuildTarget<?>> myChunkTargets;
    private final Map<BuildTarget<?>, Set<BuildTarget<?>>> myCache;
    private final BuildRootIndex myBuildRootIndex;
    private final BuildTargetIndex myBuildTargetIndex;

    private ModulesBasedFileFilter(CompileContext context, ModuleChunk chunk) {
      myContext = context;
      myChunkTargets = chunk.getTargets();
      myBuildRootIndex = context.getProjectDescriptor().getBuildRootIndex();
      myBuildTargetIndex = context.getProjectDescriptor().getBuildTargetIndex();
      Map<BuildTarget<?>, Set<BuildTarget<?>>> cache = TARGETS_CACHE_KEY.get(context);
      if (cache == null) {
        TARGETS_CACHE_KEY.set(context, cache = new HashMap<>());
      }
      myCache = cache;
    }

    @Override
    public boolean accept(File file) {
      final JavaSourceRootDescriptor rd = myBuildRootIndex.findJavaRootDescriptor(myContext, file);
      if (rd == null) {
        return true;
      }
      final BuildTarget<?> targetOfFile = rd.target;
      if (myChunkTargets.contains(targetOfFile)) {
        return true;
      }
      Set<BuildTarget<?>> targetOfFileWithDependencies = myCache.computeIfAbsent(
        targetOfFile,
        trg -> Iterators.collect(Iterators.recurseDepth(trg, new Iterators.Function<BuildTarget<?>, Iterable<? extends BuildTarget<?>>>() {
          @Override
          public Iterable<? extends BuildTarget<?>> fun(BuildTarget<?> t) {
            return myBuildTargetIndex.getDependencies(t, myContext);
          }
        }, false), new HashSet<>())
      );
      return ContainerUtil.intersects(targetOfFileWithDependencies, myChunkTargets);
    }

    @Override
    public boolean belongsToCurrentTargetChunk(File file) {
      final JavaSourceRootDescriptor rd = myBuildRootIndex.findJavaRootDescriptor(myContext, file);
      return rd != null && myChunkTargets.contains(rd.target);
    }

    public boolean containsFilesFromCurrentTargetChunk(Collection<? extends File> files) {
      for (File file : files) {
        if (belongsToCurrentTargetChunk(file)) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class NegationFileFilter implements FileFilter {
    private final FileFilter myFilter;

    NegationFileFilter(FileFilter filter) {
      myFilter = filter;
    }

    @Override
    public boolean accept(File pathname) {
      return !myFilter.accept(pathname);
    }
  }

}