// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.ProjectTopics
import com.intellij.configurationStore.*
import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.project.isDirectoryBased
import com.intellij.util.*
import com.intellij.util.containers.*
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val SELECTED_ATTR = "selected"
internal const val METHOD = "method"
private const val OPTION = "option"
private const val RECENT = "recent_temporary"

// open for Upsource (UpsourceRunManager overrides to disable loadState (empty impl))
@State(name = "RunManager", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE, useSaveThreshold = ThreeState.NO))])
open class RunManagerImpl(internal val project: Project) : RunManagerEx(), PersistentStateComponent<Element>, Disposable {
  companion object {
    const val CONFIGURATION = "configuration"
    const val NAME_ATTR = "name"

    internal val LOG = logger<RunManagerImpl>()

    @JvmStatic
    fun getInstanceImpl(project: Project) = RunManager.getInstance(project) as RunManagerImpl

    @JvmStatic
    fun canRunConfiguration(environment: ExecutionEnvironment): Boolean {
      return environment.runnerAndConfigurationSettings?.let { canRunConfiguration(it, environment.executor) } ?: false
    }

    @JvmStatic
    fun canRunConfiguration(configuration: RunnerAndConfigurationSettings, executor: Executor): Boolean {
      try {
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

  private val lock = ReentrantReadWriteLock()

  private val idToType = LinkedHashMap<String, ConfigurationType>()

  @Suppress("LeakingThis")
  private val listManager = RunConfigurationListManagerHelper(this)

  private val templateIdToConfiguration = THashMap<String, RunnerAndConfigurationSettingsImpl>()
  // template configurations are not included here
  private val idToSettings: LinkedHashMap<String, RunnerAndConfigurationSettings>
    get() = listManager.idToSettings

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  private var selectedConfigurationId: String? = null

  private val iconCache = TimedIconCache()
  private val _config by lazy { RunManagerConfig(PropertiesComponent.getInstance(project)) }

  private val recentlyUsedTemporaries = ArrayList<RunnerAndConfigurationSettings>()

  // templates should be first because to migrate old before run list to effective, we need to get template before run task
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

  internal val schemeManagerIprProvider = if (project.isDirectoryBased) null else SchemeManagerIprProvider("configuration")

  @Suppress("LeakingThis")
  private val templateDifferenceHelper = TemplateDifferenceHelper(this)

  @Suppress("LeakingThis")
  private val workspaceSchemeManager = SchemeManagerFactory.getInstance(project).create("workspace",
                                                                                        RunConfigurationSchemeManager(this, templateDifferenceHelper,
                                                                                                                      isShared = false,
                                                                                                                      isWrapSchemeIntoComponentElement = false),
                                                                                        streamProvider = workspaceSchemeManagerProvider,
                                                                                        isAutoSave = false)

  @Suppress("LeakingThis")
  private var projectSchemeManager = SchemeManagerFactory.getInstance(project).create("runConfigurations",
                                                                                      RunConfigurationSchemeManager(this, templateDifferenceHelper,
                                                                                                                    isShared = true,
                                                                                                                    isWrapSchemeIntoComponentElement = schemeManagerIprProvider == null),
                                                                                      schemeNameToFileName = OLD_NAME_CONVERTER,
                                                                                      streamProvider = schemeManagerIprProvider)

  private val isFirstLoadState = AtomicBoolean(true)

  private val stringIdToBeforeRunProvider by lazy {
    val result = ContainerUtil.newConcurrentMap<String, BeforeRunTaskProvider<*>>()
    for (provider in BeforeRunTaskProvider.EXTENSION_POINT_NAME.getExtensions(project)) {
      result.put(provider.id.toString(), provider)
    }
    result
  }

  internal val eventPublisher: RunManagerListener
    get() = project.messageBus.syncPublisher(RunManagerListener.TOPIC)

  init {
    initializeConfigurationTypes(ConfigurationType.CONFIGURATION_TYPE_EP.extensions)
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        selectedConfiguration?.let {
          iconCache.remove(it.uniqueID)
        }
      }
    })
  }

  // separate method needed for tests
  fun initializeConfigurationTypes(factories: Array<ConfigurationType>) {
    val types = factories.toMutableList()
    types.sortBy { it.displayName }
    types.add(UnknownConfigurationType.INSTANCE)
    lock.write {
      idToType.clear()
      for (type in types) {
        idToType.put(type.id, type)
      }
    }
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
    if (!settings.isShared) {
      shareConfiguration(settings, template.isShared)
    }
    configuration.beforeRunTasks = template.configuration.beforeRunTasks
    return settings
  }

  override fun dispose() {
    lock.write { templateIdToConfiguration.clear() }
  }

  override fun getConfig() = _config

  override val configurationFactories by lazy { idToType.values.toTypedArray() }

  override val configurationFactoriesWithoutUnknown: List<ConfigurationType>
    get() = idToType.values.filterSmart { it.isManaged }

  /**
   * Template configuration is not included
   */
  override fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration> {
    var result: MutableList<RunConfiguration>? = null
    for (settings in allSettings) {
      val configuration = settings.configuration
      if (type.id == configuration.type.id) {
        if (result == null) {
          result = SmartList<RunConfiguration>()
        }
        result.add(configuration)
      }
    }
    return result ?: emptyList()
  }

  override val allConfigurationsList: List<RunConfiguration>
    get() = allSettings.mapSmart { it.configuration }

  fun getSettings(configuration: RunConfiguration) = allSettings.firstOrNull { it.configuration === configuration } as? RunnerAndConfigurationSettingsImpl

  override fun getConfigurationSettingsList(type: ConfigurationType) = allSettings.filterSmart { it.type.id == type.id }

  override fun getStructure(type: ConfigurationType): Map<String, List<RunnerAndConfigurationSettings>> {
    val result = LinkedHashMap<String?, MutableList<RunnerAndConfigurationSettings>>()
    val typeList = SmartList<RunnerAndConfigurationSettings>()
    val settings = getConfigurationSettingsList(type)
    for (setting in settings) {
      val folderName = setting.folderName
      if (folderName == null) {
        typeList.add(setting)
      }
      else {
        result.getOrPut(folderName) { SmartList() }.add(setting)
      }
    }
    result.put(null, Collections.unmodifiableList(typeList))
    return Collections.unmodifiableMap(result)
  }

  override fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettingsImpl {
    val key = "${factory.type.id}.${factory.id}"
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
    val template = RunnerAndConfigurationSettingsImpl(this, configuration,
                                                      isTemplate = true,
                                                      isSingleton = factory.isConfigurationSingletonByDefault)
    if (configuration is UnknownRunConfiguration) {
      configuration.isDoNotStore = true
    }
    configuration.beforeRunTasks = getHardcodedBeforeRunTasks(configuration, factory)
    return template
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings) {
    doAddConfiguration(settings, isCheckRecentsLimit = true)
  }

  private fun doAddConfiguration(settings: RunnerAndConfigurationSettings, isCheckRecentsLimit: Boolean) {
    val newId = settings.uniqueID
    var existingId: String? = null
    lock.write {
      listManager.immutableSortedSettingsList = null

      // https://youtrack.jetbrains.com/issue/IDEA-112821
      // we should check by instance, not by id (todo is it still relevant?)
      existingId = if (idToSettings.get(newId) === settings) newId else findExistingConfigurationId(settings)
      existingId?.let {
        if (newId != it) {
          idToSettings.remove(it)

          if (selectedConfigurationId == it) {
            selectedConfigurationId = newId
          }
        }
      }

      idToSettings.put(newId, settings)

      if (existingId == null) {
        refreshUsagesList(settings)
      }
      else {
        (if (settings.isShared) workspaceSchemeManager else projectSchemeManager).removeScheme(
          settings as RunnerAndConfigurationSettingsImpl)
      }

      // scheme level can be changed (workspace -> project), so, ensure that scheme is added to corresponding scheme manager (if exists, doesn't harm)
      settings.schemeManager?.addScheme(settings as RunnerAndConfigurationSettingsImpl)
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

  private val RunnerAndConfigurationSettings.schemeManager: SchemeManager<RunnerAndConfigurationSettingsImpl>?
    get() = if (isShared) projectSchemeManager else workspaceSchemeManager

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
        trimUsagesListToLimit()
      }
    }
  }

  // call only under write lock
  private fun trimUsagesListToLimit() {
    while (recentlyUsedTemporaries.size > config.recentsLimit) {
      recentlyUsedTemporaries.removeAt(recentlyUsedTemporaries.size - 1)
    }
  }

  fun checkRecentsLimit() {
    var removed: MutableList<RunnerAndConfigurationSettings>? = null
    lock.write {
      trimUsagesListToLimit()

      var excess = idToSettings.values.count { it.isTemporary } - config.recentsLimit
      if (excess <= 0) {
        return
      }

      for (settings in idToSettings.values) {
        if (settings.isTemporary && !recentlyUsedTemporaries.contains(settings)) {
          if (removed == null) {
            removed = SmartList<RunnerAndConfigurationSettings>()
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

  fun setOrder(comparator: Comparator<RunnerAndConfigurationSettings>) {
    lock.write {
      listManager.setOrder(comparator)
    }
  }

  override var selectedConfiguration: RunnerAndConfigurationSettings?
    get() = selectedConfigurationId?.let { lock.read { idToSettings.get(it) } }
    set(value) {
      if (value?.uniqueID == selectedConfigurationId) {
        return
      }

      selectedConfigurationId = value?.uniqueID
      eventPublisher.runConfigurationSelected()
    }

  fun requestSort() {
    lock.write {
      listManager.requestSort()
      allSettings
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

  override fun getState(): Element {
    if (!isFirstLoadState.get()) {
      lock.read {
        val list = idToSettings.values.toList()
        list.forEachManaged {
          listManager.checkIfDependenciesAreStable(it.configuration, list)
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

        val listElement = Element("list")
        idToSettings.values.forEachManaged {
          listElement.addContent(Element("item").setAttribute("itemvalue", it.uniqueID))
        }

        if (!listElement.isEmpty()) {
          element.addContent(listElement)
        }
      }

      val recentList = SmartList<String>()
      recentlyUsedTemporaries.forEachManaged {
        recentList.add(it.uniqueID)
      }
      if (!recentList.isEmpty()) {
        val recent = Element(RECENT)
        element.addContent(recent)

        val listElement = recent.element("list")
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

  internal fun writeBeforeRunTasks(configuration: RunConfiguration): Element? {
    val tasks = configuration.beforeRunTasks
    val methodElement = Element(METHOD)
    methodElement.attribute("v", "2")
    for (task in tasks) {
      val child = Element(OPTION)
      child.setAttribute(NAME_ATTR, task.providerId.toString())
      if (task is PersistentStateComponent<*>) {
        if (!task.isEnabled) {
          child.setAttribute("enabled", "false")
        }
        task.serializeStateInto(child)
      }
      else {
        @Suppress("DEPRECATION")
        task.writeExternal(child)
      }
      methodElement.addContent(child)
    }
    return methodElement
  }

  @Suppress("unused")
  /**
   * used by MPS. Do not use if not approved.
   */
  fun reloadSchemes() {
    lock.write {
      // not really required, but hot swap friendly - 1) factory is used a key, 2) developer can change some defaults.
      templateDifferenceHelper.clearCache()
      templateIdToConfiguration.clear()
      listManager.idToSettings.clear()
      recentlyUsedTemporaries.clear()
    }
    workspaceSchemeManager.reload()
    projectSchemeManager.reload()
  }

  override fun noStateLoaded() {
    isFirstLoadState.set(false)
    loadSharedRunConfigurations()
    runConfigurationFirstLoaded()
    eventPublisher.stateLoaded()
  }

  override fun loadState(parentNode: Element) {
    val oldSelectedConfigurationId: String?
    val isFirstLoadState = isFirstLoadState.compareAndSet(true, false)
    if (isFirstLoadState) {
      oldSelectedConfigurationId = null
    }
    else {
      oldSelectedConfigurationId = selectedConfigurationId
      clear(false)
    }

    val nameGenerator = UniqueNameGenerator()
    workspaceSchemeManagerProvider.load(parentNode) {
      var schemeKey: String? = it.getAttributeValue("name")
      if (schemeKey == "<template>" || schemeKey == null) {
        // scheme name must be unique
        it.getAttributeValue("type")?.let {
          if (schemeKey == null) {
            schemeKey = "<template>"
          }
          schemeKey += ", type: ${it}"
        }
      }
      else if (schemeKey != null) {
        val typeId = it.getAttributeValue("type")
        if (typeId == null) {
          LOG.warn("typeId is null for '${schemeKey}'")
        }
        schemeKey = "${typeId ?: "unknown"}-${schemeKey}"
      }

      // in case if broken configuration, do not fail, just generate name
      if (schemeKey == null) {
        schemeKey = nameGenerator.generateUniqueName("Unnamed")
      }
      else {
        schemeKey = "${schemeKey!!}, factoryName: ${it.getAttributeValue("factoryName", "")}"
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
      parentNode.getChild("list")?.let { listElement ->
        listManager.setCustomOrder(listElement.getChildren("item").mapNotNull { it.getAttributeValue("itemvalue") })
      }
      listManager.immutableSortedSettingsList = null
    }

    runConfigurationFirstLoaded()
    fireBeforeRunTasksUpdated()

    if (!isFirstLoadState && oldSelectedConfigurationId != null && oldSelectedConfigurationId != selectedConfigurationId) {
      eventPublisher.runConfigurationSelected()
    }

    eventPublisher.stateLoaded()
  }

  private fun loadSharedRunConfigurations() {
    if (schemeManagerIprProvider == null) {
      projectSchemeManager.loadSchemes()
      return
    }
    else {
      project.service<IprRunManagerImpl>().lastLoadedState.getAndSet(null)?.let { data ->
        schemeManagerIprProvider.load(data)
        projectSchemeManager.reload()
      }
    }
  }

  private fun runConfigurationFirstLoaded() {
    requestSort()
    if (selectedConfiguration == null) {
      selectedConfiguration = allSettings.firstOrNull { it.type !is UnknownRunConfiguration }
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

    eventPublisher.runConfigurationSelected()
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
    idToType.clear()
    initializeConfigurationTypes(emptyArray())
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
        val configurations = SmartList<RunnerAndConfigurationSettings>()
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

    iconCache.clear()
    val eventPublisher = eventPublisher
    removedConfigurations.forEach { eventPublisher.runConfigurationRemoved(it) }
  }

  fun loadConfiguration(element: Element, isShared: Boolean): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this)
    LOG.runAndLogException {
      settings.readExternal(element, isShared)
    }
    addConfiguration(element, settings)
    return settings
  }

  internal fun addConfiguration(element: Element, settings: RunnerAndConfigurationSettingsImpl, isCheckRecentsLimit: Boolean = true) {
    if (settings.isTemplate) {
      val factory = settings.factory
      lock.write {
        templateIdToConfiguration.put("${factory.type.id}.${factory.id}", settings)
      }
    }
    else {
      doAddConfiguration(settings, isCheckRecentsLimit)
      if (element.getAttributeBooleanValue(SELECTED_ATTR)) {
        // to support old style
        selectedConfiguration = settings
      }
    }
  }

  internal fun readBeforeRunTasks(element: Element?, settings: RunnerAndConfigurationSettings, configuration: RunConfiguration) {
    var result: MutableList<BeforeRunTask<*>>? = null
    if (element != null) {
      for (methodElement in element.getChildren(OPTION)) {
        val key = methodElement.getAttributeValue(NAME_ATTR)
        val provider = stringIdToBeforeRunProvider.getOrPut(key) { UnknownBeforeRunTaskProvider(key) }
        val beforeRunTask = provider.createTask(configuration) ?: continue
        if (beforeRunTask is PersistentStateComponent<*>) {
          // for PersistentStateComponent we don't write default value for enabled, so, set it to true explicitly
          beforeRunTask.isEnabled = true
          beforeRunTask.deserializeAndLoadState(methodElement)
        }
        else {
          @Suppress("DEPRECATION")
          beforeRunTask.readExternal(methodElement)
        }
        if (result == null) {
          result = SmartList()
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
        configuration.beforeRunTasks = getEffectiveBeforeRunTaskList(result ?: emptyList(), getConfigurationTemplate(configuration.factory!!).configuration.beforeRunTasks, true, false)
        return
      }
    }

    configuration.beforeRunTasks = result ?: emptyList()
  }

  override fun getConfigurationType(typeName: String) = idToType.get(typeName)

  @JvmOverloads
  fun getFactory(typeId: String?, factoryId: String?, checkUnknown: Boolean = false): ConfigurationFactory? {
    val type = idToType.get(typeId)
    if (type == null) {
      if (checkUnknown && typeId != null) {
        UnknownFeaturesCollector.getInstance(project).registerUnknownRunConfiguration(typeId, factoryId)
      }
      return UnknownConfigurationType.getFactory()
    }

    if (type is UnknownConfigurationType) {
      return type.configurationFactories.firstOrNull()
    }

    return type.configurationFactories.firstOrNull {
      factoryId == null || it.id == factoryId
    }
  }

  override fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?) {
    if (tempConfiguration == null) {
      return
    }

    tempConfiguration.isTemporary = true
    addConfiguration(tempConfiguration)
    if (Registry.`is`("select.run.configuration.from.context")) {
      selectedConfiguration = tempConfiguration
    }
  }

  override val tempConfigurationsList: List<RunnerAndConfigurationSettings>
    get() = allSettings.filterSmart { it.isTemporary }

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
    val tasks = SmartList<T>()
    val checkedTemplates = SmartList<RunnerAndConfigurationSettings>()
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
      iconCache.checkValidity(uniqueId)
    }
    var icon = iconCache.get(uniqueId, settings, project)
    if (withLiveIndicator) {
      val runningDescriptors = ExecutionManagerImpl.getInstance(project).getRunningDescriptors { it === settings }
      when {
        runningDescriptors.size == 1 -> icon = ExecutionUtil.getLiveIndicator(icon)
        runningDescriptors.size > 1 -> icon = IconUtil.addText(icon, runningDescriptors.size.toString())
      }
    }
    return icon
  }

  fun getConfigurationById(id: String) = lock.read { idToSettings.get(id) }

  override fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings? {
    if (name == null) {
      return null
    }
    return allSettings.firstOrNull { it.name == name }
  }

  override fun findSettings(configuration: RunConfiguration): RunnerAndConfigurationSettings? {
    return allSettings.firstOrNull { it.configuration === configuration } ?: findConfigurationByName(configuration.name)
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(settings: RunConfiguration, taskProviderId: Key<T>): List<T> {
    if (settings is WrappingRunConfiguration<*>) {
      return getBeforeRunTasks(settings.peer, taskProviderId)
    }

    var result: MutableList<T>? = null
    for (task in getBeforeRunTasks(settings)) {
      if (task.providerId === taskProviderId) {
        if (result == null) {
          result = SmartList<T>()
        }
        @Suppress("UNCHECKED_CAST")
        result.add(task as T)
      }
    }
    return result ?: emptyList()
  }

  override fun getBeforeRunTasks(configuration: RunConfiguration): List<BeforeRunTask<*>> {
    return when (configuration) {
      is WrappingRunConfiguration<*> -> getBeforeRunTasks(configuration.peer)
      else -> configuration.beforeRunTasks
    }
  }

  fun shareConfiguration(settings: RunnerAndConfigurationSettings, value: Boolean) {
    if (settings.isShared == value) {
      return
    }

    if (value && settings.isTemporary) {
      doMakeStable(settings)
    }
    settings.isShared = value
    fireRunConfigurationChanged(settings)
  }

  override fun setBeforeRunTasks(configuration: RunConfiguration, tasks: List<BeforeRunTask<*>>, addEnabledTemplateTasksIfAbsent: Boolean) {
    setBeforeRunTasks(configuration, tasks)
  }

  override fun setBeforeRunTasks(configuration: RunConfiguration, tasks: List<BeforeRunTask<*>>) {
    if (configuration is UnknownRunConfiguration) {
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

  @Suppress("OverridingDeprecatedMember")
  override fun addRunManagerListener(listener: RunManagerListener) {
    project.messageBus.connect().subscribe(RunManagerListener.TOPIC, listener)
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
    if (toRemove.isEmpty()) {
      return
    }

    val changedSettings = SmartList<RunnerAndConfigurationSettings>()
    val removed = SmartList<RunnerAndConfigurationSettings>()
    var selectedConfigurationWasRemoved = false
    lock.write {
      listManager.immutableSortedSettingsList = null

      val iterator = idToSettings.values.iterator()
      for (settings in iterator) {
        if (toRemove.contains(settings)) {
          if (selectedConfigurationId == settings.uniqueID) {
            selectedConfigurationWasRemoved = true
          }

          iterator.remove()
          settings.schemeManager?.removeScheme(settings as RunnerAndConfigurationSettingsImpl)
          recentlyUsedTemporaries.remove(settings)
          removed.add(settings)
          iconCache.remove(settings.uniqueID)
        }
        else {
          var isChanged = false
          val otherConfiguration = settings.configuration
          val newList = otherConfiguration.beforeRunTasks.nullize()?.toMutableSmartList() ?: continue
          val beforeRunTaskIterator = newList.iterator()
          for (task in beforeRunTaskIterator) {
            if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask && toRemove.firstOrNull {
              task.isMySettings(it)
            } != null) {
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
    }

    if (selectedConfigurationWasRemoved) {
      selectedConfiguration = null
    }

    removed.forEach { eventPublisher.runConfigurationRemoved(it) }
    changedSettings.forEach { eventPublisher.runConfigurationChanged(it, null) }
  }

  @TestOnly
  fun getTemplateIdToConfiguration(): Map<String, RunnerAndConfigurationSettingsImpl> {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      throw IllegalStateException("test only")
    }
    return templateIdToConfiguration
  }
}

@State(name = "ProjectRunConfigurationManager")
internal class IprRunManagerImpl(private val project: Project) : PersistentStateComponent<Element> {
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

private inline fun Collection<RunnerAndConfigurationSettings>.forEachManaged(handler: (settings: RunnerAndConfigurationSettings) -> Unit) {
  for (settings in this) {
    if (settings.type.isManaged) {
      handler(settings)
    }
  }
}