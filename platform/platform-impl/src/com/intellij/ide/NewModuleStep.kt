// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.ui.layout.migLayout.patched.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import java.io.File
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel

abstract class NewModuleStep(context: WizardContext) : ModuleWizardStep() {

  protected open val steps = listOf<NewProjectWizardStep<*>>(Step(context))

  final override fun getPreferredFocusedComponent() = panel.preferredFocusedComponent

  final override fun getComponent() = panel

  final override fun updateDataModel() {
    panel.apply()
  }

  private val panel by lazy {
    panel { steps.forEach { it.setupUI(this) } }
      .also { it.withBorder(JBUI.Borders.empty(10, 10)) }
      .also { fixUiShiftingWhenChoosingMultiStep(it) }
  }

  private fun fixUiShiftingWhenChoosingMultiStep(panel: DialogPanel) {
    val labels = UIUtil.uiTraverser(panel)
      .filterIsInstance<JLabel>()
      .filter { it.parent is DialogPanel }
      .filter { it.getGapBefore() == null }
    val width = labels.maxOf { it.preferredSize.width }
    labels.forEach { it.setMinimumWidth(width) }
  }

  private fun JComponent.getConstraints(): CC? {
    val layout = parent.layout as? MigLayout ?: return null
    return layout.getComponentConstraints()[this]
  }

  private fun JComponent.getGapBefore(): BoundSize? {
    return getConstraints()?.horizontal?.gapBefore
  }

  private fun JComponent.setMinimumWidth(width: Int) {
    minimumSize = minimumSize.apply { this.width = width }
  }

  fun setupProject(project: Project) {
    steps.forEach { it.setupProject(project) }
  }

  open class Step(private val context: WizardContext) : NewProjectWizardStep<Settings> {
    override val settings = Settings(context)

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        row(UIBundle.message("label.project.wizard.new.project.name")) {
          textField(settings.nameProperty)
            .constraints(pushX)
            .focused()
          installNameGenerators(getBuilderId(), settings.nameProperty)
        }.largeGapAfter()
        row(UIBundle.message("label.project.wizard.new.project.location")) {
          val uiPathProperty = settings.pathProperty.transform(::getUiPath, ::getModelPath)
          textFieldWithBrowseButton(uiPathProperty,
            UIBundle.message("dialog.title.project.name"), context.project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor())
        }.largeGapAfter()
        row("") {
          checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), settings.gitProperty)
        }.largeGapAfter()

        onGlobalApply {
          context.projectName = settings.name
          context.setProjectFileDirectory(settings.projectPath, false)
        }
      }
    }

    private fun getUiPath(path: String): String {
      return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
    }

    private fun getModelPath(path: String, removeLastSlash: Boolean = true): String {
      return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
    }

    private fun getBuilderId(): String? {
      val projectBuilder = context.projectBuilder
      if (projectBuilder is NewWizardModuleBuilder) {
        return projectBuilder.builderId
      }
      return null
    }

    override fun setupProject(project: Project) {
    }
  }

  class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context, PropertyGraph("New Project Wizard")) {
    val nameProperty = propertyGraph.graphProperty { suggestName(context) }
    val pathProperty = propertyGraph.graphProperty { context.projectFileDirectory }
    val gitProperty = propertyGraph.graphProperty { false }

    var name by nameProperty
    var path by pathProperty
    var git by gitProperty

    val projectPath: Path get() = Path.of(path, name)

    private fun suggestName(context: WizardContext): String {
      val moduleNames = findAllModules(context).map { it.name }.toSet()
      return FileUtil.createSequentFileName(File(path), "untitled", "") {
        !it.exists() && it.name !in moduleNames
      }
    }

    private fun findAllModules(context: WizardContext): List<Module> {
      val project = context.project ?: return emptyList()
      val moduleManager = ModuleManager.getInstance(project)
      return moduleManager.modules.toList()
    }

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)

      fun getNameProperty(context: WizardContext) = KEY.get(context).nameProperty
      fun getPathProperty(context: WizardContext) = KEY.get(context).pathProperty
      fun getName(context: WizardContext) = KEY.get(context).name
      fun getPath(context: WizardContext) = KEY.get(context).projectPath
    }
  }

  companion object {
    fun RowBuilder.twoColumnRow(column1: InnerCell.() -> Unit, column2: InnerCell.() -> Unit): Row = row {
      cell {
        column1()
      }
      cell {
        column2()
      }
      placeholder().constraints(growX, pushX)
    }
  }
}
