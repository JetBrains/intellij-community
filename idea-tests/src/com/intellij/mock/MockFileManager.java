/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.mock;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.FactoryMap;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class MockFileManager implements FileManager {
  private PsiManagerEx myManager;
  private FactoryMap<VirtualFile,FileViewProvider> myViewProviders = new FactoryMap<VirtualFile, FileViewProvider>() {
    protected FileViewProvider create(final VirtualFile key) {
      return new SingleRootFileViewProvider(myManager, key);
    }
  };

  public MockFileManager(final PsiManagerEx manager) {
    myManager = manager;
  }

  public void dispose() {
    throw new UnsupportedOperationException("Method dispose is not yet implemented in " + getClass().getName());
  }

  public void runStartupActivity() {
    throw new UnsupportedOperationException("Method runStartupActivity is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    return getCachedPsiFile(vFile);
  }

  @Nullable
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    throw new UnsupportedOperationException("Method findDirectory is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public PsiPackage findPackage(@NotNull String packageName) {
    throw new UnsupportedOperationException("Method findPackage is not yet implemented in " + getClass().getName());
  }

  public PsiDirectory[] getRootDirectories(int rootType) {
    throw new UnsupportedOperationException("Method getRootDirectories is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    throw new UnsupportedOperationException("Method findClass is not yet implemented in " + getClass().getName());
  }

  public PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    throw new UnsupportedOperationException("Method findClasses is not yet implemented in " + getClass().getName());
  }

  public void reloadFromDisk(@NotNull PsiFile file) //Q: move to PsiFile(Impl)?
  {
    throw new UnsupportedOperationException("Method reloadFromDisk is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    return findCachedViewProvider(vFile).getPsi(Language.ANY);
  }

  @NotNull
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    return new MockGlobalSearchScope();
  }

  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    return GlobalSearchScope.allScope(element.getProject());
  }

  public Collection<String> getNonTrivialPackagePrefixes() {
    throw new UnsupportedOperationException("Method getNonTrivialPackagePrefixes is not yet implemented in " + getClass().getName());
  }

  public void cleanupForNextTest() {
    throw new UnsupportedOperationException("Method cleanupForNextTest is not yet implemented in " + getClass().getName());
  }

  public FileViewProvider findViewProvider(VirtualFile file) {
    throw new UnsupportedOperationException("Method findViewProvider is not yet implemented in " + getClass().getName());
  }

  public FileViewProvider findCachedViewProvider(VirtualFile file) {
    return myViewProviders.get(file);
  }

  public void setViewProvider(VirtualFile virtualFile, FileViewProvider fileViewProvider) {
    myViewProviders.put(virtualFile, fileViewProvider);
  }

  public List<PsiFile> getAllCachedFiles() {
    throw new UnsupportedOperationException("Method getAllCachedFiles is not yet implemented in " + getClass().getName());
  }

}
