/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Collections;

/**
 * @author peter
 */
public abstract class FileReferenceHelper {

  public static final ExtensionPointName<FileReferenceHelper> EP_NAME = new ExtensionPointName<FileReferenceHelper>("com.intellij.psi.fileReferenceHelper");

  @NotNull
  public String trimUrl(@NotNull String url) {
    return url;
  }

  @Nullable
  public List<? extends LocalQuickFix> registerFixes(HighlightInfo info, FileReference reference) {
    return Collections.emptyList();
  }

  @Nullable
  public PsiFileSystemItem getPsiFileSystemItem(final Project project, final @NotNull VirtualFile file) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    return file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
  }

  @Nullable
  public abstract PsiFileSystemItem findRoot(final Project project, final @NotNull VirtualFile file);

  @NotNull
  public abstract Collection<PsiFileSystemItem> getRoots(@NotNull Module module);

  @NotNull
  public abstract Collection<PsiFileSystemItem> getContexts(final Project project, final @NotNull VirtualFile file);

  public abstract boolean isMine(final Project project, final @NotNull VirtualFile file);
}
