package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.model.module.JpsModule;

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
  static final Key<Callbacks.ConstantAffectionResolver> CONSTANT_SEARCH_SERVICE = Key.create("_constant_search_service_");

  private final BuilderCategory myCategory;

  protected ModuleLevelBuilder(BuilderCategory category) {
    myCategory = category;
  }

  public enum ExitCode {
    NOTHING_DONE, OK, ABORT, ADDITIONAL_PASS_REQUIRED, CHUNK_REBUILD_REQUIRED
  }

  public abstract ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException;

  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return false;
  }

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
   * @param filesToCompile       files compiled in this round
   * @param successfullyCompiled
   * @return true if additional compilation pass is required, false otherwise
   * @throws Exception
   */
  public final boolean updateMappings(CompileContext context, final Mappings delta, ModuleChunk chunk, Collection<File> filesToCompile, Collection<File> successfullyCompiled) throws IOException {
    if (Utils.errorsDetected(context)) {
      return false;
    }
    try {
      boolean additionalPassRequired = false;

      final Set<String> removedPaths = getRemovedPaths(context, chunk);

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

          final HashSet<File> affectedBeforeDif = new HashSet<File>(allAffectedFiles);

          final ModulesBasedFileFilter moduleBasedFilter = new ModulesBasedFileFilter(context, chunk);
          final boolean incremental = globalMappings.differentiateOnIncrementalMake(
            delta, removedPaths, filesToCompile, allCompiledFiles, allAffectedFiles, moduleBasedFilter, CONSTANT_SEARCH_SERVICE.get(context)
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
            newlyAffectedFiles
              .removeAll(allCompiledFiles); // the diff operation may have affected the class already compiled in thic compilation round

            final String infoMessage = "Dependency analysis found " + newlyAffectedFiles.size() + " affected files";
            LOG.info(infoMessage);
            context.processMessage(new ProgressMessage(infoMessage));

            if (!newlyAffectedFiles.isEmpty()) {

              if (LOG.isDebugEnabled()) {
                final List<Pair<File, String>> wrongFiles =
                  checkAffectedFilesInCorrectModules(context, newlyAffectedFiles, moduleBasedFilter);
                if (!wrongFiles.isEmpty()) {
                  LOG.debug("Wrong affected files for module chunk " + chunk.getName() + ": ");
                  for (Pair<File, String> pair : wrongFiles) {
                    final String name = pair.second != null ? pair.second : "null";
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

      // save to remove everything that has been integrated
      dropRemovedPaths(context, chunk);

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

  private static List<Pair<File, String>> checkAffectedFilesInCorrectModules(CompileContext context,
                                                                             Collection<File> affected,
                                                                             ModulesBasedFileFilter moduleBasedFilter) {
    if (affected.isEmpty()) {
      return Collections.emptyList();
    }
    final List<Pair<File, String>> result = new ArrayList<Pair<File, String>>();
    for (File file : affected) {
      if (!moduleBasedFilter.accept(file)) {
        final RootDescriptor moduleAndRoot = context.getProjectDescriptor().rootsIndex.getModuleAndRoot(context, file);
        result.add(Pair.create(file, moduleAndRoot != null ? moduleAndRoot.module : null));
      }
    }
    return result;
  }

  private static boolean chunkContainsAffectedFiles(CompileContext context, ModuleChunk chunk, final Set<File> affected)
    throws IOException {
    final Set<String> chunkModules = new HashSet<String>();
    for (JpsModule module : chunk.getModules()) {
      chunkModules.add(module.getName());
    }
    if (!chunkModules.isEmpty()) {
      for (File file : affected) {
        final RootDescriptor moduleAndRoot = context.getProjectDescriptor().rootsIndex.getModuleAndRoot(context, file);
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

  private static Set<String> getRemovedPaths(CompileContext context, ModuleChunk chunk) {
    final Map<ModuleBuildTarget, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
    if (map == null) {
      return Collections.emptySet();
    }
    final Set<String> removed = new HashSet<String>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      final Collection<String> modulePaths = map.get(target);
      if (modulePaths != null) {
        removed.addAll(modulePaths);
      }
    }
    return removed;
  }

  private static void dropRemovedPaths(CompileContext context, ModuleChunk chunk) throws IOException {
    final Map<ModuleBuildTarget, Collection<String>> map = Utils.REMOVED_SOURCES_KEY.get(context);
    if (map != null) {
      for (ModuleBuildTarget target : chunk.getTargets()) {
        final Collection<String> paths = map.remove(target);
        if (paths != null) {
          final SourceToOutputMapping storage = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target.getModuleName(),
                                                                                                                target.isTests());
          for (String path : paths) {
            storage.remove(path);
          }
        }
      }
    }
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
      final RootDescriptor rd = myContext.getProjectDescriptor().rootsIndex.getModuleAndRoot(myContext, file);
      if (rd == null) {
        return true;
      }
      final JpsModule moduleOfFile = myContext.getProjectDescriptor().rootsIndex.getModuleByName(rd.module);
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
