/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class EmptyFileManager implements FileManager {
  public void dispose() {
  }

  public void runStartupActivity() {
  }

  public PsiFile findFile(@NotNull VirtualFile vFile) {
    return null;
  }

  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    return null;
  }

  public void reloadFromDisk(@NotNull PsiFile file)
  {
  }

  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    return null;
  }

  @NotNull
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    return GlobalSearchScope.EMPTY_SCOPE;
  }

  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    return GlobalSearchScope.EMPTY_SCOPE;
  }

  public void cleanupForNextTest() {
  }

  public FileViewProvider findViewProvider(VirtualFile file) {
    return null;
  }

  public FileViewProvider findCachedViewProvider(VirtualFile file) {
    return null;
  }

  public void setViewProvider(final VirtualFile virtualFile, final FileViewProvider singleRootFileViewProvider) {

  }

  public List<PsiFile> getAllCachedFiles() {
    return Collections.emptyList();
  }
}