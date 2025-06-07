// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class FileReferenceHelperRegistrar {

  private FileReferenceHelperRegistrar() {
  }

  public static FileReferenceHelper @NotNull [] getHelpers() {
    return FileReferenceHelper.EP_NAME.getExtensions();
  }

  /**
   * @deprecated this method is broken, please avoid using it, use getHelpers() instead
   */
  @Deprecated
  static @NotNull <T extends PsiFileSystemItem> FileReferenceHelper getNotNullHelper(@NotNull T psiFileSystemItem) {
    FileReferenceHelper helper = getHelper(psiFileSystemItem);
    if (helper != null) {
      return helper;
    }
    FileReferenceHelper[] helpers = getHelpers();
    return helpers[helpers.length - 1];
  }

  /**
   * @deprecated this method is broken, please avoid using it, use getHelpers() instead
   */
  @Deprecated
  private static <T extends PsiFileSystemItem> FileReferenceHelper getHelper(final @NotNull T psiFileSystemItem) {
    final VirtualFile file = psiFileSystemItem.getVirtualFile();
    if (file == null) return null;
    final Project project = psiFileSystemItem.getProject();
    return ContainerUtil.find(getHelpers(), fileReferenceHelper -> fileReferenceHelper.isMine(project, file));
  }

  public static @Unmodifiable <T extends PsiFileSystemItem> List<FileReferenceHelper> getHelpers(final @NotNull T psiFileSystemItem) {
    final VirtualFile file = psiFileSystemItem.getVirtualFile();
    if (file == null) return null;
    final Project project = psiFileSystemItem.getProject();
    return ContainerUtil.findAll(getHelpers(), fileReferenceHelper -> fileReferenceHelper.isMine(project, file));
  }

  public static boolean areElementsEquivalent(final @NotNull PsiFileSystemItem element1, final @NotNull PsiFileSystemItem element2) {
    return element2.getManager().areElementsEquivalent(element1, element2);
  }
}
