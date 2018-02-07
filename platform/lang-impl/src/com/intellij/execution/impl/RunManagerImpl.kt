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
@State(name = "RunManager", defaultStateAsResource = true, storages = arrayOf(Storage(value = StoragePathMacros.WORKSPACE_FILE, useSaveThreshold = ThreeState.NO)))
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

  private val workspaceSchemeManagerProvider = SchemeManagerIprProvider("configuration")

  internal val schemeManagerIprProvider = if (project.isDirectoryBased) null else SchemeManagerIprProvider("configuration")

  @Suppress("LeakingThis")
  private val workspaceSchemeManager = SchemeManagerFactory.getInstance(project).create("workspace",
                                                                                        RunConfigurationSchemeManager(this, false,
                                                                                                                      isWrapSchemeIntoComponentElement = false),
                                                                                        streamProvider = workspaceSchemeManagerProvider,
                                                                                        autoSave = false)

  @Suppress("LeakingThis")
  private var projectSchemeManager = SchemeManagerFactory.getInstance(project).create("runConfigurations",
                                                                                      RunConfigurationSchemeManager(this, true,
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
    for (type in types) {
      idToType.put(type.id, type)
    }
  }

  override fun createConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    val template = getConfigurationTemplate(factory)
    return createConfiguration(factory.createConfiguration(name, template.configuration), template)
  }

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory) = createConfiguration(
    runConfiguration, getConfigurationTemplate(factory))

  private fun createConfiguration(configuration: RunConfiguration,
                                  template: RunnerAndConfigurationSettingsImpl): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this, configuration, false)
    settings.importRunnerAndConfigurationSettings(template)
    if (!settings.isShared) {
      shareConfiguration(settings, template.isShared)
    }
    return settings
  }

  override fun dispose() {
    lock.write { templateIdToConfiguration.clear() }
  }

  override fun getConfig() = _config

  override val configurationFactories by lazy { idToType.values.toTypedArray() }

  override val configurationFactoriesWithoutUnknown: List<ConfigurationType>
    get() = idToType.values.filterSmart { it !is UnknownConfigurationType }

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
        (template.configuration as? UnknownRunConfiguration)?.let {
          it.isDoNotStore = true
        }

        workspaceSchemeManager.addScheme(template)

        template
      }
    }
  }

  internal fun createTemplateSettings(factory: ConfigurationFactory): RunnerAndConfigurationSettingsImpl {
    return RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(project, this), isTemplate = true,
                                              singleton = factory.isConfigurationSingletonByDefault)
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, isShared: Boolean) {
    settings.isShared = isShared
    addConfiguration(settings)
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings) {
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
      if (settings.isTemporary) {
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
        for (settings in list) {
          if (settings.type !is UnknownConfigurationType) {
            listManager.checkIfDependenciesAreStable(settings.configuration, list)
          }
        }
      }
    }

    val element = Element("state")

    workspaceSchemeManager.save()

    lock.read {
      // backward compatibility - write templates in the end
      workspaceSchemeManagerProvider.writeState(element, Comparator { n1, n2 ->
        val w1 = if (n1.startsWith("<template> of ")) 1 else 0
        val w2 = if (n2.startsWith("<template> of ")) 1 else 0
        if (w1 != w2) {
          w1 - w2
        }
        else {
          n1.compareTo(n2)
        }
      })

      if (idToSettings.size > 1) {
        selectedConfiguration?.let {
          element.setAttribute(SELECTED_ATTR, it.uniqueID)
        }

        val listElement = Element("list")
        for (settings in idToSettings.values) {
          if (settings.type is UnknownConfigurationType) {
            continue
          }

          listElement.addContent(Element("item").setAttribute("itemvalue", settings.uniqueID))
        }

        if (!listElement.isEmpty()) {
          element.addContent(listElement)
        }
      }

      val recentList = SmartList<String>()
      for (settings in recentlyUsedTemporaries) {
        if (settings.type is UnknownConfigurationType) {
          continue
        }
        recentList.add(settings.uniqueID)
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

  internal fun writeBeforeRunTasks(settings: RunnerAndConfigurationSettings, configuration: RunConfiguration): Element? {
    var tasks = if (settings.isTemplate) configuration.beforeRunTasks
    else getEffectiveBeforeRunTasks(configuration, ownIsOnlyEnabled = false, isDisableTemplateTasks = false)

    if (!tasks.isEmpty() && !settings.isTemplate) {
      val templateTasks = getTemplateBeforeRunTasks(getConfigurationTemplate(configuration.factory).configuration)
      if (!templateTasks.isEmpty()) {
        var index = 0
        for (templateTask in templateTasks) {
          if (!templateTask.isEnabled) {
            continue
          }

          if (templateTask == tasks.get(index)) {
            index++
          }
          else {
            break
          }
        }

        if (index > 0) {
          tasks = tasks.subList(index, tasks.size)
        }
      }
    }

    if (tasks.isEmpty() && settings.isNewSerializationAllowed) {
      return null
    }

    val methodElement = Element(METHOD)
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
// used by MPS. Do not use if not approved.
  fun reloadSchemes() {
    workspaceSchemeManager.reload()
    projectSchemeManager.reload()
  }

  override fun noStateLoaded() {
    isFirstLoadState.set(false)
    loadSharedRunConfigurations()
    runConfigurationFirstLoaded()
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

  internal fun addConfiguration(element: Element, settings: RunnerAndConfigurationSettingsImpl) {
    if (settings.isTemplate) {
      val factory = settings.factory
      lock.write {
        templateIdToConfiguration.put("${factory.type.id}.${factory.id}", settings)
      }
    }
    else {
      addConfiguration(settings)
      if (element.getAttributeBooleanValue(SELECTED_ATTR)) {
        // to support old style
        selectedConfiguration = settings
      }
    }
  }

  internal fun readStepsBeforeRun(child: Element, settings: RunnerAndConfigurationSettings): List<BeforeRunTask<*>> {
    var result: MutableList<BeforeRunTask<*>>? = null
    for (methodElement in child.getChildren(OPTION)) {
      val key = methodElement.getAttributeValue(NAME_ATTR)
      val provider = stringIdToBeforeRunProvider.getOrPut(key) { UnknownBeforeRunTaskProvider(key) }
      val beforeRunTask = provider.createTask(settings.configuration) ?: continue
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
    return result ?: emptyList()
  }

  override fun getConfigurationType(typeName: String) = idToType.get(typeName)

  @JvmOverloads
  fun getFactory(typeId: String?, _factoryId: String?, checkUnknown: Boolean = false): ConfigurationFactory? {
    var type = idToType.get(typeId)
    if (type == null) {
      if (checkUnknown && typeId != null) {
        UnknownFeaturesCollector.getInstance(project).registerUnknownRunConfiguration(typeId, _factoryId)
      }
      type = idToType.get(UnknownConfigurationType.NAME) ?: return null
    }

    if (type is UnknownConfigurationType || _factoryId == null) {
      return type.configurationFactories.get(0)
    }

    return type.configurationFactories.firstOrNull {
      it.id == _factoryId
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
            val template = getConfigurationTemplate(configuration.factory)
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

  override fun getBeforeRunTasks(configuration: RunConfiguration) = getEffectiveBeforeRunTasks(configuration)

  private fun getEffectiveBeforeRunTasks(configuration: RunConfiguration,
                                         ownIsOnlyEnabled: Boolean = true,
                                         isDisableTemplateTasks: Boolean = false,
                                         newTemplateTasks: List<BeforeRunTask<*>>? = null,
                                         newOwnTasks: List<BeforeRunTask<*>>? = null): List<BeforeRunTask<*>> {
    if (configuration is WrappingRunConfiguration<*>) {
      return getBeforeRunTasks(configuration.peer)
    }

    val ownTasks: List<BeforeRunTask<*>> = newOwnTasks ?: configuration.beforeRunTasks

    val templateConfiguration = getConfigurationTemplate(configuration.factory).configuration
    if (templateConfiguration is UnknownRunConfiguration) {
      return emptyList()
    }

    val templateTasks = newTemplateTasks ?: if (templateConfiguration === configuration) {
      getHardcodedBeforeRunTasks(configuration)
    }
    else {
      getTemplateBeforeRunTasks(templateConfiguration)
    }

    // if no own tasks, no need to write
    if (newTemplateTasks == null && ownTasks.isEmpty()) {
      return if (isDisableTemplateTasks) emptyList() else templateTasks.filterSmart { !ownIsOnlyEnabled || it.isEnabled }
    }
    return getEffectiveBeforeRunTaskList(ownTasks, templateTasks, ownIsOnlyEnabled, isDisableTemplateTasks = isDisableTemplateTasks)
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
    if (configuration is UnknownRunConfiguration) {
      return
    }

    val result: List<BeforeRunTask<*>>
    if (addEnabledTemplateTasksIfAbsent) {
      // copy to be sure that list is immutable
      result = tasks.mapSmart { it }
    }
    else {
      val templateConfiguration = getConfigurationTemplate(configuration.factory).configuration
      val templateTasks = if (templateConfiguration === configuration) {
        getHardcodedBeforeRunTasks(configuration)
      }
      else {
        getTemplateBeforeRunTasks(templateConfiguration)
      }

      if (templateConfiguration === configuration) {
        // we must update all existing configuration tasks to ensure that effective tasks (own + template) are the same as before template configuration change
        // see testTemplates test
        lock.read {
          for (otherSettings in allSettings) {
            val otherConfiguration = otherSettings.configuration
            if (otherConfiguration !is WrappingRunConfiguration<*> && otherConfiguration.factory === templateConfiguration.factory) {
              otherConfiguration.beforeRunTasks = getEffectiveBeforeRunTasks(otherConfiguration, ownIsOnlyEnabled = false,
                                                                             isDisableTemplateTasks = true, newTemplateTasks = tasks)
            }
          }
        }
      }

      result = if (tasks == templateTasks) {
        emptyList()
      }
      else {
        getEffectiveBeforeRunTaskList(tasks, templateTasks = templateTasks, ownIsOnlyEnabled = false, isDisableTemplateTasks = true)
      }
    }

    configuration.beforeRunTasks = result
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