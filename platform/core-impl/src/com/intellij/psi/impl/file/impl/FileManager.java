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

package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public interface FileManager extends Disposable {
  @Nullable
  PsiFile findFile(@NotNull VirtualFile vFile);

  @Nullable
  PsiDirectory findDirectory(@NotNull VirtualFile vFile);

  void reloadFromDisk(@NotNull PsiFile file); //Q: move to PsiFile(Impl)?

  @Nullable
  PsiFile getCachedPsiFile(@NotNull VirtualFile vFile);

  @TestOnly
  void cleanupForNextTest();

  FileViewProvider findViewProvider(@NotNull VirtualFile file);
  FileViewProvider findCachedViewProvider(@NotNull VirtualFile file);
  void setViewProvider(@NotNull VirtualFile virtualFile, @Nullable FileViewProvider fileViewProvider);

  @NotNull
  List<PsiFile> getAllCachedFiles();

  @NotNull
  FileViewProvider createFileViewProvider(@NotNull VirtualFile file, boolean eventSystemEnabled);
}
