/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.util;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;


public class IconUtil {
  public static Icon getIcon(VirtualFile file, int flags, Project project) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);

    Icon icon = file.getIcon();

    Icon excludedIcon = null;
    Icon lockedIcon = null;
    if (project != null) {
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      final boolean isUnderSource = projectFileIndex.isJavaSourceFile(file);
      if (fileType == StdFileTypes.JAVA) {
        if (!isUnderSource) {
          icon = Icons.JAVA_OUTSIDE_SOURCE_ICON;
        }
        else {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile instanceof PsiJavaFile) {
            PsiClass[] classes = ((PsiJavaFile)psiFile).getClasses();
            if (classes.length != 0) {
              icon = classes[0].getIcon(flags);
            }
          }
        }
      }

      if (projectFileIndex.isInSource(file) && CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
        excludedIcon = Icons.EXCLUDED_FROM_COMPILE_ICON;
      }
    }

    if ((flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !file.isWritable()) {
      lockedIcon = Icons.LOCKED_ICON;
    }

    if (excludedIcon != null || lockedIcon != null) {
      LayeredIcon layeredIcon = new LayeredIcon(1 + (lockedIcon != null ? 1 : 0) + (excludedIcon != null ? 1 : 0));
      int layer = 0;
      layeredIcon.setIcon(icon, layer++);
      if (lockedIcon != null) {
        layeredIcon.setIcon(lockedIcon, layer++);
      }
      if (excludedIcon != null) {
        layeredIcon.setIcon(excludedIcon, layer);
      }
      icon = layeredIcon;
    }

    return icon;
  }
}
