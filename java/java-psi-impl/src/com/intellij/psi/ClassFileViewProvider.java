/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiBinaryFileImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClassFileViewProvider extends SingleRootFileViewProvider {
  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
    super(manager, file, eventSystemEnabled, JavaClassFileType.INSTANCE);
  }

  @Override
  protected PsiFile createFile(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileType fileType) {
    FileIndexFacade fileIndex = ServiceManager.getService(project, FileIndexFacade.class);
    if (!fileIndex.isInLibraryClasses(file) && fileIndex.isInSource(file)) {
      return new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }

    // skip inners & anonymous
    if (isInnerClass(file)) return null;

    return new ClsFileImpl(this);
  }

  public static boolean isInnerClass(@NotNull VirtualFile file) {
    String name = file.getNameWithoutExtension();
    int index = name.lastIndexOf('$', name.length());
    if (index > 0 && index < name.length() - 1) {
      String supposedParentName = name.substring(0, index) + ".class";
      if (file.getParent().findChild(supposedParentName) != null) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}