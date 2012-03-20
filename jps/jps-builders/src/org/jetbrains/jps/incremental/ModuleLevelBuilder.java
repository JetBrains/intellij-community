package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

      if (!context.isProjectRebuild() && context.shouldDifferentiate(chunk, context.isCompilingTests())) {
        context.processMessage(new ProgressMessage("Checking dependencies"));
        final Set<File> allCompiledFiles = getAllCompiledFilesContainer(context);
        final Set<File> allAffectedFiles = getAllAffectedFilesContainer(context);

        // mark as affected all files that were dirty before compilation
        allAffectedFiles.addAll(filesToCompile);
        // accumulate all successfully compiled in this round
        allCompiledFiles.addAll(successfullyCompiled);
        // unmark as affected all successfully compiled
        allAffectedFiles.removeAll(successfullyCompiled);

        final HashSet<File> affectedBeforeDif = new HashSet<File>(allAffectedFiles);

        final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
        final boolean incremental = globalMappings.differentiate(
          delta, removedPaths, filesToCompile, allCompiledFiles, allAffectedFiles, moduleBasedFilter
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

          context.processMessage(new ProgressMessage("Found " + newlyAffectedFiles.size() + " affected files"));

          if (!newlyAffectedFiles.isEmpty()) {

            if (LOG.isDebugEnabled()) {
              final List<Pair<File, Module>> wrongFiles = checkAffectedFilesInCorrectModules(context, newlyAffectedFiles, moduleBasedFilter);
              if (!wrongFiles.isEmpty()) {
                LOG.debug("Wrong affected files for module chunk " + chunk.getName() + ": ");
                for (Pair<File, Module> pair : wrongFiles) {
                  final String name = pair.second != null? pair.second.getName() : "null";
                  LOG.debug("\t[" + name + "] " + pair.first.getPath());
                }
              }
            }

            for (File file : newlyAffectedFiles) {
              context.markDirtyIfNotDeleted(file);
            }
            additionalPassRequired = context.isMake() && chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
          }
        }
        else {
          context.processMessage(new ProgressMessage("Marking " + chunk.getName() + " and dependants for recompilation"));

          additionalPassRequired = context.isMake();
          context.markDirtyRecursively(chunk);
        }
      }

      context.processMessage(new ProgressMessage("Updating dependency information"));

      globalMappings.integrate(delta, successfullyCompiled, removedPaths);

      return additionalPassRequired;
    }
    catch(RuntimeException e) {
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

  private static List<Pair<File, Module>> checkAffectedFilesInCorrectModules(CompileContext context, Collection<File> affected, ModulesBasedFileFilter moduleBasedFilter) {
    if (affected.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Pair<File, Module>> result = new ArrayList<Pair<File, Module>>();
    for (File file : affected) {
      if (!moduleBasedFilter.accept(file)) {
        final RootDescriptor moduleAndRoot = context.getModuleAndRoot(file);
        result.add(Pair.create(file, moduleAndRoot != null? moduleAndRoot.module : null));
      }
    }
    return result;
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

  private static class ModulesBasedFileFilter implements Mappings.DependentFilesFilter{
    private final CompileContext myContext;
    private final Set<Module> myChunkModules;
    private final Map<Module, Set<Module>> myCache = new HashMap<Module, Set<Module>>();

    private ModulesBasedFileFilter(CompileContext context, ModuleChunk chunk) {
      myContext = context;
      myChunkModules = chunk.getModules();
    }

    @Override
    public boolean accept(File file) {
      final RootDescriptor moduleAndRoot = myContext.getModuleAndRoot(file);
      if (moduleAndRoot == null) {
        return true;
      }
      final Module moduleOfFile = moduleAndRoot.module;
      if (myChunkModules.contains(moduleOfFile)) {
        return true;
      }
      Set<Module> moduleOfFileWithDependencies = myCache.get(moduleOfFile);
      if (moduleOfFileWithDependencies == null) {
        moduleOfFileWithDependencies = ProjectPaths.getModulesWithDependentsRecursively(moduleOfFile, true);
        myCache.put(moduleOfFile, moduleOfFileWithDependencies);
      }
      return intersects(moduleOfFileWithDependencies, myChunkModules);
    }

    private static boolean intersects(Set<Module> set1, Set<Module> set2) {
      if (set1.size() < set2.size()) {
        return new HashSet<Module>(set1).removeAll(set2);
      }
      return new HashSet<Module>(set2).removeAll(set1);
    }
  }

}
