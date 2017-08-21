/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectView.actions

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ui.configuration.ConfigureUnloadedModulesDialog

/**
 * @author nik
 */
class LoadUnloadModulesAction : DumbAwareAction("Load/Unload Modules...") {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && ModuleManager.getInstance(e.project!!).let {
      it.modules.size > 1 || it.unloadedModuleDescriptions.isNotEmpty()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selectedModuleName = e.getData(LangDataKeys.MODULE_CONTEXT)?.name ?: getSelectedUnloadedModuleName(e)
    ConfigureUnloadedModulesDialog(e.project!!, selectedModuleName).show()
  }

  private fun getSelectedUnloadedModuleName(e: AnActionEvent): String? {
    val project = e.project ?: return null
    val file = e.getData(LangDataKeys.VIRTUAL_FILE) ?: return null
    return ProjectRootsUtil.computeNameOfUnloadedModuleByContentRoot(file, project)
  }
}