// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloaderBase
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.concurrency.runAsync
import java.awt.BorderLayout
import java.awt.Component
import java.util.*
import javax.accessibility.AccessibleContext
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JList

/**
 * Represents an intent to set up a JDK:
 *
 * - [NoJdk] - Create the project without selecting a JDK.
 * - [DownloadJdk] - Download a JDK on project creation.
 * - [ExistingJdk] - Use a JDK already configured, or from a location selected by the user.
 * - [DetectedJdk] - Configure a JDK detected by the IDE.
 */
sealed class ProjectWizardJdkIntent {
  data object NoJdk : ProjectWizardJdkIntent()

  data class DownloadJdk(val task: SdkDownloadTask) : ProjectWizardJdkIntent()

  data class ExistingJdk(val jdk: Sdk) : ProjectWizardJdkIntent()

  data object AddJdkFromPath : ProjectWizardJdkIntent()

  data class AddJdkFromJdkListDownloader(val extension: SdkDownload) : ProjectWizardJdkIntent()

  data class DetectedJdk(val version: @NlsSafe String, val home: @NlsSafe String) : ProjectWizardJdkIntent()
}

fun Row.projectWizardJdkComboBox(
  sdkProperty: GraphProperty<Sdk?>,
  sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?>,
  sdkPropertyId: String,
) {
  val combo = ProjectWizardJdkComboBox()

  val selectedJdkProperty = "jdk.selected.$sdkPropertyId"

  cell(combo)
    .columns(COLUMNS_LARGE)
    .apply {
      val commentCell = comment("")
      component.addItemListener {
        commentCell.comment?.let { it.text = component.comment }
      }
    }
    .validationOnApply {
      val intent = it.selectedItem
      if (intent !is DownloadJdk) { null }
      else {
        when (JdkInstaller.getInstance().validateInstallDir(intent.task.plannedHomeDir).first) {
          null -> error(JavaUiBundle.message("jdk.location.error", intent.task.plannedHomeDir))
          else -> null
        }
      }
    }
    .onChanged {
      updateGraphProperties(combo, sdkProperty, sdkDownloadTaskProperty, selectedJdkProperty)
    }

  val lastSelected = PropertiesComponent.getInstance().getValue(selectedJdkProperty)
  if (lastSelected != null) {
    combo.selectedItem = lastSelected
  } else {
    combo.registered
      .maxByOrNull { JavaSdkVersion.fromVersionString(it.jdk.versionString ?: "")?.ordinal ?: 0 }
      ?.let { combo.selectedItem = it }
  }

  updateGraphProperties(combo, sdkProperty, sdkDownloadTaskProperty, selectedJdkProperty)
}

private fun updateGraphProperties(
  combo: ProjectWizardJdkComboBox,
  sdkProperty: GraphProperty<Sdk?>,
  sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?>,
  selectedJdkProperty: String,
) {
  val stateComponent = PropertiesComponent.getInstance()
  val (sdk, downloadTask) = when (val intent = combo.selectedItem) {
    is ExistingJdk -> {
      stateComponent.setValue(selectedJdkProperty, intent.jdk.name)
      (intent.jdk to null)
    }
    is DownloadJdk -> (null to intent.task)
    else -> (null to null)
  }
  sdkProperty.set(sdk)
  sdkDownloadTaskProperty.set(downloadTask)
}

class ProjectWizardJdkComboBox(): ComboBox<ProjectWizardJdkIntent>() {
  val registered: MutableList<ExistingJdk> = mutableListOf()
  val detectedJDKs: MutableList<DetectedJdk> = mutableListOf()
  var isUpdating: Boolean = true
  val progressIcon: JBLabel = JBLabel(AnimatedIcon.Default.INSTANCE)

  init {
    model = DefaultComboBoxModel(Vector(initialItems()))

    if (registered.isEmpty()) {
      runAsync { addDownloadOpenJdkIntent() }
    }
    runAsync { addExistingJdks() }

    isSwingPopup = false
    ClientProperty.put(this, ANIMATION_IN_RENDERER_ALLOWED, true)
    renderer = object : GroupedComboBoxRenderer<ProjectWizardJdkIntent>(this) {
      override fun separatorFor(value: ProjectWizardJdkIntent): ListSeparator? {
        return when (value) {
          registered.firstOrNull() -> ListSeparator(JavaUiBundle.message("jdk.registered.items"))
          is AddJdkFromJdkListDownloader -> ListSeparator("")
          detectedJDKs.firstOrNull() -> ListSeparator(JavaUiBundle.message("jdk.detected.items"))
          else -> null
        }
      }

      override fun customize(item: SimpleColoredComponent, value: ProjectWizardJdkIntent, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
        item.icon = getIcon(value)

        when (value) {
          is NoJdk -> item.append(JavaUiBundle.message("jdk.missing.item"), SimpleTextAttributes.ERROR_ATTRIBUTES)
          is ExistingJdk -> {
            item.append(value.jdk.name)
            val version = value.jdk.versionString ?: (value.jdk.sdkType as SdkType).presentableName
            item.append(" $version", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
          is DownloadJdk -> {
            item.append(JavaUiBundle.message("jdk.download.predefined.item", value.task.suggestedSdkName))
            item.append(" ${value.task.plannedVersion}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }

          is AddJdkFromJdkListDownloader -> item.append(JavaUiBundle.message("jdk.download.item"))
          is AddJdkFromPath -> item.append(JavaUiBundle.message("jdk.add.item"))

          is DetectedJdk -> {
            item.append(value.version)
            item.append(" ${value.home}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
        }
      }

      override fun getIcon(item: ProjectWizardJdkIntent): Icon? {
        return when (item) {
          is DownloadJdk, is AddJdkFromJdkListDownloader -> AllIcons.Actions.Download
          is AddJdkFromPath -> AllIcons.Nodes.PpJdk
          is ExistingJdk, is DetectedJdk -> JavaSdk.getInstance().icon
          else -> EmptyIcon.ICON_16
        }
      }

      override fun getListCellRendererComponent(list: JList<out ProjectWizardJdkIntent>?,
                                                value: ProjectWizardJdkIntent,
                                                index: Int,
                                                isSelected: Boolean,
                                                cellHasFocus: Boolean): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (index != -1 || !isUpdating) return component
        else {
          val panel = object : CellRendererPanel(BorderLayout()) {
            override fun getAccessibleContext(): AccessibleContext = component.accessibleContext
          }
          component.background = null
          panel.add(component, BorderLayout.CENTER)
          panel.add(progressIcon, BorderLayout.EAST)
          return panel
        }
      }
    }
  }

  fun initialItems(): MutableList<ProjectWizardJdkIntent> {
    val items = mutableListOf<ProjectWizardJdkIntent>()

    // Add JDKs from the ProjectJdkTable
    registered.addAll(
      ProjectJdkTable.getInstance().allJdks
        .filter { jdk ->
          jdk.sdkType is JavaSdkType && jdk.sdkType !is DependentSdkType
        }
        .map { ExistingJdk(it) }
    )

    if (registered.isNotEmpty()) {
      items.addAll(registered)
    }
    else {
      items.add(NoJdk)
    }

    // Add options to download or select a JDK
    SdkDownload.EP_NAME.findFirstSafe { it.supportsDownload(JavaSdk.getInstance()) }?.let {
      items.add(AddJdkFromJdkListDownloader(it))
    }
    items.add(AddJdkFromPath)

    return items
  }

  fun addDownloadOpenJdkIntent() {
    JdkListDownloader.getInstance()
      .downloadModelForJdkInstaller(null)
      .filter { it.matchesVendor("openjdk") }
      .filter { CpuArch.fromString(it.arch) == CpuArch.CURRENT }
      .maxByOrNull { it.jdkMajorVersion }
      ?.let {
        val jdkInstaller = JdkInstaller.getInstance()
        val request = jdkInstaller.prepareJdkInstallation(it, jdkInstaller.defaultInstallDir(it))
        val task = JdkDownloaderBase.newDownloadTask(it, request, null)
        insertItemAt(DownloadJdk(task), 1)
        if (selectedItem is NoJdk) selectedIndex = 1
      }
  }

  fun addExistingJdks() {
    val javaSdk = JavaSdk.getInstance()

    JdkFinder.getInstance().suggestHomePaths().forEach { homePath: String ->
      val version = javaSdk.getVersionString(homePath)
      if (version != null && javaSdk.isValidSdkHome(homePath)) {
        val detected = DetectedJdk(version, homePath)
        detectedJDKs.add(detected)
        addItem(detected)
      }
    }

    if ((selectedItem is NoJdk || selectedItem is DownloadJdk) && detectedJDKs.any()) {
      val regex = "(\\d+)".toRegex()
      detectedJDKs
        .maxBy { regex.find(it.version)?.value?.toInt() ?: 0 }
        .let { selectedItem = it }
    }
    isUpdating = false
  }

  override fun setSelectedItem(anObject: Any?) {
    val toSelect = when (anObject) {
      is String -> {
        registered.firstOrNull { it.jdk.name == anObject } ?: selectedItem
      }
      is AddJdkFromJdkListDownloader -> {
        addDownloadItem(anObject.extension, this)
        selectedItem
      }
      is AddJdkFromPath -> {
        addJdk(this)
        selectedItem
      }
      else -> anObject
    }
    super.setSelectedItem(toSelect)
  }

  val comment: String?
    get() = when (selectedItem) {
      is DownloadJdk -> JavaUiBundle.message("jdk.download.comment")
      else -> null
    }
}

private fun addJdk(combo: ComboBox<ProjectWizardJdkIntent>) {
  combo.popup?.hide()
  SdkConfigurationUtil.selectSdkHome(JavaSdk.getInstance()) { path: String ->
    ApplicationManager.getApplication().invokeLater {
      SdkConfigurationUtil.createAndAddSDK(path, JavaSdk.getInstance())?.let {
        combo.insertItemAt(ExistingJdk(it), 0)
        combo.selectedIndex = 0
      }
    }
  }
}

private fun addDownloadItem(extension: SdkDownload, combo: ComboBox<ProjectWizardJdkIntent>) {
  val config = ProjectStructureConfigurable.getInstance(DefaultProjectFactory.getInstance().defaultProject)
  combo.popup?.hide()
  extension.showDownloadUI(JavaSdk.getInstance(), config.projectJdksModel, combo, null) { task: SdkDownloadTask ->
    val index = when {
      combo.getItemAt(0) is NoJdk -> 1
      else -> 0
    }
    combo.insertItemAt(DownloadJdk(task), index)
    combo.selectedIndex = index
  }
}