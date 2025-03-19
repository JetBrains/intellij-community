// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.StampsStorage;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * @author Eugene Zhuravlev
 */
public final class FSOperations {
  private static final Logger LOG = Logger.getInstance(FSOperations.class);
  public static final GlobalContextKey<Set<Path>> ALL_OUTPUTS_KEY = GlobalContextKey.create("_all_project_output_dirs_");
  private static final GlobalContextKey<Set<BuildTarget<?>>> TARGETS_COMPLETELY_MARKED_DIRTY = GlobalContextKey.create("_targets_completely_marked_dirty_");

  /**
   * @return true if file is marked as "dirty" in the specified compilation round
   */
  public static boolean isMarkedDirty(CompileContext context, final CompilationRound round, Path file) {
    JavaSourceRootDescriptor rootDescriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file.toFile());
    if (rootDescriptor == null) {
      return false;
    }
    return context.getProjectDescriptor().fsState.isMarkedForRecompilation(context, round, rootDescriptor, file);
  }

  /**
   * @deprecated use markDirty(CompileContext context, final CompilationRound round, final File file)
   *
   * Note: marked file will well be visible as "dirty" only on the <b>next</b> compilation round!
   *
   */
  @Deprecated(forRemoval = true)
  public static void markDirty(CompileContext context, final File file) throws IOException {
    markDirty(context, CompilationRound.NEXT, file);
  }

  public static void markDirty(@NotNull CompileContext context, @NotNull CompilationRound round, @NotNull File file) throws IOException {
    JavaSourceRootDescriptor rootDescriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rootDescriptor != null) {
      ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
      projectDescriptor.fsState.markDirty(context,
                                          round,
                                          file.toPath(),
                                          rootDescriptor,
                                          projectDescriptor.dataManager.getFileStampStorage(rootDescriptor.target),
                                          false);
    }
  }

  public static void markDirtyIfNotDeleted(@NotNull CompileContext context,
                                           @NotNull CompilationRound round,
                                           @NotNull Path file) throws IOException {
    JavaSourceRootDescriptor rootDescriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file.toFile());
    if (rootDescriptor != null) {
      ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
      projectDescriptor.fsState.markDirtyIfNotDeleted(context,
                                                      round,
                                                      file,
                                                      rootDescriptor,
                                                      projectDescriptor.dataManager.getFileStampStorage(rootDescriptor.target));
    }
  }

  public interface DirtyFilesHolderBuilder<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
    /**
     * Marks specified files dirty if the file is not deleted
     * If the file was marked dirty as a result of this operation or had been already marked dirty,
     * the file is stored internally in the builder
     */
    DirtyFilesHolderBuilder<R, T> markDirtyFile(T target, @NotNull File file) throws IOException;

    default DirtyFilesHolderBuilder<R, T> markDirtyFile(T target, @NotNull Path file) throws IOException {
      return markDirtyFile(target, file.toFile());
    }

    /**
     * @return an object accumulating information about files marked with this builder
     * Use returned object for further processing of marked files. For example, the object can be passed to
     * {@link BuildOperations#cleanOutputsCorrespondingToChangedFiles(CompileContext, DirtyFilesHolder)}
     * to clean outputs corresponding marked sources
     */
    DirtyFilesHolder<R, T> create();
  }

  /**
   * @param round desired compilation round at which these dirty marks should be visible
   * @return a builder object that marks dirty files and collects data about files marked
   */
  public static <R extends BuildRootDescriptor, T extends BuildTarget<R>> DirtyFilesHolderBuilder<R, T> createDirtyFilesHolderBuilder(CompileContext context, final CompilationRound round) {
    return new DirtyFilesHolderBuilder<>() {
      private final Map<T, Map<R, Set<File>>> dirtyFiles = new HashMap<>();

      @Override
      public DirtyFilesHolderBuilder<R, T> markDirtyFile(T target, @NotNull File file) throws IOException {
        ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
        R rootDescriptor = projectDescriptor.getBuildRootIndex().findParentDescriptor(file, List.of(target.getTargetType()), context);
        if (rootDescriptor == null) {
          return this;
        }

        Path nioPath = file.toPath();
        if (projectDescriptor.fsState.markDirtyIfNotDeleted(context, round, nioPath, rootDescriptor, projectDescriptor.dataManager.getFileStampStorage(target)) ||
            projectDescriptor.fsState.isMarkedForRecompilation(context, round, rootDescriptor, nioPath)) {
          Map<R, Set<File>> targetFiles = dirtyFiles.get(target);
          if (targetFiles == null) {
            targetFiles = new HashMap<>();
            dirtyFiles.put(target, targetFiles);
          }
          Set<File> rootFiles = targetFiles.get(rootDescriptor);
          if (rootFiles == null) {
            rootFiles = FileCollectionFactory.createCanonicalFileSet();
            targetFiles.put(rootDescriptor, rootFiles);
          }
          rootFiles.add(file);
        }
        return this;
      }

      @Override
      public DirtyFilesHolder<R, T> create() {
        return new DirtyFilesHolder<>() {
          @Override
          public void processDirtyFiles(@NotNull FileProcessor<R, T> processor) throws IOException {
            for (Map.Entry<T, Map<R, Set<File>>> entry : dirtyFiles.entrySet()) {
              final T target = entry.getKey();
              for (Map.Entry<R, Set<File>> targetEntry : entry.getValue().entrySet()) {
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
            return List.of();
          }

          @Override
          public @NotNull @Unmodifiable Collection<@NotNull Path> getRemoved(@NotNull T target) {
            return List.of();
          }
        };
      }
    };
  }

  // used externally
  @SuppressWarnings({"unused", "IO_FILE_USAGE"})
  public static void markDeleted(CompileContext context, File file) throws IOException {
    JavaSourceRootDescriptor rootDescriptor = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, file);
    if (rootDescriptor != null) {
      ProjectDescriptor projectDescriptor = context.getProjectDescriptor();
      projectDescriptor.fsState.registerDeleted(context,
                                                rootDescriptor.target,
                                                file.toPath(),
                                                projectDescriptor.dataManager.getFileStampStorage(rootDescriptor.target));
    }
  }

  public static void markDirty(@NotNull CompileContext context,
                               @NotNull CompilationRound round,
                               @NotNull ModuleChunk chunk,
                               @Nullable FileFilter filter) throws IOException {
    for (ModuleBuildTarget target : chunk.getTargets()) {
      markDirty(context, round, target, filter);
    }
  }

  public static void markDirty(@NotNull CompileContext context,
                               @NotNull CompilationRound round,
                               @NotNull ModuleBuildTarget target,
                               @Nullable FileFilter filter) throws IOException {
    markDirtyFiles(context,
                   target,
                   round,
                   context.getProjectDescriptor().dataManager.getFileStampStorage(target),
                   true,
                   null,
                   filter);
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

    BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    for (ModuleBuildTarget target : dirtyTargets) {
      StampsStorage<?> stampStorage = dataManager.getFileStampStorage(target);
      markDirtyFiles(context, target, round, stampStorage, true, null, filter);
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

  static void markDirtyFiles(@NotNull CompileContext context,
                             @NotNull BuildTarget<?> target,
                             @NotNull CompilationRound round,
                             @Nullable StampsStorage<?> stampStorage,
                             boolean forceMarkDirty,
                             @Nullable Set<? super Path> currentFiles,
                             @Nullable FileFilter filter) throws IOException {
    boolean completelyMarkedDirty = true;
    for (BuildRootDescriptor rootDescriptor : context.getProjectDescriptor().getBuildRootIndex().getTargetRoots(target, context)) {
      if (!rootDescriptor.getRootFile().exists() ||
          //temp roots are managed by compilers themselves
          (rootDescriptor instanceof JavaSourceRootDescriptor && ((JavaSourceRootDescriptor)rootDescriptor).isTemp)) {
        continue;
      }
      if (filter == null) {
        context.getProjectDescriptor().fsState.clearRecompile(rootDescriptor);
      }
      completelyMarkedDirty &= traverseRecursively(context, rootDescriptor, round, rootDescriptor.getRootFile(), stampStorage, forceMarkDirty, currentFiles, filter);
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
  private static boolean traverseRecursively(@NotNull CompileContext context,
                                             @NotNull BuildRootDescriptor rootDescriptor,
                                             @NotNull CompilationRound round,
                                             @NotNull File file,
                                             @Nullable StampsStorage<?> stampStorage,
                                             boolean forceDirty,
                                             @Nullable Set<? super Path> currentFiles,
                                             @Nullable FileFilter filter) throws IOException {
    var fileConsumer = new BiConsumer<Path, BasicFileAttributes>() {
      boolean allFilesMarked = true;

      @Override
      public void accept(@NotNull Path file, @Nullable BasicFileAttributes attrs) {
        if (filter != null && filter != FileFilters.EVERYTHING && !filter.accept(file.toFile())) {
          allFilesMarked = false;
          return;
        }

        boolean markDirty = forceDirty;
        if (!markDirty) {
          try {
            markDirty = stampStorage == null || stampStorage.getCurrentStampIfUpToDate(file, rootDescriptor.getTarget(), attrs) == null;
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        if (markDirty) {
          // if it is a full project rebuild, all storages are already completely cleared;
          // so passing null because there is no need to access the storage to clear non-existing data
          StampsStorage<?> marker = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) ? null : stampStorage;
          try {
            context.getProjectDescriptor().fsState.markDirty(context, round, file, rootDescriptor, marker, false);
          }
          catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        if (currentFiles != null) {
          currentFiles.add(file);
        }
        if (!markDirty) {
          allFilesMarked = false;
        }
      }
    };
    BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    traverseRecursively(file.toPath(),
                        f -> rootIndex.isDirectoryAccepted(f, rootDescriptor),
                        f -> rootIndex.isFileAccepted(f, rootDescriptor),
                        fileConsumer);
    return fileConsumer.allFilesMarked;
  }

  @FunctionalInterface
  public interface FileConsumer {
    void consume(@NotNull File file, @Nullable BasicFileAttributes attrs) throws IOException;
  }

  private static void traverseRecursively(@NotNull Path fromFile,
                                          @NotNull Predicate<Path> dirFilter,
                                          @NotNull Predicate<Path> fileFilter,
                                          @NotNull BiConsumer<Path, BasicFileAttributes> processor) throws IOException {
    Files.walkFileTree(fromFile, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
        if (e instanceof NoSuchFileException) {
          return FileVisitResult.TERMINATE;
        }
        if (e instanceof FileSystemLoopException) {
          LOG.info(e);
          // In some cases (e.g., Google Drive File Stream) loop detection for directories works incorrectly.
          // Fallback: try to traverse in the old IO way.
          traverseRecursivelyIO(file.toFile(), dirFilter, fileFilter, processor);
          return FileVisitResult.SKIP_SUBTREE;
        }
        return super.visitFileFailed(file, e);
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return dirFilter.test(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
      }

      @Override
      public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
        if (fileFilter.test(f)) {
          processor.accept(f, attrs);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void traverseRecursivelyIO(@NotNull File fromFile,
                                            @NotNull Predicate<Path> dirFilter,
                                            @NotNull Predicate<Path> fileFilter,
                                            @NotNull BiConsumer<Path, BasicFileAttributes> processor) throws IOException {
    File[] children = fromFile.listFiles();
    if (children == null) {
      // is a file
      if (fileFilter.test(fromFile.toPath())) {
        processor.accept(fromFile.toPath(), null);
      }
    }
    else {
      // is a directory
      if (children.length > 0 && dirFilter.test(fromFile.toPath())) {
        for (File child : children) {
          traverseRecursivelyIO(child, dirFilter, fileFilter, processor);
        }
      }
    }
  }

  public static void pruneEmptyDirs(@NotNull CompileContext context, @Unmodifiable @Nullable Set<Path> dirsToDelete) {
    if (dirsToDelete == null || dirsToDelete.isEmpty()) {
      return;
    }

    Set<Path> doNotDelete = ALL_OUTPUTS_KEY.get(context);
    if (doNotDelete == null) {
      doNotDelete = FileCollectionFactory.createCanonicalPathSet();
      for (BuildTarget<?> target : context.getProjectDescriptor().getBuildTargetIndex().getAllTargets()) {
        for (File root : target.getOutputRoots(context)) {
          doNotDelete.add(root.toPath());
        }
      }
      ALL_OUTPUTS_KEY.set(context, doNotDelete);
    }

    Set<Path> additionalDirs = null;
    Set<Path> toDelete = dirsToDelete;
    while (toDelete != null) {
      for (Path file : toDelete) {
        // important: do not force deletion if the directory is not empty!
        boolean deleted;
        try {
          deleted = !doNotDelete.contains(file) && Files.deleteIfExists(file);
        }
        catch (IOException e) {
          deleted = false;
        }
        if (deleted) {
          Path parentFile = file.getParent();
          if (parentFile != null) {
            if (additionalDirs == null) {
              additionalDirs = FileCollectionFactory.createCanonicalPathSet();
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

  public static long lastModified(@NotNull File file) {
    return lastModified(file.toPath());
  }

  @ApiStatus.Internal
  public static long lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    }
    catch (NoSuchFileException ignored) {
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return 0L;
  }

  @ApiStatus.Internal
  public static long lastModified(Path path, BasicFileAttributes attribs) {
    return attribs != null && attribs.isRegularFile()? attribs.lastModifiedTime().toMillis() : lastModified(path);
  }

  @ApiStatus.Internal
  public static BasicFileAttributes getAttributes(Path path) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class);
    }
    catch (NoSuchFileException ignored) {
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return null;
  }

  public static void copy(File fromFile, File toFile) throws IOException {
    Path from = fromFile.toPath();
    Path to = toFile.toPath();
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

  @ApiStatus.Internal
  public static void addCompletelyMarkedDirtyTarget(CompileContext context, BuildTarget<?> target) {
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
