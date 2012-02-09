package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Use {@link BuilderService} to register implementations of this class
 *
 * @author Eugene Zhuravlev
 *         Date: 9/17/11
 */
public abstract class ModuleLevelBuilder extends Builder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.Builder");

  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");

  private final BuilderCategory myCategory;

  protected ModuleLevelBuilder(BuilderCategory category) {
    myCategory = category;
  }

  public static enum ExitCode {
    OK, ABORT, ADDITIONAL_PASS_REQUIRED, CHUNK_REBUILD_REQUIRED
  }

  public abstract ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException;

  public final BuilderCategory getCategory() {
    return myCategory;
  }

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
  public final boolean updateMappings(CompileContext context, final Mappings delta, ModuleChunk chunk, Collection<File> filesToCompile, Collection<File> successfullyCompiled) throws IOException {
    try {
      boolean additionalPassRequired = false;

      final Set<String> removedPaths = getRemovedPaths(context);

      final Mappings globalMappings = context.getDataManager().getMappings();

      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (globalMappings) {
        if (!context.isProjectRebuild() && context.shouldDifferentiate(chunk, context.isCompilingTests())) {
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
            delta, removedPaths, filesToCompile, allCompiledFiles, allAffectedFiles
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
            newlyAffectedFiles.removeAll(allCompiledFiles); // the diff operation may have affected the class already compiled in thic compilation round

            if (!newlyAffectedFiles.isEmpty()) {
              for (File file : newlyAffectedFiles) {
                context.markDirty(file);
              }
              additionalPassRequired = context.isMake() && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
            }
          }
          else {
            additionalPassRequired = context.isMake();
            context.markDirtyRecursively(chunk);
          }
        }

        globalMappings.integrate(delta, successfullyCompiled, removedPaths);
      }

      return additionalPassRequired;
    }
    catch(RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw ((IOException)cause);
      }
      throw e;
    }
  }

  private static boolean chunkContainsAffectedFiles(CompileContext context, ModuleChunk chunk, final Set<File> affected) throws IOException {
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
