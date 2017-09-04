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
package com.intellij.ide;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

/**
 * @author yole
 */
public class JavaFileIconPatcher implements FileIconPatcher {
  @Override
  public Icon patchIcon(final Icon baseIcon, final VirtualFile file, final int flags, final Project project) {
    if (project == null) {
      return baseIcon;
    }

    FileType fileType = file.getFileType();
    if (fileType == StdFileTypes.JAVA && !FileIndexUtil.isJavaSourceFile(project, file)) {
      return PlatformIcons.JAVA_OUTSIDE_SOURCE_ICON;
    }

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PsiClassOwner && psiFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE) {
      PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
      if (classes.length > 0) {
        // prefer icon of the class named after file
        final String fileName = file.getNameWithoutExtension();
        for (PsiClass aClass : classes) {
          if (aClass instanceof SyntheticElement) {
            return baseIcon;
          }
          if (Comparing.strEqual(aClass.getName(), fileName)) {
            return aClass.getIcon(flags);
          }
        }
        return classes[classes.length - 1].getIcon(flags);
      }
    }
    return baseIcon;
  }
}
