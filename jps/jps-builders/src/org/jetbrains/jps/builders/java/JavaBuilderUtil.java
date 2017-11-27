// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class JavaBuilderUtil {
  public static final Key<Callbacks.ConstantAffectionResolver> CONSTANT_SEARCH_SERVICE = Key.create("_constant_search_service_");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.Builder");
  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");
  private static final Key<Set<File>> FILES_TO_COMPILE_KEY = Key.create("_files_to_compile_");
  private static final Key<Set<File>> COMPILED_WITH_ERRORS_KEY = Key.create("_compiled_with_errors_");
  private static final Key<Set<File>> SUCCESSFULLY_COMPILED_FILES_KEY = Key.create("_successfully_compiled_files_");
  private static final Key<List<FileFilter>> SKIP_MARKING_DIRTY_FILTERS_KEY = Key.create("_skip_marking_dirty_filters_");
  private static final Key<Pair<Mappings, Callbacks.Backend>> MAPPINGS_DELTA_KEY = Key.create("_mappings_delta_");
  private static final String MODULE_INFO_FILE = "module-info.java";

  public static void registerFileToCompile(CompileContext context, File file) {
    registerFilesToCompile(context, Collections.singleton(file));
  }

  public static void registerFilesToCompile(CompileContext context, Collection<File> files) {
    getFilesContainer(context, FILES_TO_COMPILE_KEY).addAll(files);
  }

  public static void registerFilesWithErrors(CompileContext context, Collection<File> files) {
    getFilesContainer(context, COMPILED_WITH_ERRORS_KEY).addAll(files);
  }

  public static void registerSuccessfullyCompiled(CompileContext context, File file) {
    registerSuccessfullyCompiled(context, Collections.singleton(file));
  }

  public static void registerSuccessfullyCompiled(CompileContext context, Collection<File> files) {
    getFilesContainer(context, SUCCESSFULLY_COMPILED_FILES_KEY).addAll(files);
  }

  /**
   * The files accepted by {@code filter} won't be marked dirty by {@link #updateMappings} method when this compilation round finishes.
   * Use this method to register a filter accepting files of your language if you compute and mark as dirty affected files yourself in your builder.
   */
  public static void registerFilterToSkipMarkingAffectedFileDirty(@NotNull CompileContext context, @NotNull FileFilter filter) {
    List<FileFilter> filters = SKIP_MARKING_DIRTY_FILTERS_KEY.get(context);
    if (filters == null) {
      SKIP_MARKING_DIRTY_FILTERS_KEY.set(context, filters = new ArrayList<>());
    }
    filters.add(filter);
  }

  @NotNull
  public static Callbacks.Backend getDependenciesRegistrar(CompileContext context) {
    Pair<Mappings, Callbacks.Backend> pair = MAPPINGS_DELTA_KEY.get(context);
    if (pair == null) {
      final Mappings delta = context.getProjectDescriptor().dataManager.getMappings().createDelta();
      pair = Pair.create(delta, delta.getCallback());
      MAPPINGS_DELTA_KEY.set(context, pair);
    }
    return pair.second;
  }

  public static boolean updateMappingsOnRoundCompletion(
    CompileContext context, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, ModuleChunk chunk) throws IOException {

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
    SKIP_MARKING_DIRTY_FILTERS_KEY.set(context, null);
    return updateMappings(context, delta, dirtyFilesHolder, chunk, compiledFiles, successfullyCompiled, CompilationRound.NEXT, filter);
  }

  /**
   * @deprecated this method isn't supposed to be called by plugins anymore, the mappings are updated
   * by the build process infrastructure automatically. Use {@link #getDependenciesRegistrar(CompileContext)},
   * {@link #registerFilesToCompile(CompileContext, Collection)}, or
   * {@link #registerSuccessfullyCompiled(CompileContext, Collection)} instead.
   */
  public static boolean updateMappings(CompileContext context,
                                       final Mappings delta,
                                       DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                       ModuleChunk chunk,
                                       Collection<File> filesToCompile,
                                       Collection<File> successfullyCompiled) throws IOException {
    return updateMappings(context, delta, dirtyFilesHolder, chunk, filesToCompile, successfullyCompiled, CompilationRound.NEXT, null);
  }

  public static void markDirtyDependenciesForInitialRound(CompileContext context, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dfh, ModuleChunk chunk) throws IOException {
    if (hasRemovedPaths(chunk, dfh)) {
      final Mappings delta = context.getProjectDescriptor().dataManager.getMappings().createDelta();
      final Set<File> empty = Collections.emptySet();
      updateMappings(context, delta, dfh, chunk, empty, empty, CompilationRound.CURRENT, null);
    }
  }

  /**
   * @param filesToCompile   files compiled in this round
   * @param markDirtyRound   compilation round at which dirty files should be visible to builders
   * @return true if additional compilation pass is required, false otherwise
   * @throws Exception
   */
  private static boolean updateMappings(CompileContext context,
                                        final Mappings delta,
                                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                        ModuleChunk chunk,
                                        Collection<File> filesToCompile,
                                        Collection<File> successfullyCompiled,
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
          context.processMessage(new ProgressMessage("Checking dependencies... [" + chunk.getPresentableShortName() + "]"));
          final Set<File> allCompiledFiles = getFilesContainer(context, ALL_COMPILED_FILES_KEY);
          final Set<File> allAffectedFiles = getFilesContainer(context, ALL_AFFECTED_FILES_KEY);

          // mark as affected all files that were dirty before compilation
          allAffectedFiles.addAll(filesToCompile);
          // accumulate all successfully compiled in this round
          allCompiledFiles.addAll(successfullyCompiled);
          // unmark as affected all successfully compiled
          allAffectedFiles.removeAll(successfullyCompiled);

          final Set<File> affectedBeforeDif = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
          affectedBeforeDif.addAll(allAffectedFiles);

          final Set<File> compiledWithErrors = getFilesContainer(context, COMPILED_WITH_ERRORS_KEY);
          COMPILED_WITH_ERRORS_KEY.set(context, null);

          final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
          final boolean incremental = globalMappings.differentiateOnIncrementalMake(
            delta, removedPaths, filesToCompile, compiledWithErrors, allCompiledFiles, allAffectedFiles, moduleBasedFilter,
            CONSTANT_SEARCH_SERVICE.get(context)
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

            final String infoMessage = "Dependency analysis found " + newlyAffectedFiles.size() + " affected files";
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
                        targetsToMark = new THashSet<>(); // lazy init
                      }
                      targetsToMark.add(target);
                    }
                  }
                }
                else {
                  FSOperations.markDirtyIfNotDeleted(context, markDirtyRound, file);
                }
              }
              if (targetsToMark != null) {
                boolean currentChunkAfected = false;
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
              additionalPassRequired = compilingIncrementally && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
            }
          }
          else {
            // non-incremental mode
            final String messageText = "Marking " + chunk.getPresentableShortName() + " and direct dependants for recompilation";
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
        context.processMessage(new ProgressMessage("Updating dependency information... [" + chunk.getPresentableShortName() + "]"));
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

  @Nullable
  public static File findModuleInfoFile(CompileContext context, ModuleBuildTarget target) {
    return getJavaModuleIndex(context).getModuleInfoFile(target.getModule(), target.isTests());
  }

  private static JavaModuleIndex getJavaModuleIndex(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    return JpsJavaExtensionService.getInstance().getJavaModuleIndex(project);
  }

  private static FileFilter createOrFilter(final List<FileFilter> filters) {
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
    if (filter == null) return;

    Iterator<File> iterator = files.iterator();
    while (iterator.hasNext()) {
      File next = iterator.next();
      if (filter.accept(next)) {
        iterator.remove();
      }
    }
  }

  public static boolean isForcedRecompilationAllJavaModules(CompileContext context) {
    CompileScope scope = context.getScope();
    return scope.isBuildForcedForAllTargets(JavaModuleBuildTargetType.PRODUCTION) &&
           scope.isBuildForcedForAllTargets(JavaModuleBuildTargetType.TEST);
  }

  public static boolean isCompileJavaIncrementally(CompileContext context) {
    CompileScope scope = context.getScope();
    return scope.isBuildIncrementally(JavaModuleBuildTargetType.PRODUCTION) || scope.isBuildIncrementally(JavaModuleBuildTargetType.TEST);
  }

  private static List<Pair<File, JpsModule>> checkAffectedFilesInCorrectModules(CompileContext context,
                                                                             Collection<File> affected,
                                                                             ModulesBasedFileFilter moduleBasedFilter) {
    if (affected.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Pair<File, JpsModule>> result = new ArrayList<>();
    for (File file : affected) {
      if (!moduleBasedFilter.accept(file)) {
        final JavaSourceRootDescriptor moduleAndRoot = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context,
                                                                                                                                 file);
        result.add(Pair.create(file, moduleAndRoot != null ? moduleAndRoot.target.getModule() : null));
      }
    }
    return result;
  }

  private static boolean chunkContainsAffectedFiles(CompileContext context, ModuleChunk chunk, final Set<File> affected)
    throws IOException {
    final Set<JpsModule> chunkModules = chunk.getModules();
    if (!chunkModules.isEmpty()) {
      for (File file : affected) {
        final JavaSourceRootDescriptor moduleAndRoot = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context,
                                                                                                                                 file);
        if (moduleAndRoot != null && chunkModules.contains(moduleAndRoot.target.getModule())) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  private static Set<File> getFilesContainer(CompileContext context, final Key<Set<File>> dataKey) {
    Set<File> files = dataKey.get(context);
    if (files == null) {
      files = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      dataKey.set(context, files);
    }
    return files;
  }

  private static Set<String> getRemovedPaths(ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) {
    if (!dirtyFilesHolder.hasRemovedFiles()) {
      return Collections.emptySet();
    }
    final Set<String> removed = new THashSet<>(FileUtil.PATH_HASHING_STRATEGY);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      removed.addAll(dirtyFilesHolder.getRemovedFiles(target));
    }
    return removed;
  }

  private static boolean hasRemovedPaths(ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) {
    if (dirtyFilesHolder.hasRemovedFiles()) {
      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (!dirtyFilesHolder.getRemovedFiles(target).isEmpty()) {
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

  @NotNull
  public static JpsSdk<JpsDummyElement> ensureModuleHasJdk(JpsModule module, CompileContext context, final String compilerName) throws
                                                                                                                                ProjectBuildException {
    JpsSdkReference<JpsDummyElement> reference = module.getSdkReference(JpsJavaSdkType.INSTANCE);
    if (reference == null) {
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR, "JDK isn't specified for module '" + module.getName() + "'"));
      throw new StopBuildException();
    }

    JpsTypedLibrary<JpsSdk<JpsDummyElement>> sdkLibrary = reference.resolve();
    if (sdkLibrary == null) {
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                 "Cannot find JDK '" + reference.getSdkName() + "' for module '" + module.getName() + "'"));
      throw new StopBuildException();
    }
    return sdkLibrary.getProperties();
  }

  @Nullable
  public static JavaCompilingTool findCompilingTool(@NotNull String compilerId) {
    for (JavaCompilingTool tool : JpsServiceManager.getInstance().getExtensions(JavaCompilingTool.class)) {
      if (compilerId.equals(tool.getId()) || compilerId.equals(tool.getAlternativeId())) {
        return tool;
      }
    }
    return null;
  }

  private static class ModulesBasedFileFilter implements Mappings.DependentFilesFilter {
    private final CompileContext myContext;
    private final Set<? extends BuildTarget<?>> myChunkTargets;
    private final Map<BuildTarget<?>, Set<BuildTarget<?>>> myCache = new HashMap<>();
    private final BuildRootIndex myBuildRootIndex;
    private final BuildTargetIndex myBuildTargetIndex;

    private ModulesBasedFileFilter(CompileContext context, ModuleChunk chunk) {
      myContext = context;
      myChunkTargets = chunk.getTargets();
      myBuildRootIndex = context.getProjectDescriptor().getBuildRootIndex();
      myBuildTargetIndex = context.getProjectDescriptor().getBuildTargetIndex();
    }

    @Override
    public boolean accept(File file) {
      final JavaSourceRootDescriptor rd = myBuildRootIndex.findJavaRootDescriptor(myContext, file);
      if (rd == null) {
        return true;
      }
      final ModuleBuildTarget targetOfFile = rd.target;
      if (myChunkTargets.contains(targetOfFile)) {
        return true;
      }
      Set<BuildTarget<?>> targetOfFileWithDependencies = myCache.get(targetOfFile);
      if (targetOfFileWithDependencies == null) {
        targetOfFileWithDependencies = myBuildTargetIndex.getDependenciesRecursively(targetOfFile, myContext);
        myCache.put(targetOfFile, targetOfFileWithDependencies);
      }
      return ContainerUtil.intersects(targetOfFileWithDependencies, myChunkTargets);
    }

    @Override
    public boolean belongsToCurrentTargetChunk(File file) {
      final JavaSourceRootDescriptor rd = myBuildRootIndex.findJavaRootDescriptor(myContext, file);
      return rd != null && myChunkTargets.contains(rd.target);
    }
  }

  private static class NegationFileFilter implements FileFilter {
    private final FileFilter myFilter;

    public NegationFileFilter(FileFilter filter) {
      myFilter = filter;
    }

    @Override
    public boolean accept(File pathname) {
      return !myFilter.accept(pathname);
    }
  }
}