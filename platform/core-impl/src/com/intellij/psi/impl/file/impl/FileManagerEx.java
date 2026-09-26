// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

@ApiStatus.Internal
public interface FileManagerEx extends FileManager {
  @TestOnly
  void assertNoInjectedFragmentsStoredInMaps();

  /**
   * Updates the context of `viewProvider` to `context` if the current context of viewProvider is anyContext.
   * If the current context of viewProvider is not anyContext, does nothing.
   *
   * @return the effective context of viewProvider, or `null` if viewProvider is missing in the cache.
   */
  @Nullable CodeInsightContext trySetContext(@NotNull FileViewProvider viewProvider, @NotNull CodeInsightContext context);

  void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile);

  @Nullable
  PsiFile getCachedPsiFileInner(@NotNull VirtualFile file, @NotNull CodeInsightContext context);

  @NotNull @Unmodifiable
  List<PsiFile> getCachedPsiFilesInner(@NotNull VirtualFile file);

  /**
   * Removes invalid files and directories from the cache after VFS move or delete.
   */
  @RequiresWriteLock
  void updatePsiAfterVfsMoveOrDelete();

  void reloadPsiAfterTextChange(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile vFile);

  /**
   * See doc of {@link #possiblyInvalidatePhysicalPsi()}
   *
   * @param file the file to check
   * @return true if the given PsiFile is valid
   */
  @RequiresReadLock(generateAssertion = false)
  boolean evaluateValidity(@NotNull PsiFile file);

  void forceReload(@NotNull VirtualFile vFile);

  void firePropertyChangedForUnloadedPsi();

  void dispose();

  /**
   * Processes the queue of garbage-collected files.
   */
  void processQueue();

  /**
   * It tries to not perform any expensive ops like creating files/reparse/resurrecting PsiFile from temp comatose state.
   *
   * @return associated psi file
   */
  @RequiresReadLock
  @Nullable PsiFile getFastCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  /**
   * If clearViewProviders is true, then it removes all view providers.
   * If clearViewProviders is false, then it marks all view providers as "possibly invalid". See {@link #possiblyInvalidatePhysicalPsi()}.
   * <p>
   * Also fires property-change-event with PsiTreeChangeEvent.PROP_FILE_TYPES.
   */
  void processFileTypesChanged(boolean clearViewProviders);


  /**
   * Originally, all PSI was invalidated on root change, to avoid UI freeze (IDEA-172762),
   * but that has led to too many PIEAEs (like IDEA-191185, IDEA-188292, IDEA-184186, EA-114990).
   * <p>
   * Ideally, those clients should all be converted to smart pointers, but that proved to be quite hard to do, especially without breaking API.
   * And they mostly worked before those batch invalidations.
   * <p>
   * So now we have a smarter way of dealing with this issue. On root change, we mark PSI as "potentially invalid".
   * Then, when someone calls "isValid" (hopefully not for all cached PSI at once, and hopefully in a background thread),
   * we check if the old PSI is equivalent to the one that would be re-created in its place.
   * If yes, we return valid. If no, we invalidate the old PSI forever and return the new one.
   */
  @RequiresWriteLock
  void possiblyInvalidatePhysicalPsi();

  void dispatchPendingEvents();

  @TestOnly
  void checkConsistency();

  @Nullable PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile);
}