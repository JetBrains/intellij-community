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
package com.intellij.execution.ui

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.ui.EditorTextFieldWithBrowseButton

/**
 * @author nik
 */

abstract class DefaultJreSelector {
  companion object {
    @JvmStatic
    fun projectSdk(project: Project): DefaultJreSelector = ProjectSdkSelector(project)

    @JvmStatic
    fun fromModuleDependencies(moduleComboBox: ModulesComboBox, productionOnly: Boolean): DefaultJreSelector
        = SdkFromModuleDependencies(moduleComboBox, {productionOnly})

    @JvmStatic
    fun fromSourceRootsDependencies(moduleComboBox: ModulesComboBox, classSelector: EditorTextFieldWithBrowseButton): DefaultJreSelector
        = SdkFromSourceRootDependencies(moduleComboBox, classSelector)
  }

  abstract fun getNameAndDescription(): Pair<String?, String>
  open fun addChangeListener(listener: Runnable) {
  }

  fun getDescriptionString(): String {
    val (name, description) = getNameAndDescription()
    return " (${name ?: "<no JRE>"} - $description)"
  }


  class ProjectSdkSelector(val project: Project): DefaultJreSelector() {
    override fun getNameAndDescription() = Pair.create(ProjectRootManager.getInstance(project).projectSdkName, "project SDK")
  }

  open class SdkFromModuleDependencies(val moduleComboBox: ModulesComboBox, val productionOnly: () -> Boolean): DefaultJreSelector() {
    override fun getNameAndDescription(): Pair<String?, String> {
      val module = moduleComboBox.selectedModule ?: return Pair.create(null, "module not specified")

      val productionOnly = productionOnly()
      val jdkToRun = JavaParameters.getJdkToRunModule(module, productionOnly)
      val moduleJdk = ModuleRootManager.getInstance(module).sdk
      if (moduleJdk == null || jdkToRun == null) {
        return Pair.create(null, "module not specified")
      }
      if (moduleJdk.homeDirectory == jdkToRun.homeDirectory) {
        return Pair.create(moduleJdk.name, "SDK of '${module.name}' module")
      }
      return Pair.create(jdkToRun.name, "newest SDK from '${module.name}' module${if (productionOnly) "" else " test"} dependencies")
    }

    override fun addChangeListener(listener: Runnable) {
      moduleComboBox.addActionListener { listener.run() }
    }
  }

  class SdkFromSourceRootDependencies(moduleComboBox: ModulesComboBox, val classSelector: EditorTextFieldWithBrowseButton)
  : SdkFromModuleDependencies(moduleComboBox, { isClassInProductionSources(moduleComboBox, classSelector) }) {
    override fun addChangeListener(listener: Runnable) {
      super.addChangeListener(listener)
      classSelector.childComponent.addDocumentListener(object : DocumentAdapter() {
        override fun documentChanged(e: DocumentEvent?) {
          listener.run()
        }
      })
    }
  }

}

private fun isClassInProductionSources(moduleSelector: ModulesComboBox, classSelector: EditorTextFieldWithBrowseButton): Boolean {
  val module = moduleSelector.selectedModule ?: return false
  return JavaParametersUtil.isClassInProductionSources(classSelector.text, module) ?: false
}
