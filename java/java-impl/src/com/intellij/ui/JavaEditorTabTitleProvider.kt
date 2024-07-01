// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager

private class JavaEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val fileName = file.name
    if (PsiJavaModule.MODULE_INFO_FILE != fileName) {
      return null
    }

    return ReadAction.compute<String, RuntimeException> {
      val obj: Any? = PsiManager.getInstance(project).findFile(file)
      val javaFile = if (obj is PsiJavaFile) obj else null
      val moduleDescriptor = javaFile?.moduleDeclaration
      if (moduleDescriptor == null) {
        return@compute null
      }
      fileName + " (" + moduleDescriptor.name + ")"
    }
  }
}
