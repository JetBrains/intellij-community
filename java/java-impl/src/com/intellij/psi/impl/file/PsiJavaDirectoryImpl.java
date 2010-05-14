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
package com.intellij.psi.impl.file;

import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiJavaDirectoryImpl extends PsiDirectoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiJavaDirectoryImpl");

  public PsiJavaDirectoryImpl(PsiManagerImpl manager, VirtualFile file) {
    super(manager, file);
  }

  public void checkCreateFile(@NotNull final String name) throws IncorrectOperationException {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    if (type == StdFileTypes.CLASS) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    super.checkCreateFile(name);
  }

  public PsiElement add(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      final String name = ((PsiClass)element).getName();
      if (name != null) {
        final PsiClass newClass = JavaDirectoryService.getInstance().createClass(this, name);
        return newClass.replace(element);
      }
      else {
        LOG.error("not implemented");
        return null;
      }
    }
    else {
      return super.add(element);
    }
  }

  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiClass) {
      if (((PsiClass)element).getContainingClass() == null) {
        JavaDirectoryServiceImpl.checkCreateClassOrInterface(this, ((PsiClass)element).getName());
      }
      else {
        LOG.error("not implemented");
      }
    }
    else {
      super.checkAdd(element);
    }
  }

  public void navigate(final boolean requestFocus) {
    ProjectViewSelectInTarget.select(getProject(), this, ProjectViewPane.ID, null, getVirtualFile(), requestFocus);
  }
}
