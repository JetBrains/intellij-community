// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.util.application
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.*
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
  context: WizardContext,
  sdkProperty: GraphProperty<Sdk?>,
  sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?>,
  sdkPropertyId: String,
  projectJdk: Sdk? = null
): Cell<ProjectWizardJdkComboBox> {
  val combo = ProjectWizardJdkComboBox(projectJdk, context.disposable)

  val selectedJdkProperty = "jdk.selected.$sdkPropertyId"

  return cell(combo)
    .columns(COLUMNS_LARGE)
    .apply {
      val commentCell = comment(component.comment, 50)
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
    .onApply {
      context.projectJdk = sdkProperty.get()

      when (val selected = combo.selectedItem) {
        is NoJdk -> JdkComboBoxCollector.noJdkSelected()
        is DownloadJdk -> JdkComboBoxCollector.jdkDownloaded((selected.task as JdkDownloadTask).jdkItem)
      }
    }
    .apply {
      val lastSelected = PropertiesComponent.getInstance().getValue(selectedJdkProperty)
      if (lastSelected != null) {
        combo.selectedItem = lastSelected
      } else {
        combo.registered
          .maxByOrNull { JavaSdkVersion.fromVersionString(it.jdk.versionString ?: "")?.ordinal ?: 0 }
          ?.let { combo.selectedItem = it }
      }
    }
    .apply {
      updateGraphProperties(combo, sdkProperty, sdkDownloadTaskProperty, selectedJdkProperty)
    }
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

@Service(Service.Level.APP)
internal class ProjectWizardJdkComboBoxService(
  private val coroutineScope: CoroutineScope
) {
  fun childScope(name: String): CoroutineScope = coroutineScope.namedChildScope(name)
}

class ProjectWizardJdkComboBox(
  val projectJdk: Sdk? = null,
  disposable: Disposable
): ComboBox<ProjectWizardJdkIntent>() {
  val registered: MutableList<ExistingJdk> = mutableListOf()
  val detectedJDKs: MutableList<DetectedJdk> = mutableListOf()
  var isLoadingDownloadItem: Boolean = false
  var isLoadingExistingJdks: Boolean = true
  val progressIcon: JBLabel = JBLabel(AnimatedIcon.Default.INSTANCE)
  val coroutineScope = application.service<ProjectWizardJdkComboBoxService>().childScope("ProjectWizardJdkComboBox")

  init {
    model = DefaultComboBoxModel(Vector(initialItems()))

    disposable.whenDisposed { coroutineScope.cancel() }

    if (registered.isEmpty()) {
      isLoadingDownloadItem = true
      coroutineScope.launch {
        addDownloadOpenJdkIntent()
      }
    }

    coroutineScope.launch {
      addExistingJdks()
    }

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
        item.icon = when {
          value is NoJdk && index == -1 -> null
          else -> getIcon(value)
        }

        when (value) {
          is NoJdk -> item.append(JavaUiBundle.message("jdk.missing.item"), SimpleTextAttributes.ERROR_ATTRIBUTES)
          is ExistingJdk -> {
            if (value.jdk == projectJdk) {
              item.append(JavaUiBundle.message("jdk.project.item"))
              item.append(" ${projectJdk.name}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            } else {
              item.append(value.jdk.name)
              val version = value.jdk.versionString ?: (value.jdk.sdkType as SdkType).presentableName
              item.append(" $version", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
          }
          is DownloadJdk -> {
            when (value.task.productName) {
              null -> {
                item.append(JavaUiBundle.message("jdk.download.predefined.item", value.task.suggestedSdkName))
                item.append(" ${value.task.plannedVersion}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
              }
              else -> {
                item.append(JavaUiBundle.message("jdk.download.predefined.item", value.task.productName))
              }
            }
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
        if (index == -1 && (isLoadingExistingJdks || isLoadingDownloadItem) && selectedItem !is DownloadJdk) {
          val panel = object : CellRendererPanel(BorderLayout()) {
            override fun getAccessibleContext(): AccessibleContext = component.accessibleContext
          }
          component.background = null
          panel.add(component, BorderLayout.CENTER)
          panel.add(progressIcon, BorderLayout.EAST)
          return panel
        }
        else {
          return component
        }
      }
    }
  }

  private fun initialItems(): MutableList<ProjectWizardJdkIntent> {
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

  private suspend fun addDownloadOpenJdkIntent() {
    val item = JdkListDownloader.getInstance()
      .downloadModelForJdkInstaller(null)
      .filter { it.matchesVendor("openjdk") }
      .filter { CpuArch.fromString(it.arch) == CpuArch.CURRENT }
      .maxByOrNull { it.jdkMajorVersion }

    if (item == null) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        isLoadingDownloadItem = false
      }
      return
    }

    val jdkInstaller = JdkInstaller.getInstance()
    val request = JdkInstallRequestInfo(item, jdkInstaller.defaultInstallDir(item))
    val task = JdkDownloaderBase.newDownloadTask(item, request, null)

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      insertItemAt(DownloadJdk(task), lastRegisteredJdkIndex)
      if (selectedItem is NoJdk) selectedIndex = 1
      isLoadingDownloadItem = false
    }
  }

  private suspend fun addExistingJdks() {
    val javaSdk = JavaSdk.getInstance()

    val detected = blockingContext {
      JdkFinder.getInstance().suggestHomePaths().mapNotNull { homePath: String ->
        val version = javaSdk.getVersionString(homePath)
        when {
          version != null && javaSdk.isValidSdkHome(homePath) -> DetectedJdk(version, homePath)
          else -> null
        }
      }
    }

    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      detected
        .filter { d -> registered.none { r -> FileUtil.pathsEqual(d.home, r.jdk.homePath) } }
        .forEach {
          detectedJDKs.add(it)
          addItem(it)
        }
      if ((selectedItem is NoJdk || selectedItem is DownloadJdk) && detected.any()) {
        val regex = "(\\d+)".toRegex()
        detected
          .maxBy { regex.find(it.version)?.value?.toInt() ?: 0 }
          .let { selectedItem = it }
      }
      isLoadingExistingJdks = false
    }
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
        selectAndAddJdk(this)
        selectedItem
      }
      is DetectedJdk -> {
        registerJdk(anObject.home, this)
        selectedItem
      }
      else -> anObject
    }
    super.setSelectedItem(toSelect)
  }

  val lastRegisteredJdkIndex
    get() = (0 until itemCount).firstOrNull { getItemAt(it) is AddJdkFromJdkListDownloader } ?: 0

  val comment: String?
    get() = when (selectedItem) {
      is DownloadJdk -> JavaUiBundle.message("jdk.download.comment")
      is NoJdk -> when {
        (0 until itemCount).any { getItemAt(it) is DownloadJdk } -> JavaUiBundle.message("jdk.missing.item.comment")
        else -> JavaUiBundle.message("jdk.missing.item.no.internet.comment")
      }
      else -> null
    }
}

private fun selectAndAddJdk(combo: ProjectWizardJdkComboBox) {
  combo.popup?.hide()
  SdkConfigurationUtil.selectSdkHome(JavaSdk.getInstance()) { path: String ->
    registerJdk(path, combo)
  }
}

private fun registerJdk(path: String, combo: ProjectWizardJdkComboBox) {
  runReadAction {
    SdkConfigurationUtil.createAndAddSDK(path, JavaSdk.getInstance())?.let {
      JdkComboBoxCollector.jdkRegistered(it)
      combo.detectedJDKs.find { detected -> FileUtil.pathsEqual(detected.home, path) }?.let { item ->
        combo.removeItem(item)
        combo.detectedJDKs.remove(item)
      }
      val comboItem = ExistingJdk(it)
      val index = combo.lastRegisteredJdkIndex
      combo.registered.add(comboItem)
      combo.insertItemAt(ExistingJdk(it), index)
      combo.selectedIndex = index
    }
  }
}

private fun addDownloadItem(extension: SdkDownload, combo: ComboBox<ProjectWizardJdkIntent>) {
  val config = ProjectStructureConfigurable.getInstance(DefaultProjectFactory.getInstance().defaultProject)
  combo.popup?.hide()
  val task = extension.pickSdk(JavaSdk.getInstance(), config.projectJdksModel, combo, null) ?: return
  val index = (0..combo.itemCount).firstOrNull {
    val item = combo.getItemAt(it)
    item !is NoJdk && item !is DownloadJdk
  } ?: 0
  combo.insertItemAt(DownloadJdk(task), index)
  combo.selectedIndex = index
}