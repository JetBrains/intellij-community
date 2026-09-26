// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.intellij.psi.SyntheticElement
import com.intellij.util.PlatformIcons
import javax.swing.Icon

internal class JavaFileIconPatcher : FileIconPatcher {
  override fun patchIcon(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
    if (project == null) {
      return icon
    }

    if (file.fileType === JavaFileType.INSTANCE && !FileIndexUtil.isJavaSourceFile(project, file)) {
      return PlatformIcons.JAVA_OUTSIDE_SOURCE_ICON
    }

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile !is PsiClassOwner || psiFile.getViewProvider().baseLanguage !== JavaLanguage.INSTANCE) {
      return icon
    }

    val classes = psiFile.classes
    if (classes.isEmpty()) {
      return icon
    }

    // prefer icon of the class named after file
    val fileName = file.nameWithoutExtension
    for (aClass in classes) {
      if (aClass is SyntheticElement) {
        return icon
      }
      if (aClass.name == fileName) {
        return aClass.getIcon(flags)
      }
    }
    return classes.last().getIcon(flags)
  }
}
