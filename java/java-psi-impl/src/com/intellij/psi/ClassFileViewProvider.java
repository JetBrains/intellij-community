/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClassFileViewProvider extends SingleRootFileViewProvider {
  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile virtualFile, final boolean physical) {
    super(manager, virtualFile, physical);
  }

  @Override
  protected PsiFile createFile(@NotNull final Project project, @NotNull final VirtualFile vFile, @NotNull final FileType fileType) {
    final FileIndexFacade fileIndex = ServiceManager.getService(project, FileIndexFacade.class);
    if (fileIndex.isInLibraryClasses(vFile) || !fileIndex.isInSource(vFile)) {
      String name = vFile.getName();

      // skip inners & anonymous (todo: read actual class name from file)
      int dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) dotIndex = name.length();
      int index = name.lastIndexOf('$', dotIndex);
      if (index <= 0 || index == dotIndex - 1) {
        return new ClsFileImpl((PsiManagerImpl)PsiManager.getInstance(project), this);
      }
    }

    return null;
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull final VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}