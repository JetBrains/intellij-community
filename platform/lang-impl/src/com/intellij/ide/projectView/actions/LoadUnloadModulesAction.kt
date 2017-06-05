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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ui.configuration.ConfigureUnloadedModulesDialog
import com.intellij.openapi.util.registry.Registry

/**
 * @author nik
 */
class LoadUnloadModulesAction : DumbAwareAction("Load/Unload Modules...") {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && Registry.`is`("project.support.module.unloading")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selectedModules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY)
    ConfigureUnloadedModulesDialog(e.project!!, selectedModules).show()
  }
}