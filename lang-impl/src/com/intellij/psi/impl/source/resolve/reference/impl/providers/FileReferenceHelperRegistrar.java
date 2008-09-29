/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class FileReferenceHelperRegistrar {

  private FileReferenceHelperRegistrar() {
  }

  public static FileReferenceHelper[] getHelpers() {
    return Extensions.getExtensions(FileReferenceHelper.EP_NAME);
  }

  @NotNull
  public static <T extends PsiFileSystemItem> FileReferenceHelper getNotNullHelper(@NotNull T psiFileSystemItem) {
    final FileReferenceHelper helper = getHelper(psiFileSystemItem);
    return helper == null ? new NullFileReferenceHelper() : helper;
  }

  @Nullable
  public static <T extends PsiFileSystemItem> FileReferenceHelper getHelper(@NotNull final T psiFileSystemItem) {
    final VirtualFile file = psiFileSystemItem.getVirtualFile();
    if (file == null) return null;
    final Project project = psiFileSystemItem.getProject();
    return ContainerUtil.find(getHelpers(), new Condition<FileReferenceHelper>() {
      public boolean value(final FileReferenceHelper fileReferenceHelper) {
        return fileReferenceHelper.isMine(project, file);
      }
    });
  }

  public static boolean areElementsEquivalent(@NotNull final PsiFileSystemItem element1, @NotNull final PsiFileSystemItem element2) {
    return element2.getManager().areElementsEquivalent(element1, element2);
  }

  private static class NullFileReferenceHelper implements FileReferenceHelper {

    @NotNull
    public String getDirectoryTypeName() {
      throw new UnsupportedOperationException("Method getDirectoryTypeName is not yet implemented in " + getClass().getName());
    }

    @NotNull
    public String trimUrl(@NotNull final String url) {
      return url;
    }

    public List<? extends LocalQuickFix> registerFixes(final HighlightInfo info, final FileReference reference) {
      return Collections.emptyList();
    }

    public PsiFileSystemItem getPsiFileSystemItem(final Project project, @NotNull final VirtualFile file) {
      return null;
    }

    public PsiFileSystemItem findRoot(final Project project, @NotNull final VirtualFile file) {
      return null;
    }

    @NotNull
    public Collection<PsiFileSystemItem> getRoots(@NotNull final Module module) {
      return Collections.emptyList();
    }

    @NotNull
    public Collection<PsiFileSystemItem> getContexts(final Project project, final @NotNull VirtualFile file) {
      return Collections.emptyList();
    }

    public boolean isMine(final Project project, final @NotNull VirtualFile file) {
      return false;
    }
  }
}
