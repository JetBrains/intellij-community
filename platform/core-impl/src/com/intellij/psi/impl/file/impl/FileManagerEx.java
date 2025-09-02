package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.editor.Document;
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

import java.util.function.Consumer;

@ApiStatus.Internal
public interface FileManagerEx extends FileManager {

  @ApiStatus.Internal
  void forEachCachedDocument(@NotNull Consumer<? super @NotNull Document> consumer);

  @TestOnly
  @ApiStatus.Internal
  void assertNoInjectedFragmentsStoredInMaps();

  @ApiStatus.Internal
  @Nullable CodeInsightContext trySetContext(@NotNull FileViewProvider viewProvider, @NotNull CodeInsightContext context);

  @ApiStatus.Internal
  void removeFilesAndDirsRecursively(@NotNull VirtualFile vFile);

  @Nullable
  @ApiStatus.Internal
  PsiFile getCachedPsiFileInner(@NotNull VirtualFile file, @NotNull CodeInsightContext context);

  @RequiresWriteLock
  @ApiStatus.Internal
  void removeInvalidFilesAndDirs(boolean useFind);

  @ApiStatus.Internal
  void reloadPsiAfterTextChange(@NotNull FileViewProvider viewProvider, @NotNull VirtualFile vFile);

  @RequiresReadLock(generateAssertion = false)
  boolean evaluateValidity(@NotNull PsiFile file);

  @ApiStatus.Internal
  @Nullable PsiFile getRawCachedFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @ApiStatus.Internal
  void forceReload(@NotNull VirtualFile vFile);

  @ApiStatus.Internal
  void firePropertyChangedForUnloadedPsi();

  @ApiStatus.Internal
  void dispose();

  @ApiStatus.Internal
  void processQueue();

  @RequiresReadLock
  PsiFile getFastCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context);

  @ApiStatus.Internal
  void processFileTypesChanged(boolean clearViewProviders);

  @RequiresWriteLock
  @ApiStatus.Internal
  void possiblyInvalidatePhysicalPsi();

  @ApiStatus.Internal
  void dispatchPendingEvents();

  @TestOnly
  @ApiStatus.Internal
  void checkConsistency();

  @ApiStatus.Internal
  PsiDirectory getCachedDirectory(@NotNull VirtualFile vFile);
}