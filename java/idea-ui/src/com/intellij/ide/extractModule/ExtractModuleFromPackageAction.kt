// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.extractModule

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiManager
import java.nio.file.Path

class ExtractModuleFromPackageAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile) ?: return
    val directory = PsiManager.getInstance(module.project).findDirectory(virtualFile) ?: return
    val suggestedModuleName = "${module.name}.${directory.name}"
    val parentContentRoot = ModuleRootManager.getInstance(module).contentRoots.first()
    val dialog = ExtractModuleFromPackageDialog(project, suggestedModuleName,
                                                Path.of(parentContentRoot.path, directory.name, "src").toString())
    if (!dialog.showAndGet()) return

    project.service<ExtractModuleService>()
      .analyzeDependenciesAndCreateModuleInBackground(directory, module, dialog.moduleName, dialog.targetSourceRootPath)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = project != null && file != null && file.isDirectory
                                         && ProjectFileIndex.getInstance(project).isInSourceContent(file)
  }
}
