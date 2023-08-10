// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.include;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class FileIncludeManager {

  public static FileIncludeManager getManager(Project project) {
    return project.getService(FileIncludeManager.class);
  }

  public abstract VirtualFile[] getIncludedFiles(@NotNull VirtualFile file, boolean compileTimeOnly);

  public abstract VirtualFile[] getIncludedFiles(@NotNull VirtualFile file, boolean compileTimeOnly, boolean recursively);

  public abstract VirtualFile[] getIncludingFiles(@NotNull VirtualFile file, boolean compileTimeOnly);

  public abstract void processIncludingFiles(PsiFile context, Processor<? super Pair<VirtualFile, FileIncludeInfo>> processor);

  @Nullable
  public abstract PsiFileSystemItem resolveFileInclude(@NotNull FileIncludeInfo info, @NotNull PsiFile context);
}
