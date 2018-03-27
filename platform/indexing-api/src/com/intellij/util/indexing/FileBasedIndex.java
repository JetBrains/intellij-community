/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Author: dmitrylomov
 */
public abstract class FileBasedIndex {
  public abstract void iterateIndexableFiles(@NotNull ContentIterator processor, @NotNull Project project, ProgressIndicator indicator);

  public void iterateIndexableFilesConcurrently(@NotNull ContentIterator processor, @NotNull Project project, ProgressIndicator indicator) {
    iterateIndexableFiles(processor, project, indicator);
  }

  public abstract void registerIndexableSet(@NotNull IndexableFileSet set, @Nullable Project project);

  public abstract void removeIndexableSet(@NotNull IndexableFileSet set);

  public static FileBasedIndex getInstance() {
    return ApplicationManager.getApplication().getComponent(FileBasedIndex.class);
  }

  public static int getFileId(@NotNull final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  // note: upsource implementation requires access to Project here, please don't remove
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
   * @return false if ValueProcessor.process() returned false; true otherwise or if ValueProcessor was not called at all
   */
  public abstract <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                               @NotNull K dataKey,
                                               @Nullable VirtualFile inFile,
                                               @NotNull FileBasedIndex.ValueProcessor<V> processor,
                                               @NotNull GlobalSearchScope filter);

  /**
   * @return false if ValueProcessor.process() returned false; true otherwise or if ValueProcessor was not called at all
   */
  public <K, V> boolean processValues(@NotNull ID<K, V> indexId,
                                               @NotNull K dataKey,
                                               @Nullable VirtualFile inFile,
                                               @NotNull FileBasedIndex.ValueProcessor<V> processor,
                                               @NotNull GlobalSearchScope filter,
                                               @Nullable IdFilter idFilter) {
    return processValues(indexId, dataKey, inFile, processor, filter);
  }

  public abstract <K, V> boolean processFilesContainingAllKeys(@NotNull ID<K, V> indexId,
                                                               @NotNull Collection<K> dataKeys,
                                                               @NotNull GlobalSearchScope filter,
                                                               @Nullable Condition<V> valueChecker,
                                                               @NotNull Processor<VirtualFile> processor);

  /**
   * @param project it is guaranteed to return data which is up-to-date withing the project
   *                Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  @NotNull
  public abstract <K> Collection<K> getAllKeys(@NotNull ID<K, ?> indexId, @NotNull Project project);

  /**
   * DO NOT CALL DIRECTLY IN CLIENT CODE
   * The method is internal to indexing engine end is called internally. The method is public due to implementation details
   */
  public abstract <K> void ensureUpToDate(@NotNull ID<K, ?> indexId, @Nullable Project project, @Nullable GlobalSearchScope filter);

  public abstract void requestRebuild(@NotNull ID<?, ?> indexId, Throwable throwable);

  public abstract <K> void scheduleRebuild(@NotNull ID<K, ?> indexId, @NotNull Throwable e);

  public abstract void requestReindex(@NotNull VirtualFile file);

  public abstract <K, V> boolean getFilesWithKey(@NotNull ID<K, V> indexId,
                                                 @NotNull Set<K> dataKeys,
                                                 @NotNull Processor<VirtualFile> processor,
                                                 @NotNull GlobalSearchScope filter);

  /**
   * @param project it is guaranteed to return data which is up-to-date withing the project
   *                Keys obtained from the files which do not belong to the project specified may not be up-to-date or even exist
   */
  public abstract <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<K> processor, @Nullable Project project);

  public <K> boolean processAllKeys(@NotNull ID<K, ?> indexId, @NotNull Processor<K> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return processAllKeys(indexId, processor, scope.getProject());
  }

  public static void iterateRecursively(@Nullable final VirtualFile root,
                                        @NotNull final ContentIterator processor,
                                        @Nullable final ProgressIndicator indicator,
                                        @Nullable final Set<VirtualFile> visitedRoots,
                                        @Nullable final ProjectFileIndex projectFileIndex) {
    if (root == null) {
      return;
    }

    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!acceptsFile(file)) return false;
        if (file.is(VFileProperty.SYMLINK)) {
          if(!Registry.is("indexer.follows.symlinks")) return false;
          VirtualFile canonicalFile = file.getCanonicalFile();

          if (canonicalFile != null) {
            if(!acceptsFile(canonicalFile)) return false;
          }
        }
        if (indicator != null) indicator.checkCanceled();

        processor.processFile(file);
        return true;
      }

      private boolean acceptsFile(@NotNull VirtualFile file) {
        if (visitedRoots != null && !root.equals(file) && file.isDirectory() && !visitedRoots.add(file)) {
          return false;
        }
        if (projectFileIndex != null && ReadAction.compute(() -> projectFileIndex.isExcluded(file))) {
          return false;
        }
        return true;
      }
    });
  }

  public void invalidateCaches() {
    throw new IncorrectOperationException();
  }

  @FunctionalInterface
  public interface ValueProcessor<V> {
    /**
     * @param value a value to process
     * @param file the file the value came from
     * @return false if no further processing is needed, true otherwise
     */
    boolean process(@NotNull VirtualFile file, V value);
  }

  @FunctionalInterface
  public interface InputFilter {
    boolean acceptInput(@NotNull VirtualFile file);
  }

  public interface FileTypeSpecificInputFilter extends InputFilter {
    void registerFileTypesUsedForIndexing(@NotNull Consumer<FileType> fileTypeSink);
  }

  // TODO: remove once changes becomes permanent
  public static final boolean ourEnableTracingOfKeyHashToVirtualFileMapping =
    SystemProperties.getBooleanProperty("idea.enable.tracing.keyhash2virtualfile", true);
}
