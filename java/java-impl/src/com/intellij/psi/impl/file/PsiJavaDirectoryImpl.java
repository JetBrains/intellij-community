/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiJavaDirectoryImpl extends PsiDirectoryImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiJavaDirectoryImpl");

  public PsiJavaDirectoryImpl(final PsiManagerImpl manager, final VirtualFile file) {
    super(manager, file);
  }

  @Override
  public void checkCreateFile(@NotNull final String name) throws IncorrectOperationException {
    final FileType type = FileTypeManager.getInstance().getFileTypeByFileName(name);
    if (type == StdFileTypes.CLASS) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    super.checkCreateFile(name);
  }

  @Override
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

  @Override
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

  @Override
  public ItemPresentation getPresentation() {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(this);
    return aPackage != null && !StringUtil.isEmpty(aPackage.getName()) ? aPackage.getPresentation() : super.getPresentation();
  }
}
