// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @see FileBasedIndexExtension
 * @author dmitrylomov
 */
public abstract class FileBasedIndex {
  public abstract void iterateIndexableFiles(@NotNull ContentIterator processor, @NotNull Project project, @Nullable ProgressIndicator indicator);

  /**
   * @return the file which the current thread is indexing right now, or {@code null} if current thread isn't indexing.
   */
  @Nullable
  public abstract VirtualFile getFileBeingCurrentlyIndexed();

  @ApiStatus.Internal
  @Nullable
  public DumbModeAccessType getCurrentDumbModeAccessType() {
    throw new UnsupportedOperationException();
  }

  public abstract void registerIndexableSet(@NotNull IndexableFileSet set, @Nullable Project project);

  public abstract void removeIndexableSet(@NotNull IndexableFileSet set);

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getService(FileBasedIndex.class);
  }

  public static int getFileId(@NotNull final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  /**
   * @deprecated see {@link com.intellij.openapi.vfs.newvfs.ManagingFS#findFileById(int)}
   */ // note: upsource implementation requires access to Project here, please don't remove (not anymore)
  @Deprecated
  public abstract VirtualFile findFileById(Project project, int id);

  public void requestRebuild(@NotNull ID<?, ?> indexId) {
    requestRebuild(indexId, new Throwable());
  }

  @NotNull
  public abstract <K, V> List<V> getValues(@NotNull ID<K, V> indexId, @NotNull K dataKey, @NotNull GlobalSearchScope filter);

  @NotNull
  public abstract <K, V> Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
                                                                    @NotNull K dataKey,
                                                                    @NotNull GlobalSearchScope filter);

  /**
   * @return {@code false} if ValueProcessor.process() returned {@code false}; {@code true} otherwise or if ValueProcessor was not called at all
   */
  public abstract <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                               @NotNull K dataKey,
                                               @Nullable VirtualFile inFile,
                                               @NotNull ValueProcessor<? super V> processor,
                                               @NotNull GlobalSearchScope filter);

  /**
   * @return {@code false} if ValueProcessor.process() returned {@code false}; {@code true} otherwise or if ValueProcessor was not called at all
   */
  public <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                               @NotNull K dataKey,
                                               @Nullable VirtualFile inFile,
                                               @NotNull ValueProcessor<? super V> processor,
                                               @NotNull GlobalSearchScope filter,
                                               @Nullable IdFilter idFilter) {
    return processValues(indexId, dataKey, inFile, processor, filter);
  }

  public abstract <K, V> long getIndexModificationStamp(@NotNull ID<K, V> indexId, @NotNull Project project);

  public abstract <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                               @NotNull Collection<? extends K> dataKeys,
                                                               @NotNull GlobalSearchScope filter,
                                                               @Nullable Condition<? super V> valueChecker,
                                                               @NotNull Processor<? super VirtualFile> processor);

  /**
   * It is guaranteed to return data which is up-to-date within the given project.
   * Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist.
   */
  @NotNull
  public abstract <K> Collection<K> getAllKeys(@NotNull ID<K, ?> indexId, @NotNull Project project);

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  @ApiStatus.Internal
  public abstract <K> void ensureUpToDate(@NotNull ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter);

  public abstract void requestRebuild(@NotNull ID<?, ?> indexId, @NotNull Throwable throwable);

  public abstract <K> void scheduleRebuild(@NotNull ID<K, ?> indexId, @NotNull Throwable e);

  public abstract void requestReindex(@NotNull VirtualFile file);

  public abstract <K, V> boolean getFilesWithKey(@NotNull ID<K, V> indexId,
                                                 @NotNull Set<? extends K> dataKeys,
                                                 @NotNull Processor<? super VirtualFile> processor,
                                                 @NotNull GlobalSearchScope filter);

  /**
   * Executes command and allow its to have an index access in dumb mode.
   * Inside the command it's safe to call index related stuff and
   * {@link com.intellij.openapi.project.IndexNotReadyException} are not expected to be happen here.
   *
   * <p> In smart mode, the behavior is similar to direct command execution
   * @param command - a command to execute
   * @param dumbModeAccessType - defines in which manner command should be executed. Does a client expect only reliable data
   */
  @ApiStatus.Experimental
  public void ignoreDumbMode(@NotNull Runnable command,
                             @NotNull DumbModeAccessType dumbModeAccessType) {
    ignoreDumbMode(dumbModeAccessType, () -> {
      command.run();
      return null;
    });
  }

  @ApiStatus.Experimental
  public <T, E extends Throwable> T ignoreDumbMode(@NotNull DumbModeAccessType dumbModeAccessType,
                                                   @NotNull ThrowableComputable<T, E> computable) throws E {
    throw new UnsupportedOperationException();
  }

  /**
   * It is guaranteed to return data which is up-to-date within the given project.
   */
  public abstract <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @Nullable Project project);

  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexId, processor, scope.getProject());
  }

  @NotNull
  public abstract <K, V> Map<K, V> getFileData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project);

  public static void iterateRecursively(@NotNull final VirtualFile root,
                                        @NotNull final ContentIterator processor,
                                        @Nullable final ProgressIndicator indicator,
                                        @Nullable final Set<? super VirtualFile> visitedRoots,
                                        @Nullable final ProjectFileIndex projectFileIndex) {
    VirtualFileFilter acceptFilter = file -> {
      if (indicator != null) {
        indicator.checkCanceled();
      }
      if (visitedRoots != null && !root.equals(file) && file.isDirectory() && !visitedRoots.add(file)) {
        return false;
      }
      return projectFileIndex == null || !ReadAction.compute(() -> projectFileIndex.isExcluded(file));
    };

    VirtualFileFilter symlinkFilter = file -> {
      if (acceptFilter.accept(file)) {
        if (file.is(VFileProperty.SYMLINK)) {
          if (!Registry.is("indexer.follows.symlinks")) {
            return false;
          }
          VirtualFile canonicalFile = file.getCanonicalFile();
          if (canonicalFile != null) {
            return acceptFilter.accept(canonicalFile);
          }
        }
        return true;
      }
      return false;
    };

    VfsUtilCore.iterateChildrenRecursively(root, symlinkFilter, processor);
  }

  public void invalidateCaches() {
    throw new IncorrectOperationException();
  }

  @ApiStatus.Internal
  public boolean isIndexingCandidate(@NotNull VirtualFile file, @NotNull ID<?, ?> indexId) {
    throw new UnsupportedOperationException();
  }

  @FunctionalInterface
  public interface ValueProcessor<V> {
    /**
     * @param value a value to process
     * @param file the file the value came from
     * @return {@code false} if no further processing is needed, {@code true} otherwise
     */
    boolean process(@NotNull VirtualFile file, V value);
  }

  @FunctionalInterface
  public interface InputFilter {
    boolean acceptInput(@NotNull VirtualFile file);
  }

  /**
   * @see DefaultFileTypeSpecificInputFilter
   */
  public interface FileTypeSpecificInputFilter extends InputFilter {
    void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink);
  }

  /** @deprecated inline true */
  @Deprecated
  public static final boolean ourEnableTracingOfKeyHashToVirtualFileMapping = true;

  @ApiStatus.Internal
  public static final boolean ourSnapshotMappingsEnabled = SystemProperties.getBooleanProperty("idea.index.snapshot.mappings.enabled", true);

  @ApiStatus.Internal
  public static boolean isIndexAccessDuringDumbModeEnabled() {
    return !ourDisableIndexAccessDuringDumbMode;
  }
  private static final boolean ourDisableIndexAccessDuringDumbMode = SystemProperties.getBooleanProperty("idea.disable.index.access.during.dumb.mode", false);

  @ApiStatus.Internal
  public static final boolean USE_IN_MEMORY_INDEX = SystemProperties.is("idea.use.in.memory.file.based.index");

  @ApiStatus.Internal
  public static final boolean IGNORE_PLAIN_TEXT_FILES = SystemProperties.is("idea.ignore.plain.text.indexing");
}
