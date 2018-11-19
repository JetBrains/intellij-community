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
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class FSOperations {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.FSOperations");
  public static final GlobalContextKey<Set<File>> ALL_OUTPUTS_KEY = GlobalContextKey.create("_all_project_output_dirs_");
  private static final GlobalContextKey<Set<BuildTarget<?>>> TARGETS_COMPLETELY_MARKED_DIRTY = GlobalContextKey.create("_targets_completely_marked_dirty_");

  /**
   * @param context
   * @param round
   * @param file
   * @return true if file is marked as "dirty" in the specified compilation round
   * @throws IOException
   */
  public static boolean isMarkedDirty(CompileContext context, final CompilationRound round, final File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      return pd.fsState.isMarkedForRecompilation(context, round, rd, file);
    }
    return false;
  }

  /**
   * @deprecated use markDirty(CompileContext context, final CompilationRound round, final File file)
   *
   * Note: marked file will well be visible as "dirty" only on the <b>next</b> compilation round!
   * @throws IOException
   *
   */
  @Deprecated
  public static void markDirty(CompileContext context, final File file) throws IOException {
    markDirty(context, CompilationRound.NEXT, file);
  }

  public static void markDirty(CompileContext context, final CompilationRound round, final File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirty(context, round, file, rd, pd.timestamps.getStorage(), false);
    }
  }

  /**
   * @deprecated use markDirtyIfNotDeleted(CompileContext context, final CompilationRound round, final File file)
   */
  @Deprecated
  public static void markDirtyIfNotDeleted(CompileContext context, final File file) throws IOException {
    markDirtyIfNotDeleted(context, CompilationRound.NEXT, file);
  }

  public static void markDirtyIfNotDeleted(CompileContext context, final CompilationRound round, final File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirtyIfNotDeleted(context, round, file, rd, pd.timestamps.getStorage());
    }
  }

  public static void markDeleted(CompileContext context, File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.registerDeleted(context, rd.target, file, pd.timestamps.getStorage());
    }
  }

  /**
   * @deprecated use markDirty(CompileContext context, final CompilationRound round, final ModuleChunk chunk, @Nullable FileFilter filter)
   */
  @Deprecated
  public static void markDirty(CompileContext context, final ModuleChunk chunk, @Nullable FileFilter filter) throws IOException {
    markDirty(context, CompilationRound.NEXT, chunk, filter);
  }

  public static void markDirty(CompileContext context, final CompilationRound round, final ModuleChunk chunk, @Nullable FileFilter filter) throws IOException {
    for (ModuleBuildTarget target : chunk.getTargets()) {
      markDirty(context, round, target, filter);
    }
  }

  public static void markDirty(CompileContext context, final CompilationRound round, final ModuleBuildTarget target, @Nullable FileFilter filter) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    markDirtyFiles(context, target, round, pd.timestamps.getStorage(), true, null, filter);
  }

  /**
   * @deprecated use markDirtyRecursively(CompileContext context, final CompilationRound round, ModuleChunk chunk, FileFilter filter)
   */
  @Deprecated
  public static void markDirtyRecursively(CompileContext context, ModuleChunk chunk) throws IOException {
    markDirtyRecursively(context, CompilationRound.NEXT, chunk);
  }

  public static void markDirtyRecursively(CompileContext context, final CompilationRound round, ModuleChunk chunk) throws IOException {
    markDirtyRecursively(context, round, chunk, null);
  }

  public static void markDirtyRecursively(CompileContext context, final CompilationRound round, ModuleChunk chunk, @Nullable FileFilter filter) throws IOException {
    Set<JpsModule> modules = chunk.getModules();
    Set<ModuleBuildTarget> targets = chunk.getTargets();
    final Set<ModuleBuildTarget> dirtyTargets = new HashSet<>(targets);

    // now mark all modules that depend on dirty modules
    final JpsJavaClasspathKind classpathKind = JpsJavaClasspathKind.compile(chunk.containsTests());
    boolean found = false;
    for (BuildTargetChunk targetChunk : context.getProjectDescriptor().getBuildTargetIndex().getSortedTargetChunks(context)) {
      if (!found) {
        if (targetChunk.getTargets().equals(chunk.getTargets())) {
          found = true;
        }
      }
      else {
        for (final BuildTarget<?> target : targetChunk.getTargets()) {
          if (target instanceof ModuleBuildTarget) {
            final Set<JpsModule> deps = getDependentModulesRecursively(((ModuleBuildTarget)target).getModule(), classpathKind);
            if (ContainerUtil.intersects(deps, modules)) {
              for (BuildTarget<?> buildTarget : targetChunk.getTargets()) {
                if (buildTarget instanceof ModuleBuildTarget) {
                  dirtyTargets.add((ModuleBuildTarget)buildTarget);
                }
              }
              break;
            }
          }
        }
      }
    }

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      // mark as non-incremental only the module that triggered non-incremental change
      for (ModuleBuildTarget target : targets) {
        if (!isMarkedDirty(context, target)) {
          // if the target was marked dirty already, all its files were compiled, so
          // it makes no sense to mark it non-incremental
          context.markNonIncremental(target);
        }
      }
    }

    removeTargetsAlreadyMarkedDirty(context, dirtyTargets);

    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();
    for (ModuleBuildTarget target : dirtyTargets) {
      markDirtyFiles(context, target, round, timestamps, true, null, filter);
    }

  }

  private static Set<JpsModule> getDependentModulesRecursively(final JpsModule module, final JpsJavaClasspathKind kind) {
    return JpsJavaExtensionService.dependencies(module).includedIn(kind).recursivelyExportedOnly().getModules();
  }

  public static void processFilesToRecompile(CompileContext context, ModuleChunk chunk, FileProcessor<JavaSourceRootDescriptor, ? super ModuleBuildTarget> processor) throws IOException {
    for (ModuleBuildTarget target : chunk.getTargets()) {
      processFilesToRecompile(context, target, processor);
    }
  }

  public static void processFilesToRecompile(CompileContext context, @NotNull ModuleBuildTarget target, FileProcessor<JavaSourceRootDescriptor, ? super ModuleBuildTarget> processor) throws IOException {
    context.getProjectDescriptor().fsState.processFilesToRecompile(context, target, processor);
  }

  static void markDirtyFiles(CompileContext context,
                             BuildTarget<?> target,
                             final CompilationRound round,
                             Timestamps timestamps,
                             boolean forceMarkDirty,
                             @Nullable THashSet<File> currentFiles,
                             @Nullable FileFilter filter) throws IOException {
    boolean completelyMarkedDirty = true;
    for (BuildRootDescriptor rd : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
      if (!rd.getRootFile().exists() ||
          //temp roots are managed by compilers themselves
          (rd instanceof JavaSourceRootDescriptor && ((JavaSourceRootDescriptor)rd).isTemp)) {
        continue;
      }
      if (filter == null) {
        context.getProjectDescriptor().fsState.clearRecompile(rd);
      }
      //final FSCache fsCache = rd.canUseFileCache() ? context.getProjectDescriptor().getFSCache() : FSCache.NO_CACHE;
      completelyMarkedDirty &= traverseRecursively(context, rd, round, rd.getRootFile(), timestamps, forceMarkDirty, currentFiles, filter);
    }

    if (completelyMarkedDirty) {
      addCompletelyMarkedDirtyTarget(context, target);
    }
  }

  /**
   * Marks changed files under {@code file} as dirty.
   * @return {@code true} if all compilable files were marked dirty and {@code false} if some of them were skipped because they weren't accepted
   * by {@code filter} or wasn't modified
   */
  private static boolean traverseRecursively(CompileContext context,
                                             final BuildRootDescriptor rd,
                                             final CompilationRound round,
                                             final File file,
                                             @NotNull final Timestamps tsStorage,
                                             final boolean forceDirty,
                                             @Nullable Set<File> currentFiles, @Nullable FileFilter filter) throws IOException {

    final BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    final Ref<Boolean> allFilesMarked = Ref.create(Boolean.TRUE);

    Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return rootIndex.isDirectoryAccepted(dir.toFile(), rd)? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }

      @Override
      public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
        final File _file = f.toFile();
        if (!rootIndex.isFileAccepted(_file, rd)) { // ignored file
          return FileVisitResult.CONTINUE;
        }
        if (filter != null && !filter.accept(_file)) {
          allFilesMarked.set(Boolean.FALSE);
        }
        else {
          boolean markDirty = forceDirty;
          if (!markDirty) {
            // for symlinks the attr structure reflects the symlink's timestamp and not symlink's target timestamp
            markDirty = tsStorage.getStamp(_file, rd.getTarget()) != (attrs.isRegularFile()? attrs.lastModifiedTime().toMillis() : lastModified(f));
          }
          if (markDirty) {
            // if it is full project rebuild, all storages are already completely cleared;
            // so passing null because there is no need to access the storage to clear non-existing data
            final Timestamps marker = context.isProjectRebuild() ? null : tsStorage;
            context.getProjectDescriptor().fsState.markDirty(context, round, _file, rd, marker, false);
          }
          if (currentFiles != null) {
            currentFiles.add(_file);
          }
          if (!markDirty) {
            allFilesMarked.set(Boolean.FALSE);
          }
        }
        return FileVisitResult.CONTINUE;
      }

    });

    return allFilesMarked.get();
  }

  public static void pruneEmptyDirs(CompileContext context, @Nullable final Set<File> dirsToDelete) {
    if (dirsToDelete == null || dirsToDelete.isEmpty()) return;

    Set<File> doNotDelete = ALL_OUTPUTS_KEY.get(context);
    if (doNotDelete == null) {
      doNotDelete = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
      for (BuildTarget<?> target : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets()) {
        doNotDelete.addAll(target.getOutputRoots(context));
      }
      ALL_OUTPUTS_KEY.set(context, doNotDelete);
    }

    Set<File> additionalDirs = null;
    Set<File> toDelete = dirsToDelete;
    while (toDelete != null) {
      for (File file : toDelete) {
        // important: do not force deletion if the directory is not empty!
        final boolean deleted = !doNotDelete.contains(file) && file.delete();
        if (deleted) {
          final File parentFile = file.getParentFile();
          if (parentFile != null) {
            if (additionalDirs == null) {
              additionalDirs = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
            }
            additionalDirs.add(parentFile);
          }
        }
      }
      toDelete = additionalDirs;
      additionalDirs = null;
    }
  }

  public static boolean isMarkedDirty(CompileContext context, ModuleChunk chunk) {
    synchronized (TARGETS_COMPLETELY_MARKED_DIRTY) {
      Set<BuildTarget<?>> marked = TARGETS_COMPLETELY_MARKED_DIRTY.get(context);
      return marked != null && marked.containsAll(chunk.getTargets());
    }
  }

  public static long lastModified(File file) {
    return lastModified(file.toPath());
  }

  private static long lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return 0L;
  }

  public static void copy(File fromFile, File toFile) throws IOException {
    final Path from = fromFile.toPath();
    final Path to = toFile.toPath();
    try {
      try {
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
      }
      catch (NoSuchFileException e) {
        final File parent = toFile.getParentFile();
        if (parent != null && parent.mkdirs()) {
          Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING); // repeat on successful target dir creation
        }
        else {
          throw e;
        }
      }
    }
    catch (IOException e) {
      // fallback: trying 'classic' copying via streams
      LOG.info("Error copying "+ fromFile.getPath() + " to " + toFile.getPath() + " with NIO API", e);
      FileUtil.copyContent(fromFile, toFile);
    }
  }

  public static boolean isMarkedDirty(CompileContext context, BuildTarget<?> target) {
    synchronized (TARGETS_COMPLETELY_MARKED_DIRTY) {
      Set<BuildTarget<?>> marked = TARGETS_COMPLETELY_MARKED_DIRTY.get(context);
      return marked != null && marked.contains(target);
    }
  }

  private static void addCompletelyMarkedDirtyTarget(CompileContext context, BuildTarget<?> target) {
    synchronized (TARGETS_COMPLETELY_MARKED_DIRTY) {
      Set<BuildTarget<?>> marked = TARGETS_COMPLETELY_MARKED_DIRTY.get(context);
      if (marked == null) {
        marked = new HashSet<>();
        TARGETS_COMPLETELY_MARKED_DIRTY.set(context, marked);
      }
      marked.add(target);
    }
  }

  private static void removeTargetsAlreadyMarkedDirty(CompileContext context, Set<ModuleBuildTarget> targetsSetToFilter) {
    synchronized (TARGETS_COMPLETELY_MARKED_DIRTY) {
      Set<BuildTarget<?>> marked = TARGETS_COMPLETELY_MARKED_DIRTY.get(context);
      if (marked != null) {
        targetsSetToFilter.removeAll(marked);
      }
    }
  }
}
