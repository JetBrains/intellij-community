// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.ui

import com.intellij.application.options.ModuleDescriptionsComboBox
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.util.JavaParametersUtil
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldWithBrowseButton
import org.jetbrains.annotations.Nls

abstract class DefaultJreSelector {
  companion object {
    @JvmStatic
    fun projectSdk(project: Project): DefaultJreSelector = ProjectSdkSelector(project)

    @JvmStatic
    fun fromModuleDependencies(moduleComboBox: ModulesComboBox, productionOnly: Boolean): DefaultJreSelector
        = SdkFromModuleDependencies(moduleComboBox, ModulesComboBox::getSelectedModule, {productionOnly})

    @JvmStatic
    fun fromModuleDependencies(moduleComboBox: ModuleDescriptionsComboBox, productionOnly: Boolean): DefaultJreSelector
        = SdkFromModuleDependencies(moduleComboBox, ModuleDescriptionsComboBox::getSelectedModule, {productionOnly})

    @JvmStatic
    fun fromModuleDependencies(moduleComboBox: ModuleClasspathCombo, productionOnly: Boolean): DefaultJreSelector
        = SdkFromModuleDependencies(moduleComboBox, ModuleClasspathCombo::getSelectedModule, {productionOnly})

    @JvmStatic
    fun fromSourceRootsDependencies(moduleComboBox: ModulesComboBox, classSelector: EditorTextFieldWithBrowseButton): DefaultJreSelector
        = SdkFromSourceRootDependencies(moduleComboBox, ModulesComboBox::getSelectedModule, classSelector.childComponent)

    @JvmStatic
    fun fromSourceRootsDependencies(moduleComboBox: ModuleDescriptionsComboBox, classSelector: EditorTextFieldWithBrowseButton): DefaultJreSelector
        = SdkFromSourceRootDependencies(moduleComboBox, ModuleDescriptionsComboBox::getSelectedModule, classSelector.childComponent)

    @JvmStatic
    fun fromSourceRootsDependencies(moduleComboBox: ModuleClasspathCombo, classSelector: EditorTextField): DefaultJreSelector
        = SdkFromSourceRootDependencies(moduleComboBox, ModuleClasspathCombo::getSelectedModule, classSelector)
  }

  abstract fun getNameAndDescription(): Pair<String?, String>
  open fun getVersion(): String? = null
  open fun addChangeListener(listener: Runnable) {
  }

  @Nls
  fun getDescriptionString(): String {
    val (name, description) = getNameAndDescription()
    return " (${name ?: JavaBundle.message("no.jre.description")} - $description)"
  }


  class ProjectSdkSelector(val project: Project): DefaultJreSelector() {
    override fun getNameAndDescription(): Pair<String?, String> = Pair.create(ProjectRootManager.getInstance(project).projectSdkName, "project SDK")
    override fun getVersion(): String? = ProjectRootManager.getInstance(project).projectSdk?.versionString;
  }

  open class SdkFromModuleDependencies<T: ComboBox<*>>(val moduleComboBox: T, val getSelectedModule: (T) -> Module?, val productionOnly: () -> Boolean): DefaultJreSelector() {
    override fun getNameAndDescription(): Pair<String?, String> {
      val module = getSelectedModule(moduleComboBox) ?: return Pair.create(null, "module not specified")

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

    override fun getVersion(): String? {
      val module = getSelectedModule(moduleComboBox) ?: return null
      return JavaParameters.getJdkToRunModule(module, productionOnly())?.versionString
    }

    override fun addChangeListener(listener: Runnable) {
      moduleComboBox.addActionListener { listener.run() }
    }
  }

  class SdkFromSourceRootDependencies<T: ComboBox<*>>(moduleComboBox: T, getSelectedModule: (T) -> Module?, val classSelector: EditorTextField)
  : SdkFromModuleDependencies<T>(moduleComboBox, getSelectedModule, { isClassInProductionSources(moduleComboBox, getSelectedModule, classSelector) }) {
    override fun addChangeListener(listener: Runnable) {
      super.addChangeListener(listener)
      classSelector.addDocumentListener(object : DocumentListener {
        override fun documentChanged(e: DocumentEvent) {
          listener.run()
        }
      })
    }
  }
}

private fun <T: ComboBox<*>> isClassInProductionSources(moduleSelector: T, getSelectedModule: (T) -> Module?, classSelector: EditorTextField): Boolean {
  val module = getSelectedModule(moduleSelector) ?: return false
  return JavaParametersUtil.isClassInProductionSources(classSelector.text, module) ?: false
}
