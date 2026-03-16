// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.icons.AllIcons
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.AddJdkFromJdkListDownloader
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.AddJdkFromPath
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.DetectedJdk
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.DownloadJdk
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.ExistingJdk
import com.intellij.ide.projectWizard.ProjectWizardJdkIntent.NoJdk
import com.intellij.ide.projectWizard.ProjectWizardJdkPredicate.Companion.getError
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.WizardContext
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
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.projectRoots.impl.JavaHomeFinder
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JDK_DOWNLOADER_EXT
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloaderDialogHostExtension
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallRequestInfo
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.java.LanguageLevel
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.ClientProperty
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.lang.JavaVersion
import com.intellij.util.system.CpuArch
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import javax.accessibility.AccessibleContext
import javax.swing.Icon
import javax.swing.JList
import kotlin.reflect.KFunction1

private val selectedJdkProperty = "jdk.selected.JAVA_MODULE"

/**
 * @param sdkFilter Filter for registered SDKs
 * @param jdkPredicate Predicate to show an error based on the JDK intent version/name
 */
fun NewProjectWizardStep.projectWizardJdkComboBox(
  row: Row,
  intentProperty: GraphProperty<ProjectWizardJdkIntent>,
  sdkFilter: (Sdk) -> Boolean = { true },
  jdkPredicate: ProjectWizardJdkPredicate? = ProjectWizardJdkPredicate.IsJdkSupported(),
): Cell<ProjectWizardJdkComboBox> {
  return row.projectWizardJdkComboBox(
    context,
    requireNotNull(baseData) {
      "Expected ${NewProjectWizardBaseStep::class.java.simpleName} in the new project wizard step tree."
    }.pathProperty.toEelDescriptorProperty(),
    intentProperty,
    sdkFilter,
    jdkPredicate,
  )
}

/**
 * @param sdkFilter Filter for registered SDKs
 * @param jdkPredicate Predicate to show an error based on the JDK intent version/name
 */
fun Row.projectWizardJdkComboBox(
  context: WizardContext,
  eelDescriptorProperty: ObservableProperty<EelDescriptor>,
  intentProperty: ObservableMutableProperty<ProjectWizardJdkIntent>,
  sdkFilter: (Sdk) -> Boolean = { true },
  jdkPredicate: ProjectWizardJdkPredicate? = ProjectWizardJdkPredicate.IsJdkSupported(),
): Cell<ProjectWizardJdkComboBox> {
  val comboBox = ProjectWizardJdkComboBox(context.projectJdk, context.disposable, sdkFilter, jdkPredicate)
  comboBox.isUsePreferredSizeAsMinimum = false

  val intentValue = intentProperty.get()
  require(intentValue == NoJdk) {
    """
      The default value of intentProperty is controlled by ${ProjectWizardJdkComboBox::class.java.simpleName}. 
      All external default values of intentProperty will be ignored.
      External default value = '$intentValue', expected value = '$NoJdk'.
    """.trimIndent()
  }
  intentProperty.set(comboBox.item)

  return cell(comboBox)
    .align(AlignX.FILL)
    .apply {
      val commentCell = comment(component.comment, 50)
      component.addItemListener {
        commentCell.comment?.let { it.text = component.comment }
      }
    }
    .applyToComponent {
      filterItems { sdkFilter(it) }
      bind(intentProperty)
      bindEelDescriptor(eelDescriptorProperty)
    }
    .validationInfo {
      val intent = intentProperty.get()
      val version = intent.versionString ?: return@validationInfo null
      val name = intent.name
      val error = jdkPredicate?.getError(version, name ?: version) ?: return@validationInfo null
      warning(error)
    }
    .validationOnApply {
      val intent = it.selectedItem

      val jdkCompatibilityValidation = validateJdkAndProjectCompatibility(intent, eelDescriptorProperty.get())
      if (jdkCompatibilityValidation != null) return@validationOnApply jdkCompatibilityValidation

      if (intent is DownloadJdk) {
        return@validationOnApply validateInstallDir(intent)
      }

      null
    }
    .onApply {
      val intent = intentProperty.get()
      when (intent) {
        is NoJdk -> JdkComboBoxCollector.noJdkSelected()
        is DownloadJdk -> JdkComboBoxCollector.jdkDownloaded((intent.task as JdkDownloadTask).jdkItem)
        else -> Unit
      }
      PropertiesComponent.getInstance().setValue(selectedJdkProperty, intent.name)
    }
    .onApply {
      context.projectJdk = intentProperty.get().prepareJdk()
    }
}

private fun ValidationInfoBuilder.validateInstallDir(intent: DownloadJdk): ValidationInfo? {
  return when (JdkInstaller.getInstance().validateInstallDir(intent.task.plannedHomeDir).first) {
    null -> error(JavaUiBundle.message("jdk.location.error", intent.task.plannedHomeDir))
    else -> null
  }
}

private fun ValidationInfoBuilder.validateJdkAndProjectCompatibility(intent: Any?, eelDescriptor: EelDescriptor): ValidationInfo? {
  val path = when (intent) {
    is DownloadJdk -> intent.task.plannedHomeDir
    is ExistingJdk -> intent.jdk.homePath
    is DetectedJdk -> intent.home
    else -> null
  } ?: return null

  val projectRelatedMachine = eelDescriptor.getResolvedEelMachine() ?: LocalEelMachine
  if (!projectRelatedMachine.ownsPath(Path.of(path))) {
    return error(JavaUiBundle.message("jdk.incompatible.location.error", Path.of(path).getEelDescriptor().name, eelDescriptor.name))
  }

  return null
}

@Service(Service.Level.APP)
internal class ProjectWizardJdkComboBoxService(
  private val coroutineScope: CoroutineScope
) {
  fun childScope(name: String): CoroutineScope = coroutineScope.childScope(name)
}

private inline fun guardEelDescriptor(producer: () -> EelDescriptor): EelDescriptor? {
  return if (Registry.`is`("java.home.finder.use.eel")) {
    producer()
  }
  else {
    null
  }
}

class ProjectWizardJdkComboBox(
  val projectJdk: Sdk? = null,
  disposable: Disposable,
  val sdkFilter: (Sdk) -> Boolean = { true },
  val jdkPredicate: ProjectWizardJdkPredicate? = null,
) : ComboBox<ProjectWizardJdkIntent>(MutableCollectionComboBoxModel()), UiDataProvider {

  override fun getModel(): CollectionComboBoxModel<ProjectWizardJdkIntent> {
    return super.getModel() as CollectionComboBoxModel<ProjectWizardJdkIntent>
  }

  val registered: List<ExistingJdk>
    get() = model.items.filterIsInstance<ExistingJdk>()
  val detectedJDKs: List<DetectedJdk>
    get() = model.items.filterIsInstance<DetectedJdk>()
  var isLoadingDownloadItem: Boolean = false
  var isLoadingDetectedJdks: Boolean = false
  val progressIcon: JBLabel = JBLabel(AnimatedIcon.Default.INSTANCE)
  val coroutineScope: CoroutineScope = application.service<ProjectWizardJdkComboBoxService>().childScope("ProjectWizardJdkComboBox")
  private var downloadOpenJdkJob: Job? = null
  private var addDetectedJdkJob: Job? = null

  // todo: remove nullability from EelDescriptor here we enable Eel by default in JDK detection
  var currentEelDescriptor: EelDescriptor? = guardEelDescriptor { LocalEelDescriptor }

  init {
    disposable.whenDisposed { coroutineScope.cancel() }

    reloadJdks(guardEelDescriptor { LocalEelDescriptor })

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
          is ExistingJdk -> JavaSdk.getInstance().icon
          is DetectedJdk -> if (item.isSymlink) AllIcons.Nodes.Related else AllIcons.Nodes.PpJdk
          else -> EmptyIcon.ICON_16
        }
      }

      override fun getListCellRendererComponent(
        list: JList<out ProjectWizardJdkIntent>?,
        value: ProjectWizardJdkIntent,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
      ): Component {
        val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (index == -1 && (isLoadingDetectedJdks || isLoadingDownloadItem) && selectedItem !is DownloadJdk) {
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

  @RequiresEdt
  fun refreshJdks(descriptor: EelDescriptor) {
    currentEelDescriptor = descriptor
    reloadJdks(descriptor)
  }

  @RequiresEdt
  private fun reloadJdks(key: EelDescriptor?) {
    model.removeAll()

    model.add(computeRegisteredSdks(key).filter { existing -> sdkFilter(existing.jdk) })
    model.add(computeHelperJdks(registered))

    selectDefaultItem()

    downloadOpenJdkJob?.cancel()
    isLoadingDownloadItem = false
    if (registered.isEmpty() || registered.none { it.isAtLeast(8) }) {
      isLoadingDownloadItem = true
      downloadOpenJdkJob = coroutineScope.getDownloadOpenJdkIntent(this)
    }

    isLoadingDetectedJdks = true
    addDetectedJdkJob?.cancel()
    addDetectedJdkJob = coroutineScope.findDetectedJdks(currentEelDescriptor, this)
  }

  private fun selectDefaultItem() {
    var intent: ProjectWizardJdkIntent? = null

    if (projectJdk != null) {
      // If we are creating a new module, select the project JDK
      intent = model.items.find { intent -> intent is ExistingJdk && intent.name == projectJdk.name }
    }

    if (intent == null) {
      // Select the JDK defined in New Projects Setup | Structure…
      val defaultProject = DefaultProjectFactory.getInstance().defaultProject
      val defaultSdk = ProjectRootManager.getInstance(defaultProject).projectSdk
      if (defaultSdk != null) {
        intent = model.items.find { intent -> intent is ExistingJdk && intent.name == defaultSdk.name }
      }
    }

    if (intent == null) {
      // Select the last JDK used to create a project
      val lastSelected = PropertiesComponent.getInstance().getValue(selectedJdkProperty)
      intent = model.items.find { intent -> intent is ExistingJdk && intent.name == lastSelected }
    }

    if (intent == null) {
      // Select the JDK with the highest compatible and released version
      intent = model.items
        .map { intent -> intent to intent.javaVersion }
        .filter { (_, version) -> version != null && canSuggestVersion(version) }
        .maxByOrNull { (_, version) -> version!!.feature }
        ?.first
    }

    if (intent == null) {
      intent = model.items.firstOrNull()
    }

    if (intent != null) selectedItem = intent
  }

  @RequiresEdt
  internal fun addDownloadOpenJdkIntent(task: ProjectWizardJdkIntent?) {
    if (task == null) {
      isLoadingDownloadItem = false
      return
    }

    insertItemAt(task, lastRegisteredJdkIndex)
    if (selectedItem is NoJdk || !(selectedItem as ProjectWizardJdkIntent).isAtLeast(8)) selectedIndex = 1
    isLoadingDownloadItem = false
  }

  @RequiresEdt
  internal fun addDetectedJdks(detected: List<DetectedJdk>) {
    detected
      .filter { d -> registered.none { r -> FileUtil.pathsEqual(d.home, r.jdk.homePath) } }
      .forEach {
        addItem(it)
      }
    if ((selectedItem is NoJdk || selectedItem is DownloadJdk) && detected.any()) {
      detected
        .mapNotNull {
          when (val version = it.javaVersion) {
            null -> null
            else -> it to version
          }
        }
        .filter { (_, version) -> canSuggestVersion(version) }
        .maxBy { (_, version) -> version.feature }
        .let { selectedItem = it.first }
    }
    isLoadingDetectedJdks = false
  }

  private fun canSuggestVersion(version: JavaVersion): Boolean {
    return version.isAtLeast(8) && version.feature <= LanguageLevel.HIGHEST.feature()
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
    registered.forEach {
      if (!sdkFilter(it.jdk)) {
        removeItem(it)
      }
    }
    for (i in itemCount - 1 downTo 0) {
      val item = getItemAt(i)
      if (item is ExistingJdk && !sdkFilter(item.jdk)) {
        removeItemAt(i)
      }
    }
  }

  val lastRegisteredJdkIndex: Int
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
        return runBlockingMaybeCancellable {
          currentEelDescriptor?.toEelApi() ?: localEel
        }
      }
    }
  }
}

private fun selectAndAddJdk(combo: ProjectWizardJdkComboBox) {
  combo.popup?.hide()
  val path = if (Registry.`is`("java.home.finder.use.eel")) {
    EelPathUtils.getHomePath(combo.currentEelDescriptor ?: LocalEelDescriptor)
  }
  else {
    Path.of(System.getProperty("user.home"))
  }
  SdkConfigurationUtil.selectSdkHome(JavaSdk.getInstance(), null, path) { path: String ->
    val version = JavaSdk.getInstance().getVersionString(path)
    val comboItem = DetectedJdk(version ?: "", path, containsSymbolicLink(path))
    combo.addItem(comboItem)
    combo.selectedItem = comboItem
  }
}

private fun containsSymbolicLink(path: String): Boolean {
  val p = Paths.get(path)
  return try { p.toRealPath() != p }
         catch (_: IOException) { false }
}

private fun computeRegisteredSdks(key: EelDescriptor?): List<ExistingJdk> {
  // Add JDKs from the ProjectJdkTable
  return ProjectJdkTable.getInstance().allJdks
    .filter { jdk ->
      jdk.sdkType is JavaSdkType &&
      jdk.sdkType !is DependentSdkType &&
      (key == null || ProjectSdksModel.sdkMatchesEel(key.getResolvedEelMachine() ?: LocalEelMachine, jdk))
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

/**
 * Adds an option to download the latest OpenJDK to the combo box.
 * This is called if no JDKs were found in the ProjectJdkTable and on the disk.
 */
private fun CoroutineScope.getDownloadOpenJdkIntent(comboBox: ProjectWizardJdkComboBox): Job = launch {
  val eel = if (Registry.`is`("java.home.finder.use.eel")) {
    comboBox.currentEelDescriptor?.toEelApi() ?: localEel
  }
  else {
    null
  }
  val predicate = if (eel != null) {
    JdkPredicate.forEel(eel)
  }
  else {
    JdkPredicate.default()
  }

  val item = JdkListDownloader.getInstance()
    .downloadModelForJdkInstaller(null, predicate)
    .filter { it.isDefaultItem }
    .filter { CpuArch.fromString(it.arch) == CpuArch.CURRENT }
    .filter { comboBox.jdkPredicate?.showJdkItem(it) ?: true }
    .maxByOrNull { it.jdkMajorVersion }

  if (item == null) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      comboBox.addDownloadOpenJdkIntent(null)
    }
    return@launch
  }

  val jdkInstaller = JdkInstaller.getInstance()
  val request = JdkInstallRequestInfo(item, jdkInstaller.defaultInstallDir(item, eel))
  val task = JdkDownloadTask(item, request, null)

  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    val task = DownloadJdk(task)
    comboBox.addDownloadOpenJdkIntent(task)
  }
}


/**
 * Searches for JDKs located on the computer, but not registered in the IDE.
 */
private fun CoroutineScope.findDetectedJdks(descriptor: EelDescriptor?, comboBox: ProjectWizardJdkComboBox): Job = launch {
  val detected = when {
    Registry.`is`("java.home.finder.use.eel") -> {
      val eelDescriptor = descriptor ?: LocalEelDescriptor
      findDetectedJdksEel(eelDescriptor)
    }
    else -> {
      val homePaths = JavaHomeFinder.suggestHomePaths()
      val javaSdk = JavaSdk.getInstance()
      homePaths.mapNotNull { homePath: String ->
        val version = javaSdk.getVersionString(homePath)
        when {
          version != null && javaSdk.isValidSdkHome(homePath) -> DetectedJdk(version, homePath, false)
          else -> null
        }
      }
    }
  }

  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    comboBox.addDetectedJdks(detected)
  }
}

private fun findDetectedJdksEel(eelDescriptor: EelDescriptor): List<DetectedJdk> {
  val javaSdk = JavaSdk.getInstance()
  val jdks = JavaHomeFinder.findJdks(eelDescriptor, false)

  return jdks.mapNotNull {
    val version = it.versionInfo
    val symlink = containsSymbolicLink(it.path)

    when {
      version != null && javaSdk.isValidSdkHome(it.path) -> DetectedJdk(version.displayVersionString(), it.path, symlink)
      else -> null
    }
  }
}

private fun addDownloadItem(extension: SdkDownload, combo: ComboBox<ProjectWizardJdkIntent>) {
  val config = ProjectStructureConfigurable.getInstance(DefaultProjectFactory.getInstance().defaultProject)
  combo.popup?.hide()
  val sdkFilter = getSdkFilter(combo)
  val task = extension.pickSdk(JavaSdk.getInstance(), config.projectJdksModel, combo, null, sdkFilter) ?: return
  val index = (0..combo.itemCount).firstOrNull {
    val item = combo.getItemAt(it)
    item !is NoJdk && item !is DownloadJdk
  } ?: 0
  combo.insertItemAt(DownloadJdk(task), index)
  combo.selectedIndex = index
}

private fun getSdkFilter(combo: ComboBox<ProjectWizardJdkIntent>): KFunction1<JdkItem, Boolean>? {
  val projectWizardJdkComboBox = combo as? ProjectWizardJdkComboBox ?: return null
  val jdkPredicate = projectWizardJdkComboBox.jdkPredicate ?: return null
  return jdkPredicate::showJdkItem
}

internal fun ProjectWizardJdkComboBox.bindEelDescriptor(eelDescriptorProperty: ObservableProperty<EelDescriptor>) {
  // initial setup
  refreshJdks(eelDescriptorProperty.get())
  eelDescriptorProperty.afterChange { eelDescriptor ->
    refreshJdks(eelDescriptor)
  }
}

internal fun ObservableProperty<String>.toEelDescriptorProperty(): ObservableProperty<EelDescriptor> {
  return transform { Path.of(it).getEelDescriptor() }
}