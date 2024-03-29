// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public final class JavaFileIconPatcher implements FileIconPatcher {
  @Override
  public @NotNull Icon patchIcon(@NotNull Icon icon, @NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (project == null) {
      return icon;
    }

    FileType fileType = file.getFileType();
    if (fileType == JavaFileType.INSTANCE && !FileIndexUtil.isJavaSourceFile(project, file)) {
      return PlatformIcons.JAVA_OUTSIDE_SOURCE_ICON;
    }

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PsiClassOwner && psiFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE) {
      PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
      if (classes.length > 0) {
        // prefer icon of the class named after file
        String fileName = file.getNameWithoutExtension();
        for (PsiClass aClass : classes) {
          if (aClass instanceof SyntheticElement) {
            return icon;
          }
          if (Comparing.strEqual(aClass.getName(), fileName)) {
            return aClass.getIcon(flags);
          }
        }
        return classes[classes.length - 1].getIcon(flags);
      }
    }
    return icon;
  }
}
