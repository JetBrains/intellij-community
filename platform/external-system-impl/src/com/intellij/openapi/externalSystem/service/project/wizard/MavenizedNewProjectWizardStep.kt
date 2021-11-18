// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.wizard

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.*
import com.intellij.util.io.systemIndependentPath
import java.util.Comparator.comparing
import java.util.function.Function
import javax.swing.JList

abstract class MavenizedNewProjectWizardStep<Data : Any, ParentStep>(val parentStep: ParentStep) :
  AbstractNewProjectWizardStep(parentStep)
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  abstract fun createView(data: Data): DataView<Data>

  abstract fun findAllParents(): List<Data>

  private val parentProperty = propertyGraph.graphProperty(::suggestParentByPath)
  private val groupIdProperty = propertyGraph.graphProperty(::suggestGroupIdByParent)
  private val artifactIdProperty = propertyGraph.graphProperty(::suggestArtifactIdByName)
  private val versionProperty = propertyGraph.graphProperty(::suggestVersionByParent)

  var parent by parentProperty
  var groupId by groupIdProperty.map { it.trim() }
  var artifactId by artifactIdProperty.map { it.trim() }
  var version by versionProperty.map { it.trim() }

  val parents by lazy { parentsData.map(::createView) }
  val parentsData by lazy { findAllParents() }
  var parentData: Data?
    get() = DataView.getData(parent)
    set(value) {
      parent = if (value == null) EMPTY_VIEW else createView(value)
    }

  init {
    parentStep.nameProperty.dependsOn(artifactIdProperty, ::suggestNameByArtifactId)
    parentProperty.dependsOn(parentStep.pathProperty, ::suggestParentByPath)
    parentStep.pathProperty.dependsOn(parentProperty, ::suggestPathByParent)
    groupIdProperty.dependsOn(parentProperty, ::suggestGroupIdByParent)
    artifactIdProperty.dependsOn(parentStep.nameProperty, ::suggestArtifactIdByName)
    versionProperty.dependsOn(parentProperty, ::suggestVersionByParent)
  }

  protected open fun setupAdvancedSettingsUI(panel: Panel) {
    with(panel) {
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.label")) {
        textField()
          .bindText(groupIdProperty)
          .columns(COLUMNS_MEDIUM)
          .validationOnApply { validateGroupId() }
          .validationOnInput { validateGroupId() }
      }.bottomGap(BottomGap.SMALL)
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.label")) {
        textField()
          .bindText(artifactIdProperty)
          .columns(COLUMNS_MEDIUM)
          .validationOnApply { validateArtifactId() }
          .validationOnInput { validateArtifactId() }
      }.bottomGap(BottomGap.SMALL)
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
        textField()
          .bindText(versionProperty)
          .columns(COLUMNS_MEDIUM)
          .validationOnApply { validateVersion() }
          .validationOnInput { validateVersion() }
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun setupUI(builder: Panel) {
    with(builder) {
      if (parents.isNotEmpty()) {
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.parent.label")) {
          val presentationName = Function<DataView<Data>, String> { it.presentationName }
          val parentComboBoxModel = SortedComboBoxModel(comparing(presentationName, String.CASE_INSENSITIVE_ORDER))
          parentComboBoxModel.add(EMPTY_VIEW)
          parentComboBoxModel.addAll(parents)
          comboBox(parentComboBoxModel, ParentRenderer())
            .bindItem(parentProperty)
            .columns(COLUMNS_MEDIUM)
        }.topGap(TopGap.SMALL)
      }
      collapsibleGroup(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.advanced.settings.title")) {
        setupAdvancedSettingsUI(this)
      }.topGap(TopGap.MEDIUM)
    }
  }

  protected fun findAllModules(): List<Module> {
    val project = context.project ?: return emptyList()
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.modules.toList()
  }

  private fun suggestParentByPath(): DataView<Data> {
    val path = parentStep.projectPath.systemIndependentPath
    return parents.find { FileUtil.isAncestor(it.location, path, true) } ?: EMPTY_VIEW
  }

  protected open fun suggestNameByArtifactId(): String {
    return artifactId
  }

  protected open fun suggestGroupIdByParent(): String {
    return parent.groupId
  }

  protected open fun suggestArtifactIdByName(): String {
    return parentStep.name
  }

  protected open fun suggestVersionByParent(): String {
    return parent.version
  }

  protected fun suggestPathByParent(): String {
    return if (parent.isPresent) parent.location else context.projectFileDirectory
  }

  protected open fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? = null

  protected open fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? = null

  protected open fun ValidationInfoBuilder.validateVersion(): ValidationInfo? = null

  private class ParentRenderer<Data : Any> : SimpleListCellRenderer<DataView<Data>?>() {
    override fun customize(list: JList<out DataView<Data>?>,
                           value: DataView<Data>?,
                           index: Int,
                           selected: Boolean,
                           hasFocus: Boolean) {
      val view = value ?: EMPTY_VIEW
      text = view.presentationName
      icon = DataView.getIcon(view)
    }
  }

  companion object {
    private val EMPTY_VIEW = object : DataView<Nothing>() {
      override val data: Nothing get() = throw UnsupportedOperationException()
      override val location: String = ""
      override val icon: Nothing get() = throw UnsupportedOperationException()
      override val presentationName: String = "<None>"
      override val groupId: String = "org.example"
      override val version: String = "1.0-SNAPSHOT"

      override val isPresent: Boolean = false
    }
  }
}
