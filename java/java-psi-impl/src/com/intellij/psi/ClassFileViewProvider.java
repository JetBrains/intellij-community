/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ClassFileViewProvider extends SingleRootFileViewProvider {
  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile file) {
    super(manager, file);
  }

  public ClassFileViewProvider(@NotNull final PsiManager manager, @NotNull final VirtualFile virtualFile, final boolean eventSystemEnabled) {
    super(manager, virtualFile, eventSystemEnabled, JavaClassFileType.INSTANCE);
  }

  @Override
  protected PsiFile createFile(@NotNull final Project project, @NotNull final VirtualFile vFile, @NotNull final FileType fileType) {
    FileIndexFacade fileIndex = ServiceManager.getService(project, FileIndexFacade.class);
    if (!fileIndex.isInLibraryClasses(vFile) && fileIndex.isInSource(vFile)) {
      return new PsiBinaryFileImpl((PsiManagerImpl)getManager(), this);
    }

    // skip inners & anonymous
    if (isInnerClass(vFile, project)) return null;

    return new ClsFileImpl(this);
  }

  public static boolean isInnerClass(@NotNull VirtualFile vFile) {
    return isInnerClass(vFile, null);
  }

  public static boolean isInnerClass(@NotNull VirtualFile vFile, @Nullable Project project) {
    String name = vFile.getNameWithoutExtension();
    int index = name.lastIndexOf('$', name.length());
    if (index > 0 && index < name.length() - 1) {
      String supposedParentName = name.substring(0, index) + ".class";
      VirtualFile supposedParent = vFile.getParent().findChild(supposedParentName);
      if (project == null) {
        return supposedParent != null;
      } else {
        if (supposedParent != null) {
          PsiFile supposedParentFile = PsiManager.getInstance(project).findFile(supposedParent);

          return !isJavaInterface(supposedParentFile);
        }
      }
    }
    return false;
  }

  private static boolean isJavaInterface(PsiFile file) {
    if (file instanceof PsiClassOwner) {
      for (PsiClass cls : ((PsiClassOwner)file).getClasses()) {
        if (cls.isInterface() && cls.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  public SingleRootFileViewProvider createCopy(@NotNull final VirtualFile copy) {
    return new ClassFileViewProvider(getManager(), copy, false);
  }
}