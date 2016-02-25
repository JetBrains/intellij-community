/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CheckUtil {
  private CheckUtil() { }

  public static void checkWritable(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!element.isWritable()) {
      if (element instanceof PsiDirectory) {
        String url = ((PsiDirectory)element).getVirtualFile().getPresentableUrl();
        throw new IncorrectOperationException(PsiBundle.message("cannot.modify.a.read.only.directory", url));
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
        throw new IncorrectOperationException(PsiBundle.message("cannot.modify.a.read.only.file", virtualFile.getPresentableUrl()));
      }
    }
  }

  public static void checkDelete(@NotNull final VirtualFile file) throws IncorrectOperationException {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (FileTypeRegistry.getInstance().isFileIgnored(file)) {
          return false;
        }
        if (!file.isWritable()) {
          throw new IncorrectOperationException(PsiBundle.message("cannot.delete.a.read.only.file", file.getPresentableUrl()));
        }
        return true;
      }
    });
  }
}