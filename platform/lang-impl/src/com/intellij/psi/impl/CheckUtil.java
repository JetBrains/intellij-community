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

package com.intellij.psi.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public class CheckUtil {

  public static void checkWritable(PsiElement element) throws IncorrectOperationException{
    if (!element.isWritable()){
      if (element instanceof PsiDirectory){
        throw new IncorrectOperationException(
          PsiBundle.message("cannot.modify.a.read.only.directory", ((PsiDirectory)element).getVirtualFile().getPresentableUrl()));
      }
      else{
        PsiFile file = element.getContainingFile();
        if (file == null){
          throw new IncorrectOperationException();
        }
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null){
          throw new IncorrectOperationException();
        }
        throw new IncorrectOperationException(PsiBundle.message("cannot.modify.a.read.only.file", virtualFile.getPresentableUrl()));
      }
    }
  }

  public static void checkDelete(VirtualFile file) throws IncorrectOperationException{
    if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
    if (!file.isWritable()) {
      throw new IncorrectOperationException(PsiBundle.message("cannot.delete.a.read.only.file", file.getPresentableUrl()));
    }
    if (file.isDirectory()){
      VirtualFile[] children = file.getChildren();
      for (VirtualFile aChildren : children) {
        checkDelete(aChildren);
      }
    }
  }
}
