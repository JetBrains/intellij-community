/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class EmptyFileManager implements FileManager {
  private final PsiManagerImpl myManager;

  EmptyFileManager(final PsiManagerImpl manager) {
    myManager = manager;
  }

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

  public FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  public FileViewProvider findCachedViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  @NotNull
  public FileViewProvider createFileViewProvider(@NotNull final VirtualFile file, final boolean physical) {
    return new SingleRootFileViewProvider(myManager, file, physical);
  }

  public void setViewProvider(@NotNull final VirtualFile virtualFile, final FileViewProvider singleRootFileViewProvider) {

  }

  public List<PsiFile> getAllCachedFiles() {
    return Collections.emptyList();
  }
}
