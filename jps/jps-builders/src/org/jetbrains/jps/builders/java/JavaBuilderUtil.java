package org.jetbrains.jps.builders.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class JavaBuilderUtil {
  private static final Key<Set<File>> ALL_AFFECTED_FILES_KEY = Key.create("_all_affected_files_");
  private static final Key<Set<File>> ALL_COMPILED_FILES_KEY = Key.create("_all_compiled_files_");
  public static final Key<Callbacks.ConstantAffectionResolver> CONSTANT_SEARCH_SERVICE = Key.create("_constant_search_service_");
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.Builder");

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
    if (Utils.errorsDetected(context)) {
      return false;
    }
    try {
      boolean additionalPassRequired = false;

      final Set<String> removedPaths = getRemovedPaths(chunk, dirtyFilesHolder);

      final Mappings globalMappings = context.getProjectDescriptor().dataManager.getMappings();

      if (!context.isProjectRebuild()) {
        if (context.shouldDifferentiate(chunk)) {
          context.processMessage(new ProgressMessage("Checking dependencies"));
          final Set<File> allCompiledFiles = getAllCompiledFilesContainer(context);
          final Set<File> allAffectedFiles = getAllAffectedFilesContainer(context);

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
            newlyAffectedFiles.removeAll(allCompiledFiles); // the diff operation may have affected the class already compiled in thic compilation round

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
                FSOperations.markDirtyIfNotDeleted(context, file);
              }
              additionalPassRequired = context.isMake() && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
            }
          }
          else {
            final String messageText = "Marking " + chunk.getName() + " and direct dependants for recompilation";
            LOG.info("Non-incremental mode: " + messageText);
            context.processMessage(new ProgressMessage(messageText));

            additionalPassRequired = context.isMake();
            FSOperations.markDirtyRecursively(context, chunk);
          }
        }
        else {
          globalMappings.differentiateOnNonIncrementalMake(delta, removedPaths, filesToCompile);
        }
      }
      else {
        globalMappings.differentiateOnRebuild(delta);
      }

      context.processMessage(new ProgressMessage("Updating dependency information"));

      globalMappings.integrate(delta);

      return additionalPassRequired;
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw ((IOException)cause);
      }
      throw e;
    }
    finally {
      context.processMessage(new ProgressMessage("")); // clean progress messages
    }
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

  private static Set<File> getAllAffectedFilesContainer(CompileContext context) {
    Set<File> allAffectedFiles = ALL_AFFECTED_FILES_KEY.get(context);
    if (allAffectedFiles == null) {
      allAffectedFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      ALL_AFFECTED_FILES_KEY.set(context, allAffectedFiles);
    }
    return allAffectedFiles;
  }

  private static Set<File> getAllCompiledFilesContainer(CompileContext context) {
    Set<File> allCompiledFiles = ALL_COMPILED_FILES_KEY.get(context);
    if (allCompiledFiles == null) {
      allCompiledFiles = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
      ALL_COMPILED_FILES_KEY.set(context, allCompiledFiles);
    }
    return allCompiledFiles;
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

  private static class ModulesBasedFileFilter implements Mappings.DependentFilesFilter {
    private final CompileContext myContext;
    private final Set<JpsModule> myChunkModules;
    private final Map<JpsModule, Set<JpsModule>> myCache = new HashMap<JpsModule, Set<JpsModule>>();

    private ModulesBasedFileFilter(CompileContext context, ModuleChunk chunk) {
      myContext = context;
      myChunkModules = chunk.getModules();
    }

    @Override
    public boolean accept(File file) {
      final JavaSourceRootDescriptor rd = myContext.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(myContext, file);
      if (rd == null) {
        return true;
      }
      final JpsModule moduleOfFile = rd.target.getModule();
      if (myChunkModules.contains(moduleOfFile)) {
        return true;
      }
      Set<JpsModule> moduleOfFileWithDependencies = myCache.get(moduleOfFile);
      if (moduleOfFileWithDependencies == null) {
        moduleOfFileWithDependencies = ProjectPaths.getModulesWithDependentsRecursively(moduleOfFile, true);
        myCache.put(moduleOfFile, moduleOfFileWithDependencies);
      }
      return Utils.intersects(moduleOfFileWithDependencies, myChunkModules);
    }
  }
}
