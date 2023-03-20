// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.wizard

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ui.DataView
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleLocalFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil.*
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SortedComboBoxModel
import com.intellij.ui.layout.*
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.Comparator.comparing
import java.util.function.Function
import javax.swing.JList
import javax.swing.JTextField
import javax.swing.ListCellRenderer

abstract class MavenizedStructureWizardStep<Data : Any>(val context: WizardContext) : ModuleWizardStep() {
  abstract fun createView(data: Data): DataView<Data>

  abstract fun findAllParents(): List<Data>

  private val propertyGraph = PropertyGraph()
  private val entityNameProperty = propertyGraph.lazyProperty(::suggestName)
  private val locationProperty = propertyGraph.lazyProperty(::suggestLocationByName)
  private val parentProperty = propertyGraph.lazyProperty(::suggestParentByLocation)
  private val groupIdProperty = propertyGraph.lazyProperty(::suggestGroupIdByParent)
  private val artifactIdProperty = propertyGraph.lazyProperty(::suggestArtifactIdByName)
  private val versionProperty = propertyGraph.lazyProperty(::suggestVersionByParent)

  var entityName by entityNameProperty.trim()
  var location by locationProperty
  var parent by parentProperty
  var groupId by groupIdProperty.trim()
  var artifactId by artifactIdProperty.trim()
  var version by versionProperty.trim()

  val parents by lazy { parentsData.map(::createView) }
  val parentsData by lazy { findAllParents() }
  var parentData: Data?
    get() = DataView.getData(parent)
    set(value) {
      parent = if (value == null) EMPTY_VIEW else createView(value)
    }

  init {
    entityNameProperty.dependsOn(locationProperty, ::suggestNameByLocation)
    entityNameProperty.dependsOn(artifactIdProperty, ::suggestNameByArtifactId)
    parentProperty.dependsOn(locationProperty, ::suggestParentByLocation)
    locationProperty.dependsOn(parentProperty, ::suggestLocationByParentAndName)
    locationProperty.dependsOn(entityNameProperty, ::suggestLocationByParentAndName)
    groupIdProperty.dependsOn(parentProperty, ::suggestGroupIdByParent)
    artifactIdProperty.dependsOn(entityNameProperty, ::suggestArtifactIdByName)
    versionProperty.dependsOn(parentProperty, ::suggestVersionByParent)
  }

  private val contentPanel by lazy {
    panel {
      if (!context.isCreatingNewProject) {
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.parent.label")) {
          val presentationName = Function<DataView<Data>, String> { it.presentationName }
          val parentComboBoxModel = SortedComboBoxModel(comparing(presentationName, String.CASE_INSENSITIVE_ORDER))
          parentComboBoxModel.add(EMPTY_VIEW)
          parentComboBoxModel.addAll(parents)
          comboBox(parentComboBoxModel, parentProperty, renderer = getParentRenderer())
        }
      }
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.name.label")) {
        textField(entityNameProperty)
          .withValidationOnApply { validateName() }
          .withValidationOnInput { validateName() }
          .constraints(pushX)
          .focused()
        installNameGenerators(getBuilderId(), entityNameProperty)
      }
      row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.location.label")) {
        val fileChooserDescriptor = createSingleLocalFileDescriptor()
          .withFileFilter { it.isDirectory }
          .withPathToTextConvertor(::getPresentablePath)
          .withTextToPathConvertor(::getCanonicalPath)
        val title = IdeBundle.message("title.select.project.file.directory", context.presentationName)
        val property = locationProperty.transform(::getPresentablePath, ::getCanonicalPath)
        textFieldWithBrowseButton(property, title, context.project, fileChooserDescriptor)
          .withValidationOnApply { validateLocation() }
          .withValidationOnInput { validateLocation() }
      }
      hideableRow(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.coordinates.title")) {
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.label")) {
          textField(groupIdProperty)
            .withValidationOnApply { validateGroupId() }
            .withValidationOnInput { validateGroupId() }
            .comment(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.help"))
        }
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.label")) {
          textField(artifactIdProperty)
            .withValidationOnApply { validateArtifactId() }
            .withValidationOnInput { validateArtifactId() }
            .comment(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.help", context.presentationName))
        }
        row(ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.label")) {
          textField(versionProperty)
            .withValidationOnApply { validateVersion() }
            .withValidationOnInput { validateVersion() }
        }
      }
    }.apply {
      registerValidators(context.disposable)
    }
  }

  protected open fun getBuilderId(): String? = null

  override fun getPreferredFocusedComponent() = contentPanel.preferredFocusedComponent

  override fun getComponent() = contentPanel

  override fun updateStep() = (preferredFocusedComponent as JTextField).selectAll()

  private fun getParentRenderer(): ListCellRenderer<DataView<Data>?> {
    return object : SimpleListCellRenderer<DataView<Data>?>() {
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
  }

  protected open fun suggestName(): String {
    val projectFileDirectory = File(context.projectFileDirectory)
    val moduleNames = findAllModules().map { it.name }.toSet()
    return createSequentFileName(projectFileDirectory, "untitled", "") {
      !it.exists() && it.name !in moduleNames
    }
  }

  protected fun findAllModules(): List<Module> {
    val project = context.project ?: return emptyList()
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.modules.toList()
  }

  protected open fun suggestNameByLocation(): String {
    return File(location).name
  }

  protected open fun suggestNameByArtifactId(): String {
    return artifactId
  }

  protected open fun suggestLocationByParentAndName(): String {
    if (!parent.isPresent) return suggestLocationByName()
    return join(parent.location, entityName)
  }

  protected open fun suggestLocationByName(): String {
    return join(context.projectFileDirectory, entityName)
  }

  protected open fun suggestParentByLocation(): DataView<Data> {
    val location = location
    return parents.find { isAncestor(it.location, location, true) } ?: EMPTY_VIEW
  }

  protected open fun suggestGroupIdByParent(): String {
    return parent.groupId
  }

  protected open fun suggestArtifactIdByName(): String {
    return entityName
  }

  protected open fun suggestVersionByParent(): String {
    return parent.version
  }

  override fun validate(): Boolean {
    return contentPanel.validateCallbacks
      .asSequence()
      .mapNotNull { it() }
      .all { it.okEnabled }
  }

  protected open fun ValidationInfoBuilder.validateGroupId() = superValidateGroupId()
  protected fun ValidationInfoBuilder.superValidateGroupId(): ValidationInfo? {
    if (groupId.isEmpty()) {
      val propertyPresentation = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.group.id.presentation")
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.missing.error",
                                                 context.presentationName, propertyPresentation)
      return error(message)
    }
    return null
  }

  protected open fun ValidationInfoBuilder.validateArtifactId() = superValidateArtifactId()
  protected fun ValidationInfoBuilder.superValidateArtifactId(): ValidationInfo? {
    if (artifactId.isEmpty()) {
      val propertyPresentation = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.artifact.id.presentation")
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.missing.error",
                                                 context.presentationName, propertyPresentation)
      return error(message)
    }
    return null
  }

  protected open fun ValidationInfoBuilder.validateVersion() = superValidateVersion()
  private fun ValidationInfoBuilder.superValidateVersion(): ValidationInfo? {
    if (version.isEmpty()) {
      val propertyPresentation = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.version.presentation")
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.missing.error",
                                                 context.presentationName, propertyPresentation)
      return error(message)
    }
    return null
  }

  protected open fun ValidationInfoBuilder.validateName() = superValidateName()
  protected fun ValidationInfoBuilder.superValidateName(): ValidationInfo? {
    if (entityName.isEmpty()) {
      val propertyPresentation = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.name.presentation")
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.missing.error",
                                                 context.presentationName, propertyPresentation)
      return error(message)
    }
    val moduleNames = findAllModules().map { it.name }.toSet()
    if (entityName in moduleNames) {
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.entity.name.exists.error",
                                                 context.presentationName.capitalize(), entityName)
      return error(message)
    }
    return null
  }

  protected open fun ValidationInfoBuilder.validateLocation() = superValidateLocation()
  private fun ValidationInfoBuilder.superValidateLocation(): ValidationInfo? {
    val location = location
    if (location.isEmpty()) {
      val propertyPresentation = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.location.presentation")
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.missing.error",
                                                 context.presentationName, propertyPresentation)
      return error(message)
    }

    val locationPath = try {
      Paths.get(location)
    }
    catch (ex: InvalidPathException) {
      val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.directory.invalid", ex.reason)
      return error(message)
    }
    for (project in ProjectManager.getInstance().openProjects) {
      if (ProjectUtil.isSameProject(locationPath, project)) {
        val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.directory.already.taken.error", project.name)
        return error(message)
      }
    }

    val file = locationPath.toFile()
    if (file.exists()) {
      if (!file.canWrite()) {
        val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.directory.not.writable.error")
        return error(message)
      }
      val children = file.list()
      if (children == null) {
        val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.file.not.directory.error")
        return error(message)
      }
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        if (children.isNotEmpty()) {
          val message = ExternalSystemBundle.message("external.system.mavenized.structure.wizard.directory.not.empty.warning")
          return warning(message)
        }
      }
    }
    return null
  }

  override fun updateDataModel() {
    val location = location
    context.projectName = entityName
    context.setProjectFileDirectory(location)
    createDirectory(File(location))
    updateProjectData()
  }

  abstract fun updateProjectData()

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
