// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class CheckUtil {
  private CheckUtil() { }

  public static void checkWritable(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!element.isWritable()) {
      if (element instanceof PsiDirectory) {
        String url = ((PsiDirectory)element).getVirtualFile().getPresentableUrl();
        throw new IncorrectOperationException(CoreBundle.message("cannot.modify.a.read.only.directory", url));
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          throw new IncorrectOperationException();
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
          throw new IncorrectOperationException();
        }
        throw new IncorrectOperationException(CoreBundle.message("cannot.modify.a.read.only.file", virtualFile.getPresentableUrl()));
      }
    }
  }

  public static void checkDelete(@NotNull final VirtualFile file) throws IncorrectOperationException {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (FileTypeRegistry.getInstance().isFileIgnored(file)) {
          return false;
        }
        if (!file.isWritable()) {
          throw new IncorrectOperationException(CoreBundle.message("cannot.delete.a.read.only.file", file.getPresentableUrl()));
        }
        return true;
      }
    });
  }
}