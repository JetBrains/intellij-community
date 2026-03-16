// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.PsiManager

internal class JavaEditorTabTitleProvider : EditorTabTitleProvider {
  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): String? {
    val fileName = file.name
    if (PsiJavaModule.MODULE_INFO_FILE != fileName) {
      return null
    }

    val psiManager = project.serviceAsync<PsiManager>()
    return readAction {
      val moduleDescriptor = (psiManager.findFile(file) as? PsiJavaFile)?.moduleDeclaration ?: return@readAction null
      "$fileName (${moduleDescriptor.name})"
    }
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val fileName = file.name
    if (PsiJavaModule.MODULE_INFO_FILE != fileName) {
      return null
    }

    val psiManager = PsiManager.getInstance(project)
    return ReadAction.compute<String, RuntimeException> {
      val moduleDescriptor = (psiManager.findFile(file) as? PsiJavaFile)?.moduleDeclaration ?: return@compute null
      "$fileName (${moduleDescriptor.name})"
    }
  }
}
