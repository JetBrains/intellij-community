// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.codeInsight.multiverse.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class MockFileManager implements FileManager {
  private final PsiManagerEx myManager;
  // in mock tests it's LightVirtualFile, they're only alive when they're referenced,
  // and there can not be several instances representing the same file
  private final Map<VirtualFile, FileViewProvider> myViewProviders;

  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile, boolean eventSystemEnabled) {
    return new SingleRootFileViewProvider(myManager, vFile, eventSystemEnabled);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull FileViewProvider createFileViewProvider(@NotNull VirtualFile vFile,
                                                          @NotNull CodeInsightContext context,
                                                          boolean eventSystemEnabled) {
    return createFileViewProvider(vFile, eventSystemEnabled);
  }

  public MockFileManager(PsiManagerEx manager) {
    myManager = manager;
    myViewProviders = ConcurrentFactoryMap.create(key->new SingleRootFileViewProvider(myManager, key),
                                                  () -> CollectionFactory.createConcurrentWeakKeyWeakValueMap());
  }

  @Override
  public @Nullable PsiFile findFile(@NotNull VirtualFile vFile) {
    return getCachedPsiFile(vFile);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable PsiFile findFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    return getCachedPsiFile(vFile);
  }

  @Override
  public @Nullable PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    throw new UnsupportedOperationException("Method findDirectory is not yet implemented in " + getClass().getName());
  }

  @Override
  public void reloadFromDisk(@NotNull PsiFile psiFile) //Q: move to PsiFile(Impl)?
  {
    throw new UnsupportedOperationException("Method reloadFromDisk is not yet implemented in " + getClass().getName());
  }

  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    FileViewProvider provider = findCachedViewProvider(vFile);
    if (provider == null) return null;
    return provider.getPsi(provider.getBaseLanguage());
  }

  @ApiStatus.Internal
  @Override
  public @Nullable PsiFile getCachedPsiFile(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    return getCachedPsiFile(vFile);
  }

  @Override
  public void cleanupForNextTest() {
    myViewProviders.clear();
  }

  @Override
  public FileViewProvider findViewProvider(@NotNull VirtualFile vFile) {
    throw new UnsupportedOperationException("Method findViewProvider is not yet implemented in " + getClass().getName());
  }

  @ApiStatus.Internal
  @Override
  public FileViewProvider findViewProvider(@NotNull VirtualFile vFile, @NotNull CodeInsightContext context) {
    return findViewProvider(vFile);
  }

  @Override
  public @Nullable FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile) {
    return myViewProviders.get(vFile);
  }

  @ApiStatus.Internal
  @Override
  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile vFile,@NotNull CodeInsightContext context) {
    return findCachedViewProvider(vFile);
  }

  @Override
  public void setViewProvider(@NotNull VirtualFile vFile, FileViewProvider viewProvider) {
    myViewProviders.put(vFile, viewProvider);
  }

  @Override
  public @NotNull List<PsiFile> getAllCachedFiles() {
    throw new UnsupportedOperationException("Method getAllCachedFiles is not yet implemented in " + getClass().getName());
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull List<@NotNull FileViewProvider> findCachedViewProviders(@NotNull VirtualFile vFile) {
    return ContainerUtil.createMaybeSingletonList(myViewProviders.get(vFile));
  }
}
