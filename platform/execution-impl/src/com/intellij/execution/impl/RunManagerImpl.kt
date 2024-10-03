// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.execution.impl

import com.intellij.configurationStore.*
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.execution.*
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.configurations.*
import com.intellij.execution.runToolbar.RunToolbarSlotManager
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeature
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.project.isDirectoryBased
import com.intellij.serviceContainer.NonInjectable
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import com.intellij.util.IconUtil
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.mapSmart
import com.intellij.util.containers.nullize
import com.intellij.util.containers.toMutableSmartList
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val SELECTED_ATTR = "selected"
internal const val METHOD = "method"
private const val OPTION = "option"
private const val RECENT = "recent_temporary"

private val RUN_CONFIGURATION_TEMPLATE_PROVIDER_EP = ProjectExtensionPointName<RunConfigurationTemplateProvider>("com.intellij.runConfigurationTemplateProvider")

interface RunConfigurationTemplateProvider {
  fun getRunConfigurationTemplate(factory: ConfigurationFactory, runManager: RunManagerImpl): RunnerAndConfigurationSettingsImpl?
}

@State(name = "RunManager", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE, useSaveThreshold = ThreeState.NO))])
open class RunManagerImpl @NonInjectable constructor(val project: Project, private val coroutineScope: CoroutineScope, sharedStreamProvider: StreamProvider?) :
  RunManagerEx(), PersistentStateComponent<Element>, Disposable, SettingsSavingComponent {
  companion object {
    const val CONFIGURATION: String = "configuration"
    const val NAME_ATTR: String = "name"

    internal val LOG: Logger = logger<RunManagerImpl>()

    @JvmStatic
    fun getInstanceImpl(project: Project): RunManagerImpl = getInstance(project) as RunManagerImpl

    fun canRunConfiguration(environment: ExecutionEnvironment): Boolean {
      return environment.runnerAndConfigurationSettings?.let {
        canRunConfiguration(it, environment.executor)
      } ?: false
    }

    @JvmStatic
    fun canRunConfiguration(configuration: RunnerAndConfigurationSettings, executor: Executor): Boolean {
      try {
        ThreadingAssertions.assertBackgroundThread()
        configuration.checkSettings(executor)
      }
      catch (ignored: IndexNotReadyException) {
        return false
      }
      catch (ignored: RuntimeConfigurationError) {
        return false
      }
      catch (ignored: RuntimeConfigurationException) {
      }
      return true
    }
  }

  @JvmOverloads
  constructor(project: Project, scope: CoroutineScope = (project as ComponentManagerEx).getCoroutineScope()) :
    this(project = project, coroutineScope = scope, sharedStreamProvider = null)

  private val lock = ReentrantReadWriteLock()

  private val idToType = object {
    private var cachedValue: Map<String, ConfigurationType>? = null

    val value: Map<String, ConfigurationType>
      get() {
        var result = cachedValue
        if (result == null) {
          result = compute()
          cachedValue = result
        }
        return result
      }

    fun drop() {
      cachedValue = null
    }

    fun resolve(value: Map<String, ConfigurationType>) {
      cachedValue = value
    }

    private fun compute(): Map<String, ConfigurationType> {
      return buildConfigurationTypeMap(ConfigurationType.CONFIGURATION_TYPE_EP.extensionList)
    }
  }

  @Suppress("LeakingThis")
  private val listManager = RunConfigurationListManagerHelper(this)

  private val templateIdToConfiguration = HashMap<String, RunnerAndConfigurationSettingsImpl>()

  // template configurations are not included here
  private val idToSettings: LinkedHashMap<String, RunnerAndConfigurationSettings>
    get() = listManager.idToSettings

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  protected open var selectedConfigurationId: String? = null

  private val iconAndInvalidCache = RunConfigurationIconAndInvalidCache()

  // used by Rider
  @Suppress("unused")
  val iconCache: RunConfigurationIconCache
    get() = iconAndInvalidCache

  private val recentlyUsedTemporaries = ArrayList<RunnerAndConfigurationSettings>()

  // templates should be first, because to migrate old before a run list to effective, we need to get template before a run task
  private val workspaceSchemeManagerProvider = SchemeManagerIprProvider("configuration", Comparator { n1, n2 ->
    val w1 = getNameWeight(n1)
    val w2 = getNameWeight(n2)
    if (w1 == w2) {
      n1.compareTo(n2)
    }
    else {
      w1 - w2
    }
  })

  internal val schemeManagerIprProvider: SchemeManagerIprProvider? =
    if (project.isDirectoryBased || sharedStreamProvider != null) null else SchemeManagerIprProvider("configuration")

  @Suppress("LeakingThis")
  private val templateDifferenceHelper = TemplateDifferenceHelper(this)

  @Suppress("LeakingThis")
  private val workspaceSchemeManager = SchemeManagerFactory.getInstance(project).create(
    directoryName = "workspace",
    processor = RunConfigurationSchemeManager(this, templateDifferenceHelper,
                                              isShared = false,
                                              isWrapSchemeIntoComponentElement = false),
    streamProvider = workspaceSchemeManagerProvider,
    isAutoSave = false,
  )

  @Suppress("LeakingThis")
  private val projectSchemeManager = SchemeManagerFactory.getInstance(project).create(
    directoryName = "runConfigurations",
    processor = RunConfigurationSchemeManager(this, templateDifferenceHelper,
                                              isShared = true,
                                              isWrapSchemeIntoComponentElement = schemeManagerIprProvider == null),
    schemeNameToFileName = OLD_NAME_CONVERTER,
    streamProvider = sharedStreamProvider ?: schemeManagerIprProvider,
  )

  @Suppress("unused")
  internal val dotIdeaRunConfigurationsPath: String
    get() = FileUtil.toSystemIndependentName(projectSchemeManager.rootDirectory.path)

  private val rcInArbitraryFileManager = RCInArbitraryFileManager(project)

  private val isFirstLoadState = AtomicBoolean(true)

  private val stringIdToBeforeRunProvider = SynchronizedClearableLazy {
    val result = ConcurrentHashMap<String, BeforeRunTaskProvider<*>>()
    for (provider in BeforeRunTaskProvider.EP_NAME.getExtensions(project)) {
      result.put(provider.id.toString(), provider)
    }
    result
  }

  internal val eventPublisher: RunManagerListener
    get() = project.messageBus.syncPublisher(RunManagerListener.TOPIC)

  init {
      project.messageBus.connect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        iconAndInvalidCache.clear()
      }

      override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        iconAndInvalidCache.clear()
        // must be on unloaded and not before, since a load must not be able to use unloaded plugin classes
        reloadSchemes()
      }
    })

    BeforeRunTaskProvider.EP_NAME.getPoint(project).addChangeListener(stringIdToBeforeRunProvider::drop, project)
  }

  override fun shouldSetRunConfigurationFromContext(): Boolean {
    return Registry.`is`("select.run.configuration.from.context") && !isRiderRunWidgetActive()
  }

  override fun isRiderRunWidgetActive(): Boolean {
    return RunToolbarSlotManager.getInstance(project).active
  }

  private fun clearSelectedConfigurationIcon() {
    selectedConfigurationId?.let {
      iconAndInvalidCache.remove(it)
    }
  }

  @TestOnly
  fun initializeConfigurationTypes(factories: List<ConfigurationType>) {
    idToType.resolve(buildConfigurationTypeMap(factories))
  }

  private fun buildConfigurationTypeMap(factories: List<ConfigurationType>): Map<String, ConfigurationType> {
    val types = factories.toMutableList()
    types.add(UnknownConfigurationType.getInstance())
    val map = HashMap<String, ConfigurationType>()
    for (type in types) {
      map.put(type.id, type)
    }
    return map
  }

  override fun createConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    val template = getConfigurationTemplate(factory)
    return createConfiguration(factory.createConfiguration(name, template.configuration), template)
  }

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    return createConfiguration(runConfiguration, getConfigurationTemplate(factory))
  }

  private fun createConfiguration(configuration: RunConfiguration, template: RunnerAndConfigurationSettingsImpl): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this, configuration)
    settings.importRunnerAndConfigurationSettings(template)
    configuration.beforeRunTasks = template.configuration.beforeRunTasks
    return settings
  }

  override fun dispose() {
    lock.write {
      iconAndInvalidCache.clear()
      templateIdToConfiguration.clear()
    }
  }

  @get:ApiStatus.Internal
  open val config by lazy { RunManagerConfig(PropertiesComponent.getInstance(project)) }

  /**
   * Template configuration is not included
   */
  override fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration> {
    var result: MutableList<RunConfiguration>? = null
    for (settings in allSettings) {
      val configuration = settings.configuration
      if (type.id == configuration.type.id) {
        if (result == null) {
          result = ArrayList()
        }
        result.add(configuration)
      }
    }
    return result ?: emptyList()
  }

  override val allConfigurationsList: List<RunConfiguration>
    get() = allSettings.mapSmart { it.configuration }

  fun getSettings(configuration: RunConfiguration) = allSettings.firstOrNull { it.configuration === configuration } as? RunnerAndConfigurationSettingsImpl

  override fun getConfigurationSettingsList(type: ConfigurationType) = allSettings.filter { it.type === type }

  fun getConfigurationsGroupedByTypeAndFolder(isIncludeUnknown: Boolean): Map<ConfigurationType, Map<String?, List<RunnerAndConfigurationSettings>>> {
    val result = LinkedHashMap<ConfigurationType, MutableMap<String?, MutableList<RunnerAndConfigurationSettings>>>()
    // use allSettings to return a sorted result
    for (setting in allSettings) {
      val type = setting.type
      if (!isIncludeUnknown && type === UnknownConfigurationType.getInstance()) {
        continue
      }

      val folderToConfigurations = result.computeIfAbsent(type) { LinkedHashMap() }
      folderToConfigurations.computeIfAbsent(setting.folderName) { ArrayList() }.add(setting)
    }
    return result
  }

  override fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettingsImpl {
    if (!project.isDefault) {
      for (provider in RUN_CONFIGURATION_TEMPLATE_PROVIDER_EP.getExtensions(project)) {
        provider.getRunConfigurationTemplate(factory, this)?.let {
          return it
        }
      }
    }

    val key = getFactoryKey(factory)
    return lock.read { templateIdToConfiguration.get(key) } ?: lock.write {
      templateIdToConfiguration.getOrPut(key) {
        val template = createTemplateSettings(factory)
        workspaceSchemeManager.addScheme(template)
        template
      }
    }
  }

  internal fun createTemplateSettings(factory: ConfigurationFactory): RunnerAndConfigurationSettingsImpl {
    val configuration = factory.createTemplateConfiguration(project, this)
    configuration.isAllowRunningInParallel = factory.singletonPolicy.isAllowRunningInParallel
    val template = RunnerAndConfigurationSettingsImpl(this, configuration, isTemplate = true)
    if (configuration is UnknownRunConfiguration) {
      configuration.isDoNotStore = true
    }
    factory.configureDefaultSettings(template)
    configuration.beforeRunTasks = getHardcodedBeforeRunTasks(configuration, factory)
    return template
  }

  private fun deleteRunConfigsFromArbitraryFilesNotWithinProjectContent() {
    ReadAction
      .nonBlocking(Callable {
        lock.read { rcInArbitraryFileManager.findRunConfigsThatAreNotWithinProjectContent() }
      })
      .coalesceBy(this)
      .expireWith(project)
      .finishOnUiThread(ModalityState.defaultModalityState()) {
        // don't delete file just because it has become excluded
        removeConfigurations(it, deleteFileIfStoredInArbitraryFile = false)
      }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun loadRunConfigsFromArbitraryFiles() {
    coroutineScope.launch(Dispatchers.Default) {
      readAction {
        blockingContextToIndicator {
          updateRunConfigsFromArbitraryFiles(emptyList(), loadFileWithRunConfigs(project))
        }
      }
    }
  }

  // Paths in <code>deletedFilePaths</code> and <code>updatedFilePaths</code> may be not related to the project, use ProjectIndex.isInContent() when needed
  internal fun updateRunConfigsFromArbitraryFiles(deletedFilePaths: Collection<String>, updatedFilePaths: Collection<String>) {
    val oldSelectedId = selectedConfigurationId
    val deletedRunConfigs = lock.read { rcInArbitraryFileManager.getRunConfigsFromFiles(deletedFilePaths) }

    // the file is already deleted - no need to delete it once again
    removeConfigurations(deletedRunConfigs, deleteFileIfStoredInArbitraryFile = false)

    for (filePath in updatedFilePaths) {
      val deletedAndAddedRunConfigs = ApplicationManager.getApplication().runReadAction( Computable {
        lock.read { rcInArbitraryFileManager.loadChangedRunConfigsFromFile(this, filePath) }
      })

      for (runConfig in deletedAndAddedRunConfigs.addedRunConfigs) {
        addConfiguration(runConfig)

        if (runConfig.isTemplate) {
          continue
        }

        if (selectedConfigurationId == null && runConfig.uniqueID == oldSelectedId) {
          // don't loosely currently select RC in case of any external changes in the file
          selectedConfigurationId = oldSelectedId
        }
      }

      // some VFS event caused RC to disappear (probably manual editing) - but the file itself shouldn't be deleted
      removeConfigurations(deletedAndAddedRunConfigs.deletedRunConfigs, deleteFileIfStoredInArbitraryFile = false)
    }
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings) {
    doAddConfiguration(settings as RunnerAndConfigurationSettingsImpl, isCheckRecentsLimit = true)
  }

  private fun doAddConfiguration(settings: RunnerAndConfigurationSettingsImpl, isCheckRecentsLimit: Boolean) {
    if (settings.isTemplate) {
      addOrUpdateTemplateConfiguration(settings)
      return
    }

    val newId = settings.uniqueID
    var existingId: String?
    lock.write {
      // https://youtrack.jetbrains.com/issue/IDEA-112821
      // we should check by instance, not by id (todo is it still relevant?)
      existingId = if (idToSettings.get(newId) === settings) newId else findExistingConfigurationId(settings)
      existingId?.let {
        if (newId != it) {
          idToSettings.remove(it)
          if (selectedConfigurationId == it) {
            selectedConfigurationId = newId
          }
          listManager.updateConfigurationId(it, newId)
        }
      }

      idToSettings.put(newId, settings)
      listManager.requestSort()

      if (existingId == null) {
        refreshUsagesList(settings)
      }

      ensureSettingsAreTrackedByCorrectManager(settings, existingId != null)
    }

    if (existingId == null) {
      if (isCheckRecentsLimit && settings.isTemporary) {
        checkRecentsLimit()
      }
      eventPublisher.runConfigurationAdded(settings)
    }
    else {
      eventPublisher.runConfigurationChanged(settings, existingId)
    }
  }

  private fun addOrUpdateTemplateConfiguration(settings: RunnerAndConfigurationSettingsImpl) {
    val factory = settings.factory
    // do not register unknown RC type templates (it is saved in any case in the scheme manager, so, not lost on save)
    if (factory == UnknownConfigurationType.getInstance()) return

    lock.write {
      val key = getFactoryKey(factory)
      val existing = templateIdToConfiguration.put(key, settings)
      ensureSettingsAreTrackedByCorrectManager(settings, existing != null)
    }
  }

  private fun ensureSettingsAreTrackedByCorrectManager(settings: RunnerAndConfigurationSettingsImpl,
                                                       ensureSettingsAreRemovedFromOtherManagers: Boolean) {
    if (ensureSettingsAreRemovedFromOtherManagers) {
      // storage could change, need to remove from old storages
      when {
        settings.isStoredInDotIdeaFolder -> {
          rcInArbitraryFileManager.removeRunConfiguration(settings)
          workspaceSchemeManager.removeScheme(settings)
        }
        settings.isStoredInArbitraryFileInProject -> {
          // path could change: need to remove and add again
          rcInArbitraryFileManager.removeRunConfiguration(settings, removeRunConfigOnlyIfFileNameChanged = true)
          projectSchemeManager.removeScheme(settings)
          workspaceSchemeManager.removeScheme(settings)
        }
        else -> {
          rcInArbitraryFileManager.removeRunConfiguration(settings)
          projectSchemeManager.removeScheme(settings)
        }
      }
    }

    // storage could change, need to make sure the RC is added to the corresponding scheme manager (no harm if it's already there)
    when {
      settings.isStoredInDotIdeaFolder -> projectSchemeManager.addScheme(settings)
      settings.isStoredInArbitraryFileInProject -> rcInArbitraryFileManager.addRunConfiguration(settings)
      else -> workspaceSchemeManager.addScheme(settings)
    }
  }

  override fun refreshUsagesList(profile: RunProfile) {
    if (profile !is RunConfiguration) {
      return
    }

    getSettings(profile)?.let {
      refreshUsagesList(it)
    }
  }

  private fun refreshUsagesList(settings: RunnerAndConfigurationSettings) {
    if (settings.isTemporary) {
      lock.write {
        recentlyUsedTemporaries.remove(settings)
        recentlyUsedTemporaries.add(0, settings)
        trimUsageListToLimit()
      }
    }
  }

  // call only under write lock
  private fun trimUsageListToLimit() {
    while (recentlyUsedTemporaries.size > config.recentsLimit) {
      recentlyUsedTemporaries.removeAt(recentlyUsedTemporaries.size - 1)
    }
  }

  fun checkRecentsLimit() {
    var removed: MutableList<RunnerAndConfigurationSettings>? = null
    lock.write {
      trimUsageListToLimit()

      var excess = idToSettings.values.count { it.isTemporary } - config.recentsLimit
      if (excess <= 0) {
        return
      }

      for (settings in idToSettings.values) {
        if (settings.isTemporary && !recentlyUsedTemporaries.contains(settings)) {
          if (removed == null) {
            removed = ArrayList()
          }
          removed!!.add(settings)
          if (--excess <= 0) {
            break
          }
        }
      }
    }

    removed?.let { removeConfigurations(it) }
  }

  @JvmOverloads
  fun setOrder(comparator: Comparator<RunnerAndConfigurationSettings>, isApplyAdditionalSortByTypeAndGroup: Boolean = true) {
    lock.write {
      listManager.setOrder(comparator, isApplyAdditionalSortByTypeAndGroup)
    }
  }

  override var selectedConfiguration: RunnerAndConfigurationSettings?
    get() {
      return lock.read {
        selectedConfigurationId?.let { idToSettings.get(it) }
      }
    }
    set(value) {
      fun isTheSame() = value?.uniqueID == selectedConfigurationId

      lock.read {
        if (isTheSame()) {
          return
        }
      }

      lock.write {
        if (isTheSame()) {
          return
        }

        val id = value?.uniqueID
        if (id != null && !idToSettings.containsKey(id)) {
          LOG.error("$id must be added before selecting")
        }
        selectedConfigurationId = id
      }

      eventPublisher.runConfigurationSelected(value)
    }

  internal fun isFileContainsRunConfiguration(file: VirtualFile): Boolean {
    return lock.read { rcInArbitraryFileManager.hasRunConfigsFromFile(file.path) }
  }

  internal fun selectConfigurationStoredInFile(file: VirtualFile) {
    val runConfigs = lock.read { rcInArbitraryFileManager.getRunConfigsFromFiles(listOf(file.path)) }
    runConfigs.find { idToSettings.containsKey(it.uniqueID) }?.let { selectedConfiguration = it }
  }

  fun requestSort() {
    lock.write {
      listManager.requestSort()
    }
  }

  override val allSettings: List<RunnerAndConfigurationSettings>
    get() {
      listManager.immutableSortedSettingsList?.let {
        return it
      }

      lock.write {
        return listManager.buildImmutableSortedSettingsList()
      }
    }

  override suspend fun save() {
    rcInArbitraryFileManager.saveRunConfigs(lock)
  }

  override fun getState(): Element {
    if (!isFirstLoadState.get()) {
      lock.read {
        val list = idToSettings.values.toList()
        list.managedOnly().forEach {
          listManager.checkIfDependenciesAreStable(configuration = it.configuration, list = list)
        }
      }
    }

    val element = Element("state")

    workspaceSchemeManager.save()

    lock.read {
      workspaceSchemeManagerProvider.writeState(element)

      if (idToSettings.size > 1) {
        selectedConfiguration?.let {
          element.setAttribute(SELECTED_ATTR, it.uniqueID)
        }

        listManager.writeOrder(element)
      }

      val recentList = ArrayList<String>()
      recentlyUsedTemporaries.managedOnly().forEach {
        recentList.add(it.uniqueID)
      }
      if (!recentList.isEmpty()) {
        val recent = Element(RECENT)
        element.addContent(recent)

        val listElement = Element("list")
        recent.addContent(listElement)
        for (id in recentList) {
          listElement.addContent(Element("item").setAttribute("itemvalue", id))
        }
      }
    }
    return element
  }

  fun writeContext(element: Element) {
    for (setting in allSettings) {
      if (setting.isTemporary) {
        element.addContent((setting as RunnerAndConfigurationSettingsImpl).writeScheme())
      }
    }

    selectedConfiguration?.let {
      element.setAttribute(SELECTED_ATTR, it.uniqueID)
    }
  }

  fun writeConfigurations(parentNode: Element, settings: Collection<RunnerAndConfigurationSettings>) {
    settings.forEach { parentNode.addContent((it as RunnerAndConfigurationSettingsImpl).writeScheme()) }
  }

  fun writeBeforeRunTasks(configuration: RunConfiguration): Element {
    val tasks = configuration.beforeRunTasks
    val methodElement = Element(METHOD)
    methodElement.setAttribute("v", "2")
    for (task in tasks) {
      val child = Element(OPTION)
      child.setAttribute(NAME_ATTR, task.providerId.toString())
      if (task is PersistentStateComponent<*>) {
        if (!task.isEnabled) {
          child.setAttribute("enabled", "false")
        }
        serializeStateInto(task, child)
      }
      else {
        @Suppress("DEPRECATION")
        task.writeExternal(child)
      }
      methodElement.addContent(child)
    }
    return methodElement
  }

  /**
   * Used by MPS. Do not use if not approved.
   */
  fun reloadSchemes() {
    var arbitraryFilePaths: Collection<String>
    lock.write {
      // not really required, but hot swap friendly - 1) factory is used a key, 2) developer can change some defaults.
      templateDifferenceHelper.clearCache()
      templateIdToConfiguration.clear()
      listManager.idToSettings.clear()
      arbitraryFilePaths = rcInArbitraryFileManager.clearAllAndReturnFilePaths()
      recentlyUsedTemporaries.clear()

      stringIdToBeforeRunProvider.drop()
    }

    workspaceSchemeManager.reload()
    projectSchemeManager.reload()
    reloadRunConfigsFromArbitraryFiles(arbitraryFilePaths)
  }

  private fun reloadRunConfigsFromArbitraryFiles(filePaths: Collection<String>) {
    updateRunConfigsFromArbitraryFiles(emptyList(), filePaths)
  }

  @VisibleForTesting
  protected open fun onFirstLoadingStarted() {
    SyntheticConfigurationTypeProvider.EP_NAME.point.addExtensionPointListener(
      object : ExtensionPointListener<SyntheticConfigurationTypeProvider> {
        override fun extensionAdded(extension: SyntheticConfigurationTypeProvider, pluginDescriptor: PluginDescriptor) {
          extension.configurationTypes
        }
      }, true, this)
  }

  @VisibleForTesting
  protected open fun onFirstLoadingFinished() {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        if (event.getChanges(ContentRootEntity::class.java).any() || event.getChanges(SourceRootEntity::class.java).any()) {
          clearSelectedConfigurationIcon()
          deleteRunConfigsFromArbitraryFilesNotWithinProjectContent()
        }
      }
    })

    @Suppress("TestOnlyProblems")
    if (ProjectManagerImpl.isLight(project)) {
      return
    }

    ConfigurationType.CONFIGURATION_TYPE_EP.addExtensionPointListener(object : ExtensionPointListener<ConfigurationType> {
      override fun extensionAdded(extension: ConfigurationType, pluginDescriptor: PluginDescriptor) {
        idToType.drop()
        project.stateStore.reloadState(RunManagerImpl::class.java)
      }

      override fun extensionRemoved(extension: ConfigurationType, pluginDescriptor: PluginDescriptor) {
        idToType.drop()
        for (settings in idToSettings.values) {
          settings as RunnerAndConfigurationSettingsImpl
          if (settings.type == extension) {
            val configuration = UnknownConfigurationType.getInstance().createTemplateConfiguration(project)
            configuration.name = settings.configuration.name
            settings.setConfiguration(configuration)
          }
        }

        lock.write {
          templateIdToConfiguration.values.removeIf(java.util.function.Predicate { it.type == extension })
        }
      }
    }, this)

    ProgramRunner.PROGRAM_RUNNER_EP.addExtensionPointListener(object : ExtensionPointListener<ProgramRunner<*>> {
      override fun extensionRemoved(extension: ProgramRunner<*>, pluginDescriptor: PluginDescriptor) {
        for (settings in allSettings) {
          (settings as RunnerAndConfigurationSettingsImpl).handleRunnerRemoved(extension)
        }
      }
    }, this)
  }

  override fun noStateLoaded() {
    val isFirstLoadState = isFirstLoadState.getAndSet(false)
    if (isFirstLoadState) {
      loadRunConfigsFromArbitraryFiles()
      onFirstLoadingStarted()
    }

    loadSharedRunConfigurations()
    runConfigurationFirstLoaded()
    eventPublisher.stateLoaded(this, isFirstLoadState)

    if (isFirstLoadState) {
      onFirstLoadingFinished()
    }
  }

  override fun loadState(parentNode: Element) {
    config.migrateToAdvancedSettings()
    val isFirstLoadState = isFirstLoadState.compareAndSet(true, false)
    val oldSelectedConfigurationId = if (isFirstLoadState) null else selectedConfigurationId
    if (isFirstLoadState) {
      loadRunConfigsFromArbitraryFiles()
      onFirstLoadingStarted()
    }
    else {
      clear(false)
    }

    val nameGenerator = UniqueNameGenerator()
    workspaceSchemeManagerProvider.load(parentNode) { element ->
      var schemeKey: String? = element.getAttributeValue("name")
      if (schemeKey == "<template>" || schemeKey == null) {
        // scheme name must be unique
        element.getAttributeValue("type")?.let {
          if (schemeKey == null) {
            schemeKey = "<template>"
          }
          schemeKey += ", type: $it"
        }
      }
      else {
        val typeId = element.getAttributeValue("type")
        if (typeId == null) {
          LOG.warn("typeId is null for '$schemeKey'")
        }
        schemeKey = "${typeId ?: "unknown"}-$schemeKey"
      }

      // in case if broken configuration, do not fail, generate name
      if (schemeKey == null) {
        schemeKey = nameGenerator.generateUniqueName("Unnamed")
      }
      else {
        schemeKey = "$schemeKey!!, factoryName: ${element.getAttributeValue("factoryName", "")}"
        nameGenerator.addExistingName(schemeKey!!)
      }
      schemeKey!!
    }

    workspaceSchemeManager.reload()

    lock.write {
      recentlyUsedTemporaries.clear()
      val recentListElement = parentNode.getChild(RECENT)?.getChild("list")
      if (recentListElement != null) {
        for (id in recentListElement.getChildren("item").mapNotNull { it.getAttributeValue("itemvalue") }) {
          idToSettings.get(id)?.let {
            recentlyUsedTemporaries.add(it)
          }
        }
      }

      selectedConfigurationId = parentNode.getAttributeValue(SELECTED_ATTR)
    }

    if (isFirstLoadState) {
      loadSharedRunConfigurations()
    }

    // apply order after loading shared RC
    lock.write {
      listManager.readCustomOrder(parentNode)
    }

    runConfigurationFirstLoaded()
    fireBeforeRunTasksUpdated()

    if (oldSelectedConfigurationId != null && oldSelectedConfigurationId != selectedConfigurationId) {
      eventPublisher.runConfigurationSelected(selectedConfiguration)
    }

    eventPublisher.stateLoaded(this, isFirstLoadState)

    if (isFirstLoadState) {
      onFirstLoadingFinished()
    }
  }

  private fun loadSharedRunConfigurations() {
    if (schemeManagerIprProvider == null) {
      projectSchemeManager.loadSchemes()
    }
    else {
      project.service<IprRunManagerImpl>().lastLoadedState.getAndSet(null)?.let { data ->
        schemeManagerIprProvider.load(data)
        projectSchemeManager.reload()
      }
    }
  }

  private fun runConfigurationFirstLoaded() {
    if (project.isDefault) {
      return
    }

    if (selectedConfiguration == null) {
      val runConfigIdToSelect = selectedConfigurationId.nullize()

      selectAnyConfiguration()

      // More run configurations may get loaded later by RunConfigurationInArbitraryFileScanner.
      // We may need to update the selected RC when it's done.
      if (runConfigIdToSelect != null || selectedConfiguration == null) {
        updateSelectedRunConfigWhenFileScannerIsDone(runConfigIdToSelect)
      }
    }
  }

  private fun selectAnyConfiguration() {
    selectedConfiguration = allSettings.firstOrNull { it.type.isManaged }
  }

  private fun updateSelectedRunConfigWhenFileScannerIsDone(runConfigIdToSelect: String?) {
    val currentSelectedConfigId = selectedConfigurationId

    (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.Default) {
      project.serviceAsync<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()

      // RunConfigurationInArbitraryFileScanner has finished its initial scanning, all RCs are loaded.
      // Now we can set the right RC as 'selected' in the RC combo box.
      // EDT is needed to avoid deadlock with ExecutionTargetManagerImpl or similar implementations of runConfigurationSelected()
      withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        val runConfigToSelect = lock.read {
          // don't change the selected RC if it has been already changed and is not null
          if (currentSelectedConfigId != selectedConfigurationId && selectedConfigurationId != null) return@read null
          // select the 'correct' RC if it is available
          runConfigIdToSelect?.let { idToSettings[runConfigIdToSelect] }?.let { return@read it }
          // select any RC if none is selected
          if (selectedConfiguration == null) return@read allSettings.firstOrNull { it.type.isManaged }
          return@read null
        }
        runConfigToSelect?.let { selectedConfiguration = it }
      }
    }
  }

  fun readContext(parentNode: Element) {
    var selectedConfigurationId = parentNode.getAttributeValue(SELECTED_ATTR)

    for (element in parentNode.children) {
      val config = loadConfiguration(element, false)
      if (selectedConfigurationId == null && element.getAttributeBooleanValue(SELECTED_ATTR)) {
        selectedConfigurationId = config.uniqueID
      }
    }

    this.selectedConfigurationId = selectedConfigurationId

    eventPublisher.runConfigurationSelected(selectedConfiguration)
  }

  override fun hasSettings(settings: RunnerAndConfigurationSettings) = lock.read { idToSettings.get(settings.uniqueID) == settings }

  private fun findExistingConfigurationId(settings: RunnerAndConfigurationSettings): String? {
    for ((key, value) in idToSettings) {
      if (value === settings) {
        return key
      }
    }
    return null
  }

  // used by MPS, don't delete
  fun clearAll() {
    clear(true)
    idToType.drop()
  }

  private fun clear(allConfigurations: Boolean) {
    val removedConfigurations = lock.write {
      listManager.immutableSortedSettingsList = null

      val configurations = if (allConfigurations) {
        val configurations = idToSettings.values.toList()

        idToSettings.clear()
        selectedConfigurationId = null

        configurations
      }
      else {
        val configurations = ArrayList<RunnerAndConfigurationSettings>()
        val iterator = idToSettings.values.iterator()
        for (configuration in iterator) {
          if (configuration.isTemporary || !configuration.isShared) {
            iterator.remove()

            configurations.add(configuration)
          }
        }

        selectedConfigurationId?.let {
          if (idToSettings.containsKey(it)) {
            selectedConfigurationId = null
          }
        }

        configurations
      }

      templateIdToConfiguration.clear()
      recentlyUsedTemporaries.clear()
      configurations
    }

    iconAndInvalidCache.clear()
    val eventPublisher = eventPublisher
    removedConfigurations.forEach { eventPublisher.runConfigurationRemoved(it) }
  }

  fun loadConfiguration(element: Element, isStoredInDotIdeaFolder: Boolean): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this)
    LOG.runAndLogException {
      settings.readExternal(element, isStoredInDotIdeaFolder)
    }
    addConfiguration(element, settings)
    return settings
  }

  internal fun addConfiguration(element: Element, settings: RunnerAndConfigurationSettingsImpl, isCheckRecentsLimit: Boolean = true) {
    doAddConfiguration(settings, isCheckRecentsLimit)
    if (element.getAttributeBooleanValue(SELECTED_ATTR) && !settings.isTemplate) {
      // to support old style
      selectedConfiguration = settings
    }
  }

  fun readBeforeRunTasks(element: Element?, settings: RunnerAndConfigurationSettings, configuration: RunConfiguration) {
    var result: MutableList<BeforeRunTask<*>>? = null
    if (element != null) {
      for (methodElement in element.getChildren(OPTION)) {
        val key = methodElement.getAttributeValue(NAME_ATTR) ?: continue
        val provider = stringIdToBeforeRunProvider.value.getOrPut(key) {
          UnknownBeforeRunTaskProvider(key)
        }
        val beforeRunTask = provider.createTask(configuration) ?: continue
        if (beforeRunTask is PersistentStateComponent<*>) {
          // for PersistentStateComponent we don't write default value for enabled, so, set it to true explicitly
          beforeRunTask.isEnabled = true
          deserializeAndLoadState(beforeRunTask, methodElement)
        }
        else {
          @Suppress("DEPRECATION")
          beforeRunTask.readExternal(methodElement)
        }
        if (result == null) {
          result = ArrayList()
        }
        result.add(beforeRunTask)
      }
    }

    if (element?.getAttributeValue("v") == null) {
      if (settings.isTemplate) {
        if (result.isNullOrEmpty()) {
          configuration.beforeRunTasks = getHardcodedBeforeRunTasks(configuration, configuration.factory!!)
          return
        }
      }
      else {
        configuration.beforeRunTasks = getEffectiveBeforeRunTaskList(ownTasks = result ?: emptyList(),
                                                                     templateTasks = getConfigurationTemplate(configuration.factory!!).configuration.beforeRunTasks,
                                                                     ownIsOnlyEnabled = true,
                                                                     isDisableTemplateTasks = false)
        return
      }
    }

    configuration.beforeRunTasks = result ?: emptyList()
  }

  fun getFactory(
    typeId: String?,
    factoryId: String?,
    checkUnknown: Boolean = false,
  ): ConfigurationFactory {
    var factory = idToType.value.get(typeId)?.let { getFactory(it, factoryId) }
    if (factory != null) {
      return factory
    }

    factory = UnknownConfigurationType.getInstance()
    if (checkUnknown && typeId != null) {
      UnknownFeaturesCollector.getInstance(project).registerUnknownFeature(UnknownFeature(
        CONFIGURATION_TYPE_FEATURE_ID,
        ExecutionBundle.message("plugins.advertiser.feature.run.configuration"),
        typeId,
      ))
    }
    return factory
  }

  fun getFactory(type: ConfigurationType, factoryId: String?): ConfigurationFactory? {
    return when (type) {
      is UnknownConfigurationType -> type.configurationFactories.firstOrNull()
      is SimpleConfigurationType -> type
      else -> type.configurationFactories.firstOrNull { factoryId == null || it.id == factoryId }
    }
  }

  override fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?) {
    if (tempConfiguration == null) {
      return
    }

    tempConfiguration.isTemporary = true
    addConfiguration(tempConfiguration)
    if (shouldSetRunConfigurationFromContext()) {
      selectedConfiguration = tempConfiguration
    }
  }

  override val tempConfigurationsList: List<RunnerAndConfigurationSettings>
    get() = allSettings.filter { it.isTemporary }

  override fun makeStable(settings: RunnerAndConfigurationSettings) {
    settings.isTemporary = false
    doMakeStable(settings)
    fireRunConfigurationChanged(settings)
  }

  private fun doMakeStable(settings: RunnerAndConfigurationSettings) {
    lock.write {
      recentlyUsedTemporaries.remove(settings)
      listManager.afterMakeStable()
    }
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(taskProviderId: Key<T>): List<T> {
    val tasks = ArrayList<T>()
    val checkedTemplates = ArrayList<RunnerAndConfigurationSettings>()
    lock.read {
      for (settings in allSettings) {
        val configuration = settings.configuration
        for (task in getBeforeRunTasks(configuration)) {
          if (task.isEnabled && task.providerId === taskProviderId) {
            @Suppress("UNCHECKED_CAST")
            tasks.add(task as T)
          }
          else {
            val template = getConfigurationTemplate(configuration.factory!!)
            if (!checkedTemplates.contains(template)) {
              checkedTemplates.add(template)
              for (templateTask in getBeforeRunTasks(template.configuration)) {
                if (templateTask.isEnabled && templateTask.providerId === taskProviderId) {
                  @Suppress("UNCHECKED_CAST")
                  tasks.add(templateTask as T)
                }
              }
            }
          }
        }
      }
    }
    return tasks
  }

  override fun getConfigurationIcon(settings: RunnerAndConfigurationSettings, withLiveIndicator: Boolean): Icon {
    val uniqueId = settings.uniqueID
    if (selectedConfiguration?.uniqueID == uniqueId) {
      iconAndInvalidCache.checkValidity(uniqueId, project)
    }
    var icon = iconAndInvalidCache.get(uniqueId, settings, project)
    if (withLiveIndicator) {
      val runningDescriptors = ExecutionManagerImpl.getInstanceIfCreated(project)
                                 ?.getRunningDescriptors(Condition { it === settings })
                               ?: emptyList()
      when {
        runningDescriptors.size == 1 -> icon =
          if (ExperimentalUI.isNewUI()) newUiRunningIcon(icon) else ExecutionUtil.getLiveIndicator(icon)
        runningDescriptors.size > 1 -> icon =
          if (ExperimentalUI.isNewUI()) newUiRunningIcon(icon) else IconUtil.addText(icon, runningDescriptors.size.toString())
      }
    }
    return icon
  }

  fun isInvalidInCache(configuration: RunConfiguration): Boolean {
    findSettings(configuration)?.let {
      return iconAndInvalidCache.isInvalid(it.uniqueID)
    }
    return false
  }

  fun getConfigurationById(id: String): RunnerAndConfigurationSettings? = lock.read { idToSettings.get(id) }

  override fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings? {
    if (name == null) {
      return null
    }
    return allSettings.firstOrNull { it.name == name }
  }

  override fun findSettings(configuration: RunConfiguration): RunnerAndConfigurationSettings? {
    val id = RunnerAndConfigurationSettingsImpl.getUniqueIdFor(configuration)
    lock.read {
      return idToSettings.get(id)
    }
  }

  override fun isTemplate(configuration: RunConfiguration): Boolean {
    lock.read {
      return templateIdToConfiguration.values.any { it.configuration === configuration }
    }
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(settings: RunConfiguration, taskProviderId: Key<T>): List<T> {
    if (settings is WrappingRunConfiguration<*>) {
      return getBeforeRunTasks(settings.peer, taskProviderId)
    }

    var result: MutableList<T>? = null
    for (task in getBeforeRunTasks(settings)) {
      if (task.providerId === taskProviderId) {
        if (result == null) {
          result = ArrayList()
        }
        @Suppress("UNCHECKED_CAST")
        result.add(task as T)
      }
    }
    return result ?: emptyList()
  }

  override fun getBeforeRunTasks(configuration: RunConfiguration) = doGetBeforeRunTasks(configuration)

  fun shareConfiguration(settings: RunnerAndConfigurationSettings, value: Boolean) {
    if (settings.isShared == value) {
      return
    }

    if (value && settings.isTemporary) {
      doMakeStable(settings)
    }

    if (value) {
      settings.storeInDotIdeaFolder()
    }
    else {
      settings.storeInLocalWorkspace()
    }

    fireRunConfigurationChanged(settings)
  }

  override fun setBeforeRunTasks(configuration: RunConfiguration, tasks: List<BeforeRunTask<*>>) {
    if (!configuration.type.isManaged) {
      return
    }

    configuration.beforeRunTasks = tasks
    fireBeforeRunTasksUpdated()
  }

  fun fireBeginUpdate() {
    eventPublisher.beginUpdate()
  }

  fun fireEndUpdate() {
    eventPublisher.endUpdate()
  }

  fun fireRunConfigurationChanged(settings: RunnerAndConfigurationSettings) {
    eventPublisher.runConfigurationChanged(settings, null)
  }

  fun fireBeforeRunTasksUpdated() {
    eventPublisher.beforeRunTasksChanged()
  }

  override fun removeConfiguration(settings: RunnerAndConfigurationSettings?) {
    if (settings != null) {
      removeConfigurations(listOf(settings))
    }
  }

  fun removeConfigurations(toRemove: Collection<RunnerAndConfigurationSettings>) {
    removeConfigurations(_toRemove = toRemove, deleteFileIfStoredInArbitraryFile = true)
  }

  internal fun removeConfigurations(@Suppress("LocalVariableName") _toRemove: Collection<RunnerAndConfigurationSettings>,
                                    deleteFileIfStoredInArbitraryFile: Boolean = true,
                                    onSchemeManagerDeleteEvent: Boolean = false) {
    if (_toRemove.isEmpty()) {
      return
    }

    val changedSettings = ArrayList<RunnerAndConfigurationSettings>()
    val removed = ArrayList<RunnerAndConfigurationSettings>()
    var selectedConfigurationWasRemoved = false

    lock.write {
      val runConfigsToRemove = removeTemplatesAndReturnRemaining(_toRemove, deleteFileIfStoredInArbitraryFile, onSchemeManagerDeleteEvent)
      val runConfigsToRemoveButNotYetRemoved = runConfigsToRemove.toMutableList()

      listManager.immutableSortedSettingsList = null

      val iterator = idToSettings.values.iterator()
      for (settings in iterator) {
        if (runConfigsToRemove.contains(settings)) {
          if (selectedConfigurationId == settings.uniqueID) {
            selectedConfigurationWasRemoved = true
          }

          runConfigsToRemoveButNotYetRemoved.remove(settings)
          iterator.remove()
          removeSettingsFromCorrespondingManager(settings as RunnerAndConfigurationSettingsImpl, deleteFileIfStoredInArbitraryFile)

          recentlyUsedTemporaries.remove(settings)
          removed.add(settings)
          iconAndInvalidCache.remove(settings.uniqueID)
        }
        else {
          var isChanged = false
          val otherConfiguration = settings.configuration
          val newList = otherConfiguration.beforeRunTasks.nullize()?.toMutableSmartList() ?: continue
          val beforeRunTaskIterator = newList.iterator()
          for (task in beforeRunTaskIterator) {
            if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask &&
                runConfigsToRemove.firstOrNull { task.isMySettings(it) } != null) {
              beforeRunTaskIterator.remove()
              isChanged = true
              changedSettings.add(settings)
            }
          }
          if (isChanged) {
            otherConfiguration.beforeRunTasks = newList
          }
        }
      }

      for (settings in runConfigsToRemoveButNotYetRemoved) {
        // At this point, runConfigsToRemoveButNotYetRemoved contains entries that haven't been there in idToSettings map at all.
        // This may happen, for example, if some Run Configuration appears twice or more in a *.run.xml file (e.g., as a merge result).
        removeSettingsFromCorrespondingManager(settings as RunnerAndConfigurationSettingsImpl, deleteFileIfStoredInArbitraryFile)
      }
    }

    if (selectedConfigurationWasRemoved) {
      selectAnyConfiguration()
    }

    removed.forEach { eventPublisher.runConfigurationRemoved(it) }
    changedSettings.forEach { eventPublisher.runConfigurationChanged(it, null) }
  }

  private fun removeTemplatesAndReturnRemaining(toRemove: Collection<RunnerAndConfigurationSettings>,
                                                deleteFileIfStoredInArbitraryFile: Boolean,
                                                onSchemeManagerDeleteEvent: Boolean): Collection<RunnerAndConfigurationSettings> {
    val result = mutableListOf<RunnerAndConfigurationSettings>()

    for (settings in toRemove) {
      if (settings.isTemplate) {
        templateIdToConfiguration.remove(getFactoryKey(settings.factory))
        if (!onSchemeManagerDeleteEvent) {
          removeSettingsFromCorrespondingManager(settings as RunnerAndConfigurationSettingsImpl, deleteFileIfStoredInArbitraryFile)
        }
      }
      else {
        result.add(settings)
      }
    }

    return result
  }

  private fun removeSettingsFromCorrespondingManager(settings: RunnerAndConfigurationSettingsImpl,
                                                     deleteFileIfStoredInArbitraryFile: Boolean) {
    when {
      settings.isStoredInDotIdeaFolder -> projectSchemeManager.removeScheme(settings)
      settings.isStoredInArbitraryFileInProject -> {
        rcInArbitraryFileManager.removeRunConfiguration(settings,
                                                        removeRunConfigOnlyIfFileNameChanged = false,
                                                        deleteContainingFile = deleteFileIfStoredInArbitraryFile)
      }
      else -> workspaceSchemeManager.removeScheme(settings)
    }
  }

  @TestOnly
  fun getTemplateIdToConfiguration(): Map<String, RunnerAndConfigurationSettingsImpl> {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      throw IllegalStateException("test only")
    }
    return templateIdToConfiguration
  }

  fun copyTemplatesToProjectFromTemplate(project: Project) {
    if (workspaceSchemeManager.isEmpty) {
      return
    }

    val otherRunManager = getInstanceImpl(project)
    workspaceSchemeManagerProvider.copyIfNotExists(otherRunManager.workspaceSchemeManagerProvider)
    otherRunManager.lock.write {
      otherRunManager.templateIdToConfiguration.clear()
    }
    otherRunManager.workspaceSchemeManager.reload()
  }

  private fun newUiRunningIcon(icon: Icon) = IconManager.getInstance().withIconBadge(icon, JBUI.CurrentTheme.IconBadge.SUCCESS)
}

@get:ApiStatus.Internal
const val PROJECT_RUN_MANAGER_COMPONENT_NAME: String = "ProjectRunConfigurationManager"

@Service(Service.Level.PROJECT)
@State(name = PROJECT_RUN_MANAGER_COMPONENT_NAME, useLoadedStateAsExisting = false /* ProjectRunConfigurationManager is used only for IPR,
avoid relative cost call getState */)
private class IprRunManagerImpl(private val project: Project) : PersistentStateComponent<Element> {
  val lastLoadedState = AtomicReference<Element>()

  override fun getState(): Element? {
    val iprProvider = RunManagerImpl.getInstanceImpl(project).schemeManagerIprProvider ?: return null
    val result = Element("state")
    iprProvider.writeState(result)
    return result
  }

  override fun loadState(state: Element) {
    lastLoadedState.set(state)
  }
}

private fun getNameWeight(n1: String) = if (n1.startsWith("<template> of ") || n1.startsWith("_template__ ")) 0 else 1

internal fun doGetBeforeRunTasks(configuration: RunConfiguration): List<BeforeRunTask<*>> {
  return when (configuration) {
    is WrappingRunConfiguration<*> -> doGetBeforeRunTasks(configuration.peer)
    else -> configuration.beforeRunTasks
  }
}

internal fun RunConfiguration.cloneBeforeRunTasks() {
  beforeRunTasks = doGetBeforeRunTasks(this).mapSmart { it.clone() }
}

@ApiStatus.Internal
fun callNewConfigurationCreated(factory: ConfigurationFactory, configuration: RunConfiguration) {
  @Suppress("UNCHECKED_CAST", "DEPRECATION")
  (factory as? com.intellij.execution.configuration.ConfigurationFactoryEx<RunConfiguration>)?.onNewConfigurationCreated(configuration)
  (configuration as? ConfigurationCreationListener)?.onNewConfigurationCreated()
}

private fun getFactoryKey(factory: ConfigurationFactory): String {
  return when (factory.type) {
    is SimpleConfigurationType -> factory.type.id
    else -> "${factory.type.id}.${factory.id}"
  }
}

// todo convert ChooseRunConfigurationPopup to kotlin
internal fun createFlatSettingsList(project: Project): List<ChooseRunConfigurationPopup.ItemWrapper<*>> {
  return (RunManager.getInstanceIfCreated(project) as RunManagerImpl? ?: return emptyList()).getConfigurationsGroupedByTypeAndFolder(false)
    .values
    .asSequence()
    .flatMap { map ->
      map.values.flatten()
    }
    .map { ChooseRunConfigurationPopup.ItemWrapper.wrap(project, it)
    }
    .toList()
}
