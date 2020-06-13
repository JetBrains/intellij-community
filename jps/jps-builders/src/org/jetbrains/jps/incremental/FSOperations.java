// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.storage.StampsStorage;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class FSOperations {
  private static final Logger LOG = Logger.getInstance(FSOperations.class);
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
      pd.fsState.markDirty(context, round, file, rd, pd.getProjectStamps().getStampStorage(), false);
    }
  }

  public static void markDirtyIfNotDeleted(CompileContext context, final CompilationRound round, final File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.markDirtyIfNotDeleted(context, round, file, rd, pd.getProjectStamps().getStampStorage());
    }
  }

  public interface DirtyFilesHolderBuilder<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
    /**
     * Marks specified files dirty if the file is not deleted
     * If the file was marked dirty as a result of this operation or had been already marked dirty,
     * the file is stored internally in the builder
     */
    DirtyFilesHolderBuilder<R, T> markDirtyFile(T target, File file) throws IOException;

    /**
     * @return an object accumulating information about files marked with this builder
     * Use returned object for further processing of marked files. For example, the object can be passed to
     * {@link BuildOperations#cleanOutputsCorrespondingToChangedFiles(CompileContext, DirtyFilesHolder)}
     * to clean outputs corresponding marked sources
     */
    DirtyFilesHolder<R, T> create();
  }

  /**
   * @param context
   * @param round desired compilation round at which these dirty marks should be visible
   * @return a builder object that marks dirty files and collects data about files marked
   */
  public static <R extends BuildRootDescriptor, T extends BuildTarget<R>> DirtyFilesHolderBuilder<R, T> createDirtyFilesHolderBuilder(CompileContext context, final CompilationRound round) {
    return new DirtyFilesHolderBuilder<R, T>() {
      private final Map<T, Map<R, Set<File>>> dirtyFiles = new HashMap<>();
      @Override
      public DirtyFilesHolderBuilder<R, T> markDirtyFile(T target, File file) throws IOException {
        final ProjectDescriptor pd = context.getProjectDescriptor();
        final R rd = pd.getBuildRootIndex().findParentDescriptor(file, Collections.singleton(target.getTargetType()), context);
        if (rd != null) {
          if (pd.fsState.markDirtyIfNotDeleted(context, round, file, rd, pd.getProjectStamps().getStampStorage()) || pd.fsState.isMarkedForRecompilation(context, round, rd, file)) {
            Map<R, Set<File>> targetFiles = dirtyFiles.get(target);
            if (targetFiles == null) {
              targetFiles = new HashMap<>();
              dirtyFiles.put(target, targetFiles);
            }
            Set<File> rootFiles = targetFiles.get(rd);
            if (rootFiles == null) {
              rootFiles = new THashSet<>(FileUtil.FILE_HASHING_STRATEGY);
              targetFiles.put(rd, rootFiles);
            }
            rootFiles.add(file);
          }
        }
        return this;
      }

      @Override
      public DirtyFilesHolder<R, T> create() {
        return new DirtyFilesHolder<R, T>() {
          @Override
          public void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException {
            for (Map.Entry<T, Map<R, Set<File>>> entry : dirtyFiles.entrySet()) {
              final T target = entry.getKey();
              for (Map.Entry<R, Set<File>>  targetEntry: entry.getValue().entrySet()) {
                final R rd = targetEntry.getKey();
                for (File file : targetEntry.getValue()) {
                  processor.apply(target, file, rd);
                }
              }
            }
          }

          @Override
          public boolean hasDirtyFiles() {
            return !dirtyFiles.isEmpty();
          }

          @Override
          public boolean hasRemovedFiles() {
            return false;
          }

          @Override
          public @NotNull Collection<String> getRemovedFiles(@NotNull T target) {
            return Collections.emptyList();
          }
        };
      }
    };
  }

  public static void markDeleted(CompileContext context, File file) throws IOException {
    final JavaSourceRootDescriptor rd = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rd != null) {
      final ProjectDescriptor pd = context.getProjectDescriptor();
      pd.fsState.registerDeleted(context, rd.target, file, pd.getProjectStamps().getStampStorage());
    }
  }

  public static void markDirty(CompileContext context, final CompilationRound round, final ModuleChunk chunk, @Nullable FileFilter filter) throws IOException {
    for (ModuleBuildTarget target : chunk.getTargets()) {
      markDirty(context, round, target, filter);
    }
  }

  public static void markDirty(CompileContext context, final CompilationRound round, final ModuleBuildTarget target, @Nullable FileFilter filter) throws IOException {
    final ProjectDescriptor pd = context.getProjectDescriptor();
    markDirtyFiles(context, target, round, pd.getProjectStamps().getStampStorage(), true, null, filter);
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

    final StampsStorage<? extends StampsStorage.Stamp> stampsStorage = context.getProjectDescriptor().getProjectStamps().getStampStorage();
    for (ModuleBuildTarget target : dirtyTargets) {
      markDirtyFiles(context, target, round, stampsStorage, true, null, filter);
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
                             StampsStorage<? extends StampsStorage.Stamp> stampsStorage,
                             boolean forceMarkDirty,
                             @Nullable Set<? super File> currentFiles,
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
      completelyMarkedDirty &= traverseRecursively(context, rd, round, rd.getRootFile(), stampsStorage, forceMarkDirty, currentFiles, filter);
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
                                             @NotNull final StampsStorage<? extends StampsStorage.Stamp> stampStorage,
                                             final boolean forceDirty,
                                             @Nullable Set<? super File> currentFiles, @Nullable FileFilter filter) throws IOException {

    final BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    final Ref<Boolean> allFilesMarked = Ref.create(Boolean.TRUE);

    Files.walkFileTree(file.toPath(), EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
        if (e instanceof FileSystemLoopException) {
          LOG.info(e);
          // in some cases (e.g. Google Drive File Stream) loop detection for directories works incorrectly
          // fallback: try to traverse in the old IO-way
          final boolean marked = traverseRecursivelyIO(context, rd, round, file.toFile(), stampStorage, forceDirty, currentFiles, filter);
          if (!marked) {
            allFilesMarked.set(Boolean.FALSE);
          }
          return FileVisitResult.SKIP_SUBTREE;
        }
        return super.visitFileFailed(file, e);
      }

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
            markDirty = stampStorage.isDirtyStamp(stampStorage.getPreviousStamp(_file, rd.getTarget()), _file, attrs);
          }
          if (markDirty) {
            // if it is full project rebuild, all storages are already completely cleared;
            // so passing null because there is no need to access the storage to clear non-existing data
            final StampsStorage<? extends StampsStorage.Stamp> marker = context.isProjectRebuild() ? null : stampStorage;
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

  private static boolean traverseRecursivelyIO(CompileContext context,
                                             final BuildRootDescriptor rd,
                                             final CompilationRound round,
                                             final File file,
                                             @NotNull final StampsStorage<? extends StampsStorage.Stamp> stampsStorage,
                                             final boolean forceDirty,
                                             @Nullable Set<? super File> currentFiles, @Nullable FileFilter filter) throws IOException {
    BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    final File[] children = file.listFiles();
    if (children != null) { // is directory
      boolean allMarkedDirty = true;
      if (children.length > 0 && rootIndex.isDirectoryAccepted(file, rd)) {
        for (File child : children) {
          allMarkedDirty &= traverseRecursivelyIO(context, rd, round, child, stampsStorage, forceDirty, currentFiles, filter);
        }
      }
      return allMarkedDirty;
    }
    // is file

    if (!rootIndex.isFileAccepted(file, rd)) {
      return true;
    }
    if (filter != null && !filter.accept(file)) {
      return false;
    }

    boolean markDirty = forceDirty;
    if (!markDirty) {
      markDirty = stampsStorage.isDirtyStamp(stampsStorage.getPreviousStamp(file, rd.getTarget()), file);
    }
    if (markDirty) {
      // if it is full project rebuild, all storages are already completely cleared;
      // so passing null because there is no need to access the storage to clear non-existing data
      final StampsStorage<? extends StampsStorage.Stamp> marker = context.isProjectRebuild() ? null : stampsStorage;
      context.getProjectDescriptor().fsState.markDirty(context, round, file, rd, marker, false);
    }
    if (currentFiles != null) {
      currentFiles.add(file);
    }
    return markDirty;
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
      catch (AccessDeniedException e) {
        if (!Files.isWritable(to) && toFile.setWritable(true)) {
          Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING); // repeat once the file seems to be writable again
        }
        else {
          throw e;
        }
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
