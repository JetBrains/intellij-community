// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.execution.wsl.WslPath
import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.AddJdkService
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.JavaHomeFinder
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo.isWindows
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.EelApiKey
import com.intellij.platform.eel.provider.LocalEelKey
import com.intellij.platform.eel.provider.getEelApi
import com.intellij.platform.eel.provider.getEelApiBlocking
import com.intellij.platform.eel.provider.getEelApiKey
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Path
import java.util.*
import javax.accessibility.AccessibleContext
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JList
import kotlin.io.path.Path
import kotlin.text.isNotEmpty

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

fun NewProjectWizardStep.projectWizardJdkComboBox(
  row: Row,
  sdkProperty: GraphProperty<Sdk?>,
  sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?>,
): Cell<ProjectWizardJdkComboBox> {
  return projectWizardJdkComboBox(
    row, sdkProperty, sdkDownloadTaskProperty,
    requireNotNull(baseData) {
      "Expected ${NewProjectWizardBaseStep::class.java.simpleName} in the new project wizard step tree."
    }.pathProperty,
    { sdk -> context.projectJdk = sdk },
    context.disposable,
    context.projectJdk,
  )
}

fun projectWizardJdkComboBox(
  row: Row,
  sdkProperty: GraphProperty<Sdk?>,
  sdkDownloadTaskProperty: GraphProperty<SdkDownloadTask?>,
  locationProperty: GraphProperty<String>,
  setProjectJdk: (Sdk?) -> Unit,
  disposable: Disposable,
  projectJdk: Sdk? = null,
  sdkFilter: (Sdk) -> Boolean = { true },
): Cell<ProjectWizardJdkComboBox> {
  val sdkPropertyId = StdModuleTypes.JAVA
  val selectedJdkProperty = "jdk.selected.${sdkPropertyId.id}"

  val combo = ProjectWizardJdkComboBox(projectJdk, locationProperty.get(), disposable)

  locationProperty.afterPropagation {
    val path = locationProperty.get()
    if (path.isEmpty()) {
      return@afterPropagation
    }
    combo.projectLocationChanged(locationProperty.get())
  }

  combo.filterItems { sdkFilter(it) }

  return row.cell(combo)
    .columns(COLUMNS_LARGE)
    .apply {
      val commentCell = comment(component.comment, 50)
      component.addItemListener {
        commentCell.comment?.let { it.text = component.comment }
      }
    }
    .validationOnApply {
      val intent = it.selectedItem

      if (isWindows) {
        // todo: remove this when JDK over Eel is enabled by default
        val wslJDKValidation = validateJdkAndProjectCompatibility(intent, locationProperty::get)
        if (wslJDKValidation != null) return@validationOnApply wslJDKValidation
      }

      if (intent is DownloadJdk) {
        return@validationOnApply validateInstallDir(intent)
      }

      null
    }
    .onChanged {
      updateGraphProperties(combo, sdkProperty, sdkDownloadTaskProperty, selectedJdkProperty)
    }
    .onApply {
      val selected = combo.selectedItem

      if (selected is DetectedJdk) {
        registerJdk(selected.home, combo)
      }

      setProjectJdk.invoke(sdkProperty.get())

      when (selected) {
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

private fun ValidationInfoBuilder.validateInstallDir(intent: DownloadJdk): ValidationInfo? {
  return when (JdkInstaller.getInstance().validateInstallDir(intent.task.plannedHomeDir).first) {
    null -> error(JavaUiBundle.message("jdk.location.error", intent.task.plannedHomeDir))
    else -> null
  }
}

private fun ValidationInfoBuilder.validateJdkAndProjectCompatibility(intent: Any?, location: () -> String): ValidationInfo? {
  val path = when (intent) {
    is DownloadJdk -> intent.task.plannedHomeDir
    is ExistingJdk -> intent.jdk.homePath
    is DetectedJdk -> intent.home
    else -> null
  }

  val isProjectWSL = WslPath.isWslUncPath(location.invoke())

  if (path != null && WslPath.isWslUncPath(path) != isProjectWSL) {
    return when (isProjectWSL) {
      true -> error(JavaUiBundle.message("jdk.wsl.windows.error"))
      false -> error(JavaUiBundle.message("jdk.windows.wsl.error"))
    }
  }
  return null
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
  fun childScope(name: String): CoroutineScope = coroutineScope.childScope(name)
}

private inline fun guardEelKey(producer: () -> EelApiKey): EelApiKey? {
  return if (Registry.`is`("java.home.finder.use.eel")) {
    producer()
  }
  else {
    null
  }
}

class ProjectWizardJdkComboBox(
  val projectJdk: Sdk? = null,
  var projectLocation: String,
  disposable: Disposable,
) : ComboBox<ProjectWizardJdkIntent>(), UiDataProvider {

  // used in third-party plugin
  @Suppress("unused")
  @Deprecated("Use constructor with location parameter", ReplaceWith("ProjectWizardJdkComboBox(projectJdk, projectLocation, disposable)"))
  constructor(projectSdk: Sdk?, disposable: Disposable) : this(projectSdk, System.getProperty("user.home"), disposable)

  val registered: MutableList<ExistingJdk> = mutableListOf()
  val detectedJDKs: MutableList<DetectedJdk> = mutableListOf()
  val jdkItems: MutableList<ProjectWizardJdkIntent> = mutableListOf()
  var isLoadingDownloadItem: Boolean = false
  var isLoadingExistingJdks: Boolean = true
  val progressIcon: JBLabel = JBLabel(AnimatedIcon.Default.INSTANCE)
  val coroutineScope = application.service<ProjectWizardJdkComboBoxService>().childScope("ProjectWizardJdkComboBox")
  private var downloadOpenJdkJob: Job? = null
  private var addExistingJdkJob: Job? = null

  // todo: remote nullability from EelApiKey here we enable Eel by default in JDK detection
  var currentEelKey: EelApiKey? = guardEelKey { LocalEelKey }

  init {
    model = DefaultComboBoxModel(Vector())

    disposable.whenDisposed { coroutineScope.cancel() }

    reloadJdks(guardEelKey { LocalEelKey })

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
          is AddJdkFromPath -> item.append(JavaUiBundle.message("action.AddJdkAction.text"))

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


  fun projectLocationChanged(newLocation: String) {
    projectLocation = newLocation
    val key = guardEelKey { Path(newLocation).getEelApiKey() }
    if (key != currentEelKey) {
      currentEelKey = key
      reloadJdks(key)
    }
  }

  private fun reloadJdks(key: EelApiKey?) {
    for (item in jdkItems) {
      removeItem(item)
    }
    jdkItems.clear()

    registered.clear()
    registered.addAll(computeRegisteredSdks(key))
    for (registeredItem in registered) {
      jdkItems.add(registeredItem)
      addItem(registeredItem)
    }

    val helperJdks = computeHelperJdks(registered)
    for (helperItem in helperJdks) {
      jdkItems.add(helperItem)
      addItem(helperItem)
    }

    downloadOpenJdkJob?.cancel()
    isLoadingDownloadItem = false
    if (registered.isEmpty()) {
      isLoadingDownloadItem = true
      downloadOpenJdkJob = coroutineScope.getDownloadOpenJdkIntent(this)
    }

    detectedJDKs.clear()
    isLoadingExistingJdks = false
    addExistingJdkJob?.cancel()
    addExistingJdkJob = coroutineScope.findExistingJdks(projectLocation, this)
  }

  @RequiresEdt
  internal fun addDownloadOpenJdkIntent(task: ProjectWizardJdkIntent?) {
    if (task == null) {
      isLoadingDownloadItem = false
      return
    }

    insertItemAt(task, lastRegisteredJdkIndex)
    jdkItems.add(lastRegisteredJdkIndex, task)
    if (selectedItem is NoJdk) selectedIndex = 1
    isLoadingDownloadItem = false
  }

  @RequiresEdt
  internal fun addExistingJdks(detected: List<DetectedJdk>) {
    detected
      .filter { d -> registered.none { r -> FileUtil.pathsEqual(d.home, r.jdk.homePath) } }
      .forEach {
        detectedJDKs.add(it)
        jdkItems.add(it)
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
      else -> anObject
    }
    super.setSelectedItem(toSelect)
  }

  fun filterItems(sdkFilter: (Sdk) -> Boolean) {
    registered.removeAll { !sdkFilter(it.jdk) }
    for (i in itemCount - 1 downTo 0) {
      val item = getItemAt(i)
      if (item is ExistingJdk && !sdkFilter(item.jdk)) {
        removeItemAt(i)
      }
    }
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

  override fun uiDataSnapshot(sink: DataSink) {
    if (!Registry.`is`("java.home.finder.use.eel")) {
      return
    }
    sink[JDK_DOWNLOADER_EXT] = object : JdkDownloaderDialogHostExtension {
      override fun getEel(): EelApi {
        return Path.of(projectLocation).getEelApiBlocking()
      }
    }
  }
}

private fun selectAndAddJdk(combo: ProjectWizardJdkComboBox) {
  combo.popup?.hide()
  SdkConfigurationUtil.selectSdkHome(JavaSdk.getInstance(), null, Path(combo.projectLocation)) { path: String ->
    val version = JavaSdk.getInstance().getVersionString(path)
    val comboItem = DetectedJdk(version ?: "", path)
    combo.detectedJDKs.add(comboItem)
    combo.addItem(comboItem)
    combo.selectedItem = comboItem
  }
}

private fun registerJdk(path: String, combo: ProjectWizardJdkComboBox) {
  service<AddJdkService>().createJdkFromPath(path) {
    JdkComboBoxCollector.jdkRegistered(it)
    combo.detectedJDKs.find { detected -> FileUtil.pathsEqual(detected.home, path) }?.let { item ->
      combo.removeItem(item)
      combo.detectedJDKs.remove(item)
    }
    val comboItem = ExistingJdk(it)
    val index = combo.lastRegisteredJdkIndex
    combo.registered.add(comboItem)
    combo.insertItemAt(comboItem, index)
    combo.selectedIndex = index
  }
}

private fun computeRegisteredSdks(key: EelApiKey?): List<ExistingJdk> {
  // Add JDKs from the ProjectJdkTable
  return ProjectJdkTable.getInstance().allJdks
    .filter { jdk ->
      jdk.sdkType is JavaSdkType &&
      jdk.sdkType !is DependentSdkType &&
      (key == null || ProjectSdksModel.sdkMatchesEel(key, jdk))
    }
    .map { ExistingJdk(it) }
}

private fun computeHelperJdks(registered: List<ExistingJdk>): List<ProjectWizardJdkIntent> {
  val helperJdks = mutableListOf<ProjectWizardJdkIntent>()

  if (registered.isEmpty()) {
    helperJdks.add(NoJdk)
  }

  // Add options to download or select a JDK
  SdkDownload.EP_NAME.findFirstSafe { it.supportsDownload(JavaSdk.getInstance()) }?.let {
    helperJdks.add(AddJdkFromJdkListDownloader(it))
  }
  helperJdks.add(AddJdkFromPath)
  return helperJdks
}

// Suggests to download OpenJDK if nothing else is available in the IDE
private fun CoroutineScope.getDownloadOpenJdkIntent(comboBox: ProjectWizardJdkComboBox): Job = launch {
  val item = JdkListDownloader.getInstance()
    .downloadModelForJdkInstaller(null)
    .filter { it.matchesVendor("openjdk") }
    .filter { CpuArch.fromString(it.arch) == CpuArch.CURRENT }
    .maxByOrNull { it.jdkMajorVersion }

  if (item == null) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      comboBox.addDownloadOpenJdkIntent(null)
    }
    return@launch
  }

  val jdkInstaller = JdkInstaller.getInstance()
  val request = JdkInstallRequestInfo(item, jdkInstaller.defaultInstallDir(item))
  val task = JdkDownloaderBase.newDownloadTask(item, request, null)

  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    val task = DownloadJdk(task)
    comboBox.addDownloadOpenJdkIntent(task)
  }
}


// Searches for JDKs located on the computer, but not added to the IDE
private fun CoroutineScope.findExistingJdks(location: String?, comboBox: ProjectWizardJdkComboBox): Job = launch {
  val javaSdk = JavaSdk.getInstance()
  val homePaths = if (Registry.`is`("java.home.finder.use.eel")) {
    val eel = location?.takeIf { it.isNotEmpty() }?.let { Path(it).getEelApi() } ?: localEel
    JavaHomeFinder.suggestHomePaths(eel, false)
  }
  else {
    JavaHomeFinder.suggestHomePaths()
  }
  val detected = homePaths.mapNotNull { homePath: String ->
    val version = javaSdk.getVersionString(homePath)
    when {
      version != null && javaSdk.isValidSdkHome(homePath) -> DetectedJdk(version, homePath)
      else -> null
    }
  }

  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    comboBox.addExistingJdks(detected)
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