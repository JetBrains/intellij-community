package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.ether.dependencyView.ClassRepr;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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

  public static boolean isFileDirty(File file, CompileContext context, TimestampStorage tsStorage) throws Exception {
    return !context.isMake() || tsStorage.getStamp(file) != file.lastModified();
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

    final TimestampStorage tsStorage = context.getBuildDataManager().getTimestampStorage(getName());
    final Set<String> removedPaths = getRemovedPaths(context);

    final Mappings globalMappings = context.getMappings();

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (globalMappings) {
      if (context.isMake()) {
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
          if (!newlyAffectedFiles.isEmpty()) {
            for (File file : newlyAffectedFiles) {
              tsStorage.markDirty(file);
            }
            additionalPassRequired = chunkContainsAffectedFiles(context, chunk, newlyAffectedFiles);
          }
        }
        else {
          additionalPassRequired = true;
          context.setDirty(chunk, true);
        }
      }

      globalMappings.integrate(delta, successfullyCompiled, removedPaths);
      for (File file : successfullyCompiled) {
        tsStorage.saveStamp(file);
      }
    }

    return additionalPassRequired;
  }

  // delete all class files that according to mappings correspond to given sources
  public static void deleteCorrespondingClasses(CompileContext context, Collection<File> sources) {
    if (context.isMake() && !sources.isEmpty()) {
      final Mappings mappings = context.getMappings();
      for (File file : sources) {
        final Set<ClassRepr> classes = mappings.getClasses(FileUtil.toSystemIndependentName(file.getPath()));
        if (classes != null) {
          for (ClassRepr aClass : classes) {
            final String fileName = aClass.getFileName();
            if (fileName != null) {
              FileUtil.delete(new File(fileName));
            }
          }
        }
      }
    }
  }

  private static boolean chunkContainsAffectedFiles(CompileContext context, ModuleChunk chunk, final Set<File> affected) throws Exception {
    final Set<File> chunkRoots = new HashSet<File>();
    for (String r : context.isCompilingTests() ? chunk.getTestRoots() : chunk.getSourceRoots()) {
      chunkRoots.add(new File(r));
    }
    for (File file : affected) {
      if (PathUtil.isUnder(chunkRoots, file)) {
        return true;
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
