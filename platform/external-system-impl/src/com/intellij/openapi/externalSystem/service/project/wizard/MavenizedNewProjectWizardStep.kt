// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.wizard

import com.intellij.icons.AllIcons
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Maven.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Maven.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Maven.logVersionChanged
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.GROUP_ID_PROPERTY_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.CHECK_ARTIFACT_ID
import com.intellij.openapi.ui.validation.CHECK_GROUP_ID
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.text.nullize
import java.util.Comparator.comparing
import java.util.function.Function
import javax.swing.JList

abstract class MavenizedNewProjectWizardStep<Data : Any, ParentStep>(
  protected val parentStep: ParentStep
) : AbstractNewProjectWizardStep(parentStep), MavenizedNewProjectWizardData<Data>
  where ParentStep : NewProjectWizardStep,
        ParentStep : NewProjectWizardBaseData {

  abstract fun createView(data: Data): DataView<Data>

  abstract fun findAllParents(): List<Data>

  final override val parentProperty = propertyGraph.lazyProperty(::suggestParentByPath)
  final override val groupIdProperty = propertyGraph.lazyProperty(::suggestGroupIdByParent)
  final override val artifactIdProperty = propertyGraph.lazyProperty(::suggestArtifactIdByName)
  final override val versionProperty = propertyGraph.lazyProperty(::suggestVersionByParent)

  final override var parent by parentProperty
  final override var groupId by groupIdProperty
  final override var artifactId by artifactIdProperty
  final override var version by versionProperty

  val parents by lazy { parentsData.map(::createView) }
  val parentsData by lazy { findAllParents() }
  final override var parentData: Data?
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

  protected fun setupParentsUI(builder: Panel) {
    if (parents.isNotEmpty()) {
      builder.row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.parent.label")) {
        val presentationName = Function<DataView<Data>, String> { it.presentationName }
        val parentComboBoxModel = SortedComboBoxModel(comparing(presentationName, String.CASE_INSENSITIVE_ORDER))
        parentComboBoxModel.add(EMPTY_VIEW)
        parentComboBoxModel.addAll(parents)
        comboBox(parentComboBoxModel, ParentRenderer())
          .bindItem(parentProperty)
          .columns(COLUMNS_MEDIUM)
          .whenItemSelectedFromUi { logParentChanged(!parent.isPresent) }
          .onApply { logParentFinished(!parent.isPresent) }
      }.bottomGap(BottomGap.SMALL)
    }
  }

  protected fun setupGroupIdUI(builder: Panel) {
    builder.row {
      layout(RowLayout.LABEL_ALIGNED)
      label(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.label"))
        .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
        .applyToComponent { icon = AllIcons.General.ContextHelp }
        .applyToComponent { toolTipText = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.help") }
      textField()
        .bindText(groupIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_GROUP_ID)
        .validationInfo { validateGroupId() }
        .whenTextChangedFromUi { logGroupIdChanged() }
        .onApply {
          setPersistentValue(GROUP_ID_PROPERTY_NAME, groupId)
        }
    }
  }

  protected fun setupArtifactIdUI(builder: Panel) {
    builder.row {
      layout(RowLayout.LABEL_ALIGNED)
      label(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.label"))
        .applyToComponent { horizontalTextPosition = JBLabel.LEFT }
        .applyToComponent { icon = AllIcons.General.ContextHelp }
        .applyToComponent { toolTipText = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.help", context.presentationName) }
      textField()
        .bindText(artifactIdProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_ARTIFACT_ID)
        .validationInfo { validateArtifactId() }
        .validationRequestor(WHEN_PROPERTY_CHANGED(artifactIdProperty))
        .validationRequestor(WHEN_PROPERTY_CHANGED(parentStep.nameProperty))
        .whenTextChangedFromUi { logArtifactIdChanged() }
    }
  }

  protected fun setupVersionUI(builder: Panel) {
    builder.row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
      textField()
        .bindText(versionProperty.trim())
        .columns(COLUMNS_MEDIUM)
        .trimmedTextValidation(CHECK_NON_EMPTY)
        .whenTextChangedFromUi { logVersionChanged() }
    }
  }

  protected abstract fun setupSettingsUI(builder: Panel)

  protected abstract fun setupAdvancedSettingsUI(builder: Panel)

  override fun setupUI(builder: Panel) {
    setupSettingsUI(builder)
    builder.collapsibleGroup(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
      setupAdvancedSettingsUI(this)
    }.topGap(TopGap.MEDIUM)
  }

  private fun suggestParentByPath(): DataView<Data> {
    val path = "${parentStep.path}/${parentStep.name}"
    return parents.find { FileUtil.isAncestor(it.location, path, true) } ?: EMPTY_VIEW
  }

  protected open fun suggestNameByArtifactId(): String {
    return artifactId
  }

  protected open fun suggestGroupIdByParent(): String {
    return parent.groupId.nullize() ?: EMPTY_VIEW.groupId
  }

  protected open fun suggestArtifactIdByName(): String {
    return parentStep.name
  }

  protected open fun suggestVersionByParent(): String {
    return parent.version
  }

  private fun suggestPathByParent(): String {
    return if (parent.isPresent) parent.location else context.projectFileDirectory
  }

  protected open fun ValidationInfoBuilder.validateGroupId(): ValidationInfo? = null

  protected open fun ValidationInfoBuilder.validateArtifactId(): ValidationInfo? = null

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
      override val groupId: String get() = getPersistentValue(GROUP_ID_PROPERTY_NAME, "org.example")
      override val version: String = "1.0-SNAPSHOT"

      override val isPresent: Boolean = false
    }

    private fun getPersistentValue(property: String, defaultValue: String): String {
      return PropertiesComponent.getInstance().getValue(property, defaultValue)
    }

    private fun setPersistentValue(property: String, value: String?) {
      return PropertiesComponent.getInstance().setValue(property, value)
    }
  }
}
