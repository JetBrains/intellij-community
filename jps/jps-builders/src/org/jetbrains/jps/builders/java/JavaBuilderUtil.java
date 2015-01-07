/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class JavaBuilderUtil {
  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");
  private static final Key<Set<File>> FILES_TO_COMPILE_KEY = Key.create("_files_to_compile_");
  private static final Key<Set<File>> SUCCESSFULLY_COMPILED_FILES_KEY = Key.create("_successfully_compiled_files_");
  private static final Key<Pair<Mappings, Callbacks.Backend>> MAPPINGS_DELTA_KEY = Key.create("_mappings_delta_");
  public static final Key<Callbacks.ConstantAffectionResolver> CONSTANT_SEARCH_SERVICE = Key.create("_constant_search_service_");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.Builder");

  public static void registerFileToCompile(CompileContext context, File file) {
    registerFilesToCompile(context, Collections.singleton(file));
  }
  
  public static void registerFilesToCompile(CompileContext context, Collection<File> files) {
    getFilesContainer(context, FILES_TO_COMPILE_KEY).addAll(files);
  }

  public static void registerSuccessfullyCompiled(CompileContext context, File file) {
    registerSuccessfullyCompiled(context, Collections.singleton(file));
  }
  
  public static void registerSuccessfullyCompiled(CompileContext context, Collection<File> files) {
    getFilesContainer(context, SUCCESSFULLY_COMPILED_FILES_KEY).addAll(files);
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
    return updateMappings(context, delta, dirtyFilesHolder, chunk, compiledFiles, successfullyCompiled);
  }

  /**
   *
   * @param context
   * @param delta
   * @param dirtyFilesHolder
   * @param chunk
   * @param filesToCompile       files compiled in this round
   * @param successfullyCompiled
   * @return true if additional compilation pass is required, false otherwise
   * @throws Exception
   */
  public static boolean updateMappings(CompileContext context,
                                       final Mappings delta,
                                       DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                       ModuleChunk chunk,
                                       Collection<File> filesToCompile,
                                       Collection<File> successfullyCompiled) throws IOException {
    try {
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

          final Set<File> affectedBeforeDif = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
          affectedBeforeDif.addAll(allAffectedFiles);

          final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
          final boolean incremental = globalMappings.differentiateOnIncrementalMake(
            delta, removedPaths, filesToCompile, allCompiledFiles, allAffectedFiles, moduleBasedFilter,
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

          if (incremental) {
            final Set<File> newlyAffectedFiles = new HashSet<File>(allAffectedFiles);
            newlyAffectedFiles.removeAll(affectedBeforeDif);

            final String infoMessage = "Dependency analysis found " + newlyAffectedFiles.size() + " affected files";
            LOG.info(infoMessage);
            context.processMessage(new ProgressMessage(infoMessage));

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

              for (File file : newlyAffectedFiles) {
                FSOperations.markDirtyIfNotDeleted(context, CompilationRound.NEXT, file);
              }
              additionalPassRequired = isCompileJavaIncrementally(context) && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
            }
          }
          else {
            final String messageText = "Marking " + chunk.getPresentableShortName() + " and direct dependants for recompilation";
            LOG.info("Non-incremental mode: " + messageText);
            context.processMessage(new ProgressMessage(messageText));

            additionalPassRequired = isCompileJavaIncrementally(context);
            FSOperations.markDirtyRecursively(context, CompilationRound.NEXT, chunk);
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

      context.processMessage(new ProgressMessage("Updating dependency information... [" + chunk.getPresentableShortName() + "]"));

      globalMappings.integrate(delta);

      return additionalPassRequired;
    }
    catch (BuildDataCorruptedException e) {
      throw e.getCause();
    }
    finally {
      context.processMessage(new ProgressMessage("")); // clean progress messages
    }
  }

  public static boolean isForcedRecompilationAllJavaModules(CompileContext context) {
    CompileScope scope = context.getScope();
    return scope.isBuildForcedForAllTargets(JavaModuleBuildTargetType.PRODUCTION) && scope.isBuildForcedForAllTargets(
      JavaModuleBuildTargetType.TEST);
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
    final List<Pair<File, JpsModule>> result = new ArrayList<Pair<File, JpsModule>>();
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
      files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      dataKey.set(context, files);
    }
    return files;
  }

  private static Set<String> getRemovedPaths(ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) {
    if (!dirtyFilesHolder.hasRemovedFiles()) {
      return Collections.emptySet();
    }
    final Set<String> removed = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      removed.addAll(dirtyFilesHolder.getRemovedFiles(target));
    }
    return removed;
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
    private final Map<BuildTarget<?>, Set<BuildTarget<?>>> myCache = new HashMap<BuildTarget<?>, Set<BuildTarget<?>>>();
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
}
