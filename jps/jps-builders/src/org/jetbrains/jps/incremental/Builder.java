package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class Builder {
  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");

  public static enum ExitCode {
    OK, ABORT, ADDITIONAL_PASS_REQUIRED
  }

  public abstract String getName();

  public abstract ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException;

  public abstract String getDescription();

  public void cleanupResources(CompileContext context, ModuleChunk chunk) {
    ALL_AFFECTED_FILES_KEY.set(context, null);
    ALL_COMPILED_FILES_KEY.set(context, null);
  }

  /**
   * @param context
   * @param delta
   * @param chunk
   * @param filesToCompile files compiled in this round
   * @param successfullyCompiled
   * @return true if additional compilation pass is required, false otherwise
   * @throws Exception
   */
  public final boolean updateMappings(CompileContext context, final Mappings delta, ModuleChunk chunk, Collection<File> filesToCompile, Collection<File> successfullyCompiled) throws Exception {
    boolean additionalPassRequired = false;

    final Set<String> removedPaths = getRemovedPaths(context);

    final Mappings globalMappings = context.getDataManager().getMappings();

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (globalMappings) {
      if (!context.isProjectRebuild()) {
        final Set<File> allCompiledFiles = getAllCompiledFilesContainer(context);
        final Set<File> allAffectedFiles = getAllAffectedFilesContainer(context);

        // mark as affected all files that were dirty before compilation
        allAffectedFiles.addAll(filesToCompile);
        // accumulate all successfully compiled in this round
        allCompiledFiles.addAll(successfullyCompiled);
        // unmark as affected all successfully compiled
        allAffectedFiles.removeAll(successfullyCompiled);

        final HashSet<File> affectedBeforeDif = new HashSet<File>(allAffectedFiles);

        final boolean incremental = globalMappings.differentiate(
          delta, removedPaths, successfullyCompiled, allCompiledFiles, allAffectedFiles
        );

        if (incremental) {
          final Set<File> newlyAffectedFiles = new HashSet<File>(allAffectedFiles);
          newlyAffectedFiles.removeAll(affectedBeforeDif);
          // todo: temp code
          for (String removedPath : removedPaths) {
            newlyAffectedFiles.remove(new File(removedPath));
          }
          if (!newlyAffectedFiles.isEmpty()) {
            for (File file : newlyAffectedFiles) {
              context.markDirty(file);
            }
            additionalPassRequired = context.isMake() && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
          }
        }
        else {
          additionalPassRequired = context.isMake();
          context.markDirty(chunk);
        }
      }

      globalMappings.integrate(delta, successfullyCompiled, removedPaths);
    }

    return additionalPassRequired;
  }

  // delete all class files that according to mappings correspond to given sources
  public static void deleteCorrespondingOutputFiles(CompileContext context, Map<File, Module> sources) throws Exception {
    if (!context.isProjectRebuild() && !sources.isEmpty()) {
      for (Map.Entry<File, Module> pair : sources.entrySet()) {
        final File file = pair.getKey();
        final String srcPath = file.getPath();
        final String moduleName = pair.getValue().getName().toLowerCase(Locale.US);
        final SourceToOutputMapping srcToOut = context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests());
        final Collection<String> outputs = srcToOut.getState(srcPath);
        if (outputs != null) {
          for (String output : outputs) {
            FileUtil.delete(new File(output));
          }
          srcToOut.remove(srcPath);
        }
      }
    }
  }

  private static boolean chunkContainsAffectedFiles(CompileContext context, ModuleChunk chunk, final Set<File> affected) throws Exception {
    final Set<Module> chunkModules = new HashSet<Module>(chunk.getModules());
    if (!chunkModules.isEmpty()) {
      for (File file : affected) {
        final RootDescriptor moduleAndRoot = context.getModuleAndRoot(file);
        if (moduleAndRoot != null && chunkModules.contains(moduleAndRoot.module)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<File> getAllAffectedFilesContainer(CompileContext context) {
    Set<File> allAffectedFiles = ALL_AFFECTED_FILES_KEY.get(context);
    if (allAffectedFiles == null) {
      allAffectedFiles = new HashSet<File>();
      ALL_AFFECTED_FILES_KEY.set(context, allAffectedFiles);
    }
    return allAffectedFiles;
  }

  private static Set<File> getAllCompiledFilesContainer(CompileContext context) {
    Set<File> allCompiledFiles = ALL_COMPILED_FILES_KEY.get(context);
    if (allCompiledFiles == null) {
      allCompiledFiles = new HashSet<File>();
      ALL_COMPILED_FILES_KEY.set(context, allCompiledFiles);
    }
    return allCompiledFiles;
  }

  private static Set<String> getRemovedPaths(CompileContext context) {
    final Set<String> removed = Paths.CHUNK_REMOVED_SOURCES_KEY.get(context);
    return removed != null? removed : Collections.<String>emptySet();
  }

}
