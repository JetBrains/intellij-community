// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.PluginId;
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
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * @see FileBasedIndexExtension
 * @author dmitrylomov
 */
@NonExtendable
public abstract class FileBasedIndex {
  /**
   * Don't wrap this method in one [smart] read action because on large projects it will either cause a freeze
   * without a proper indicator or ProgressManager.checkCanceled() or will be constantly interrupted by write action and restarted.
   * Consider using it without a read action if you don't require a consistent snapshot.
   */
  public abstract void iterateIndexableFiles(@NotNull ContentIterator processor, @NotNull Project project, @Nullable ProgressIndicator indicator);

  /**
   * Don't wrap this method in one [smart] read action because on large projects it will either cause a freeze
   * without a proper indicator or ProgressManager.checkCanceled() or will be constantly interrupted by write action and restarted.
   * Consider using it without a read action if you don't require a consistent snapshot.
   *
   * @implNote method tries to use {@link com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile} there applicable, to avoid
   * storing the non-indexable files in VFS
   */
  @RequiresBackgroundThread
  @ApiStatus.Experimental
  public abstract boolean iterateNonIndexableFiles(@NotNull Project project,
                                                @Nullable VirtualFileFilter acceptFilter,
                                                @NotNull ContentIterator processor);

  /**
   * @return the file which the current thread is indexing right now, or {@code null} if current thread isn't indexing.
   */
  public abstract @Nullable VirtualFile getFileBeingCurrentlyIndexed();

  /**
   * @return the file which the current thread is writing evaluated values of indexes right now,
   * or {@code null} if current thread isn't writing index values.
   */
  @Internal
  public abstract @Nullable IndexWritingFile getFileWritingCurrentlyIndexes();

  @Internal
  public static class IndexWritingFile {
    public final int fileId;

    public IndexWritingFile(int id) {
      fileId = id;
    }
  }

  @Internal
  public void registerProjectFileSets(@NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @ApiStatus.Internal
  public abstract @Nullable IdFilter projectIndexableFiles(@Nullable Project project);

  @Internal
  public void onProjectClosing(@NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  /**
   * Should be called only in dumb mode and only in a read action
   * @deprecated Please use getCurrentDumbModeAccessType(Project)
   */
  @Internal
  @Deprecated
  public @Nullable DumbModeAccessType getCurrentDumbModeAccessType() {
    return getCurrentDumbModeAccessType(null);
  }

  /**
   * Should be called only in dumb mode and only in a read action
   */
  @Internal
  public @Nullable DumbModeAccessType getCurrentDumbModeAccessType(@Nullable Project project) {
    throw new UnsupportedOperationException();
  }

  @Internal
  public <T> @NotNull Processor<? super T> inheritCurrentDumbAccessType(@NotNull Processor<? super T> processor) {
    return processor;
  }

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getService(FileBasedIndex.class);
  }

  public static int getFileId(final @NotNull VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  /**
   * @deprecated see {@link com.intellij.openapi.vfs.newvfs.ManagingFS#findFileById(int)}
   */ // note: upsource implementation requires access to Project here, please don't remove (not anymore)
  @Deprecated(forRemoval = true)
  public abstract VirtualFile findFileById(Project project, int id);

  public void requestRebuild(@NotNull ID<?, ?> indexId) {
    requestRebuild(indexId, new RebuildRequestedByUserAction(PluginUtil.getInstance().findPluginId(new Throwable())));
  }

  public abstract @NotNull <K, V> @Unmodifiable List<V> getValues(@NotNull ID<K, V> indexId, @NotNull K dataKey, @NotNull GlobalSearchScope filter);

  public abstract @NotNull <K, V> @Unmodifiable Collection<VirtualFile> getContainingFiles(@NotNull ID<K, V> indexId,
                                                                                           @NotNull K dataKey,
                                                                                           @NotNull GlobalSearchScope filter);

  /**
   * @return lazily reified iterator of VirtualFile's.
   */
  @ApiStatus.Experimental
  public abstract @NotNull <K, V> Iterator<VirtualFile> getContainingFilesIterator(@NotNull ID<K, V> indexId,
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

  public abstract <K, V> boolean processFilesContainingAnyKey(@NotNull ID<K, V> indexId,
                                                              @NotNull Collection<? extends K> dataKeys,
                                                              @NotNull GlobalSearchScope filter,
                                                              @Nullable IdFilter idFilter,
                                                              @Nullable Condition<? super V> valueChecker,
                                                              @NotNull Processor<? super VirtualFile> processor);

  /**
   * Query all the keys in the project. Note that the result may contain keys from other projects, orphan keys and the like,
   * but it is guaranteed that it contains at least all the keys from specified project.
   *
   * @param project is used to make sure that all the keys belonging to the project are found.
   *                In addition to keys from specified project the method may return a number of keys irrelevant
   *                to the project or orphan keys that are not relevant to any project.
   *                <p>
   *                {@code project} will be used to filter out irrelevant keys only if corresponding indexing extension returns
   *                {@code true} from its {@linkplain FileBasedIndexExtension#traceKeyHashToVirtualFileMapping()}
   *
   * @return collection that contains at least all the keys that can be found in the specified project.
   * <p>
   * It is often true that the result contains some strings that are not valid keys in given project, unless
   * {@linkplain FileBasedIndexExtension#traceKeyHashToVirtualFileMapping()} returns true.
   *
   * @see FileBasedIndexExtension#traceKeyHashToVirtualFileMapping()
   */
  public abstract @NotNull @Unmodifiable <K> Collection<K> getAllKeys(@NotNull ID<K, ?> indexId, @NotNull Project project);

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  @Internal
  public abstract <K> void ensureUpToDate(@NotNull ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter);

  /**
   * Marks index as requiring rebuild and requests asynchronously full indexing.
   * In unit tests one needs for full effect to dispatch events from event queue
   * with {@code PlatformTestUtil.dispatchAllEventsInIdeEventQueue()}
   */
  public abstract void requestRebuild(@NotNull ID<?, ?> indexId, @NotNull Throwable throwable);

  /**
   * @deprecated use {@link #requestRebuild(ID)} or {@link #requestRebuild(ID, Throwable)}
   */
  @Deprecated
  public abstract <K> void scheduleRebuild(@NotNull ID<K, ?> indexId, @NotNull Throwable e);

  public abstract void requestReindex(@NotNull VirtualFile file);

  public abstract <K, V> boolean getFilesWithKey(@NotNull ID<K, V> indexId,
                                                 @NotNull Set<? extends K> dataKeys,
                                                 @NotNull Processor<? super VirtualFile> processor,
                                                 @NotNull GlobalSearchScope filter);

  /**
   * Executes command and allow its to have an index access in dumb mode.
   * Inside the command it's safe to call index related stuff and
   * {@link com.intellij.openapi.project.IndexNotReadyException} is not expected to happen here.
   *
   * <p> Please use {@link DumbModeAccessType#ignoreDumbMode(Runnable)} or {@link DumbModeAccessType#ignoreDumbMode(ThrowableComputable)}
   * since they produce less boilerplate code.
   *
   * <p> In smart mode, the behavior is similar to direct command execution
   * @param dumbModeAccessType - defines in which manner command should be executed. Does a client expect only reliable data
   * @param command - a command to execute
   */
  @ApiStatus.Experimental
  public void ignoreDumbMode(@NotNull DumbModeAccessType dumbModeAccessType, @NotNull Runnable command) {
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

  /**
   * If {@link FileBasedIndexExtension#traceKeyHashToVirtualFileMapping()} is false {@link IdFilter} will be ignored.
   */
  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<? super K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexId, processor, scope.getProject());
  }

  public abstract @NotNull @Unmodifiable <K, V> Map<K, V> getFileData(@NotNull ID<K, V> id, @NotNull VirtualFile virtualFile, @NotNull Project project);

  public abstract <V> @Nullable V getSingleEntryIndexData(@NotNull ID<Integer, V> id,
                                                          @NotNull VirtualFile virtualFile,
                                                          @NotNull Project project);

  public static void iterateRecursively(final @NotNull VirtualFile root,
                                        final @NotNull ContentIterator processor,
                                        final @Nullable ProgressIndicator indicator,
                                        final @Nullable Set<? super VirtualFile> visitedRoots,
                                        final @Nullable ProjectFileIndex projectFileIndex) {
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

  @ApiStatus.Experimental
  public static final class AllKeysQuery<K, V> {
    private final @NotNull ID<K, V> indexId;
    private final @NotNull Collection<? extends K> dataKeys;
    private final @Nullable Condition<? super V> valueChecker;

    public AllKeysQuery(@NotNull ID<K, V> id,
                        @NotNull @Unmodifiable Collection<? extends K> keys,
                        @Nullable Condition<? super V> checker) {
      indexId = id;
      dataKeys = keys;
      valueChecker = checker;
    }

    public @NotNull ID<K, V> getIndexId() {
      return indexId;
    }

    public @NotNull @Unmodifiable Collection<? extends K> getDataKeys() {
      return dataKeys;
    }

    public @Nullable Condition<? super V> getValueChecker() {
      return valueChecker;
    }
  }

  /**
   * Analogue of {@link FileBasedIndex#processFilesContainingAllKeys(ID, Collection, GlobalSearchScope, Condition, Processor)}
   * which optimized to perform several queries for different indexes.
   */
  @ApiStatus.Experimental
  public boolean processFilesContainingAllKeys(@NotNull Collection<? extends AllKeysQuery<?, ?>> queries,
                                               @NotNull GlobalSearchScope filter,
                                               @NotNull Processor<? super VirtualFile> processor) {
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
   * An input filter which accepts {@link IndexedFile} as parameter.
   * One could use this interface for filters which require {@link Project} instance to filter out files.
   * <p>
   * Note that in most the cases no one needs this filter.
   * And the only use case is to optimize indexed file count when the corresponding indexer is relatively slow.
   */
  @ApiStatus.Experimental
  @OverrideOnly
  public interface ProjectSpecificInputFilter extends InputFilter {
    @Override
    default boolean acceptInput(@NotNull VirtualFile file) {
      PluginException.reportDeprecatedDefault(getClass(), "acceptInput", "`acceptInput(IndexedFile)` should be called");
      return false;
    }

    boolean acceptInput(@NotNull IndexedFile file);
  }

  /**
   * @see DefaultFileTypeSpecificInputFilter
   */
  @OverrideOnly
  public interface FileTypeSpecificInputFilter extends InputFilter {
    void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink);
  }

  @Internal
  public static final boolean ourSnapshotMappingsEnabled = SystemProperties.getBooleanProperty("idea.index.snapshot.mappings.enabled", false);

  /**
   * @deprecated Is always true
   */
  @Deprecated(forRemoval = true)
  @Internal
  public static boolean isIndexAccessDuringDumbModeEnabled() {
    return true;
  }

  @Internal
  public static final boolean USE_IN_MEMORY_INDEX = Boolean.getBoolean("idea.use.in.memory.file.based.index");

  @Internal
  public static final boolean IGNORE_PLAIN_TEXT_FILES = Boolean.getBoolean("idea.ignore.plain.text.indexing");

  @Internal
  public static boolean isCompositeIndexer(@NotNull DataIndexer<?, ?, ?> indexer) {
    return indexer instanceof CompositeDataIndexer && !USE_IN_MEMORY_INDEX;
  }

  @Internal
  public void loadIndexes() {
  }

  @Internal
  public static class RebuildRequestedByUserAction extends Throwable {
    private final @Nullable PluginId myRequestorPluginId;

    private RebuildRequestedByUserAction(@Nullable PluginId requestorPluginId) { myRequestorPluginId = requestorPluginId; }

    public @Nullable PluginId getRequestorPluginId() {
      return myRequestorPluginId;
    }
  }
}
