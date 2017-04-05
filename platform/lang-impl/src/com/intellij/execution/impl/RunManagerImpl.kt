/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.impl

import com.intellij.ProjectTopics
import com.intellij.configurationStore.*
import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.catchAndLog
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.EventDispatcher
import com.intellij.util.IconUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.*
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Function
import javax.swing.Icon
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates

private val LOG = logger<RunManagerImpl>()
private val SELECTED_ATTR = "selected"
private val METHOD = "method"
private val OPTION = "option"

@State(name = "RunManager", defaultStateAsResource = true, storages = arrayOf(Storage(StoragePathMacros.WORKSPACE_FILE)))
class RunManagerImpl(internal val project: Project) : RunManagerEx(), PersistentStateComponent<Element>, NamedComponent, Disposable {
  companion object {
    @JvmField
    val CONFIGURATION = "configuration"
    private val RECENT = "recent_temporary"
    @JvmField
    val NAME_ATTR = "name"

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
        return Registry.`is`("dumb.aware.run.configurations")
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

  private val templateIdToConfiguration = THashMap<String, RunnerAndConfigurationSettingsImpl>()
  private val idToSettings = LinkedHashMap<String, RunnerAndConfigurationSettings>() // template configurations are not included here
  private val sharedConfigurations: MutableMap<String, Boolean> = ConcurrentHashMap()

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  private var myLoadedSelectedConfigurationUniqueName: String? = null
  private var mySelectedConfigurationId: String? = null

  private val iconCache = TimedIconCache()
  private var types: Array<ConfigurationType> by Delegates.notNull()
  private val _config by lazy { RunManagerConfig(PropertiesComponent.getInstance(project)) }

  @Suppress("DEPRECATION")
  private val myOrder = JDOMExternalizableStringList()
  private val myRecentlyUsedTemporaries = ArrayList<RunConfiguration>()
  private var myOrdered = true

  private val myDispatcher = EventDispatcher.create(RunManagerListener::class.java)!!

  private val schemeManagerProvider = SchemeManagerIprProvider("configuration")

  private val schemeManager = SchemeManagerFactory.getInstance(project).create("workspace",
                                                                               object : LazySchemeProcessor<RunConfigurationScheme, RunConfigurationScheme>() {
      override fun createScheme(dataHolder: SchemeDataHolder<RunConfigurationScheme>, name: String, attributeProvider: Function<String, String?>, isBundled: Boolean): RunConfigurationScheme {
        val settings = RunnerAndConfigurationSettingsImpl(this@RunManagerImpl)
        val element = dataHolder.read()
        try {
          settings.readExternal(element)
        }
        catch (e: InvalidDataException) {
          LOG.error(e)
        }

        val factory = settings.factory ?: return UnknownRunConfigurationScheme(name)
        doLoadConfiguration(element, false, settings, factory)
        return settings
      }

      override fun getName(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String {
        var name = attributeProvider.apply("name")
        if (name == "<template>" || name == null) {
          attributeProvider.apply("type")?.let {
            if (name == null) {
              name = "<template>"
            }
            name += " of type ${it}"
          }
        }
        return name ?: throw IllegalStateException("name is missed in the scheme data")
      }
    }, streamProvider = schemeManagerProvider, autoSave = false)

  init {
    initializeConfigurationTypes(ConfigurationType.CONFIGURATION_TYPE_EP.extensions)
    project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        val configuration = selectedConfiguration
        if (configuration != null) {
          iconCache.remove(configuration.uniqueID)
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

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory) = createConfiguration(runConfiguration, getConfigurationTemplate(factory))

  private fun createConfiguration(runConfiguration: RunConfiguration, template: RunnerAndConfigurationSettingsImpl): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this, runConfiguration, false)
    settings.importRunnerAndConfigurationSettings(template)
    if (!sharedConfigurations.containsKey(settings.uniqueID)) {
      shareConfiguration(settings, isConfigurationShared(template))
    }
    return settings
  }

  override fun dispose() {
    lock.write { templateIdToConfiguration.clear() }
  }

  override fun getConfig() = _config

  override fun getConfigurationFactories() = idToType.values.toTypedArray()

  fun getConfigurationFactories(includeUnknown: Boolean): Array<ConfigurationType> {
    if (!includeUnknown) {
      return idToType.values.filter { it !is UnknownConfigurationType }.toTypedArray()
    }
    return idToType.values.toTypedArray()
  }

  /**
   * Template configuration is not included
   */
  override fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration> {
    var result: MutableList<RunConfiguration>? = null
    for (settings in sortedConfigurations) {
      val configuration = settings.configuration
      if (type.id == configuration.type.id) {
        if (result == null) {
          result = SmartList<RunConfiguration>()
        }
        result.add(configuration)
      }
    }
    return ContainerUtil.notNullize(result)
  }

  override fun getAllConfigurationsList(): List<RunConfiguration> {
    val sortedConfigurations = sortedConfigurations
    return if (sortedConfigurations.isEmpty()) emptyList() else sortedConfigurations.mapSmart { it.configuration }
  }

  @Suppress("OverridingDeprecatedMember")
  override fun getAllConfigurations() = allConfigurationsList.toTypedArray()

  override fun getAllSettings() = sortedConfigurations.toList()

  fun getSettings(configuration: RunConfiguration) = sortedConfigurations.firstOrNull { it.configuration === configuration } as? RunnerAndConfigurationSettingsImpl

  /**
   * Template configuration is not included
   */
  override fun getConfigurationSettingsList(type: ConfigurationType): List<RunnerAndConfigurationSettings> {
    val result = SmartList<RunnerAndConfigurationSettings>()
    for (configuration in sortedConfigurations) {
      val configurationType = configuration.type
      if (configurationType != null && type.id == configurationType.id) {
        result.add(configuration)
      }
    }
    return result
  }

  @Suppress("OverridingDeprecatedMember")
  override fun getConfigurationSettings(type: ConfigurationType) = getConfigurationSettingsList(type).toTypedArray()

  @Suppress("OverridingDeprecatedMember")
  override fun getConfigurations(type: ConfigurationType) = getConfigurationSettingsList(type).map { it.configuration }.toTypedArray()

  fun getConfigurationSettings() = idToSettings.values.toTypedArray()

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
    val key = "${factory.type.id}.${factory.name}"
    return lock.read { templateIdToConfiguration.get(key) } ?: lock.write {
      templateIdToConfiguration.getOrPut(key) {
        val template = RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(project, this), true)
        template.isSingleton = factory.isConfigurationSingletonByDefault
        (template.configuration as? UnknownRunConfiguration)?.let {
          it.isDoNotStore = true
        }

        schemeManager.addScheme(template)

        template
      }
    }
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, shared: Boolean, tasks: List<BeforeRunTask<*>>?, addEnabledTemplateTasksIfAbsent: Boolean) {
    val existingId = findExistingConfigurationId(settings)
    val newId = settings.uniqueID
    var existingSettings: RunnerAndConfigurationSettings? = null

    if (existingId != null) {
      existingSettings = idToSettings.remove(existingId)
      sharedConfigurations.remove(existingId)
    }

    if (mySelectedConfigurationId != null && mySelectedConfigurationId == existingId) {
      setSelectedConfigurationId(newId)
    }
    idToSettings.put(newId, settings)

    val configuration = settings.configuration
    if (existingId == null) {
      refreshUsagesList(configuration)
    }
    checkRecentsLimit()

    sharedConfigurations.put(newId, shared)
    if (shared) {
      settings.isTemporary = false
    }

    if (tasks != null) {
      setBeforeRunTasks(configuration, tasks, addEnabledTemplateTasksIfAbsent)
    }

    if (existingSettings === settings) {
      myDispatcher.multicaster.runConfigurationChanged(settings, existingId)
    }
    else {
      runConfigurationAdded(settings, shared)
    }
  }

  private fun runConfigurationAdded(settings: RunnerAndConfigurationSettings, shared: Boolean) {
    if (!shared) {
      schemeManager.addScheme(settings as RunConfigurationScheme)
    }

    myDispatcher.multicaster.runConfigurationAdded(settings)
  }

  override fun refreshUsagesList(profile: RunProfile) {
    if (profile !is RunConfiguration) return
    val settings = getSettings(profile)
    if (settings != null && settings.isTemporary) {
      myRecentlyUsedTemporaries.remove(profile)
      myRecentlyUsedTemporaries.add(0, profile)
      trimUsagesListToLimit()
    }
  }

  private fun trimUsagesListToLimit() {
    while (myRecentlyUsedTemporaries.size > config.recentsLimit) {
      myRecentlyUsedTemporaries.removeAt(myRecentlyUsedTemporaries.size - 1)
    }
  }

  fun checkRecentsLimit() {
    trimUsagesListToLimit()
    val removed = SmartList<RunnerAndConfigurationSettings>()
    while (tempConfigurationsList.size > config.recentsLimit) {
      val it = idToSettings.values.iterator()
      while (it.hasNext()) {
        val configuration = it.next()
        if (configuration.isTemporary && !myRecentlyUsedTemporaries.contains(configuration.configuration)) {
          removed.add(configuration)
          it.remove()
          break
        }
      }
    }
    fireRunConfigurationsRemoved(removed)
  }

  fun setOrdered(ordered: Boolean) {
    myOrdered = ordered
  }

  fun saveOrder() {
    setOrder(null)
  }

  private fun doSaveOrder(comparator: Comparator<RunnerAndConfigurationSettings>?) {
    val sorted = idToSettings.values.filter { it.type !is UnknownConfigurationType }
    if (comparator != null) {
      sorted.sortedWith(comparator)
    }

    myOrder.clear()
    sorted.mapTo(myOrder) { it.uniqueID}
  }

  fun setOrder(comparator: Comparator<RunnerAndConfigurationSettings>?) {
    doSaveOrder(comparator)
    // force recache of configurations list
    setOrdered(false)
  }

  override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
    if (mySelectedConfigurationId == null && myLoadedSelectedConfigurationUniqueName != null) {
      setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName)
    }
    return mySelectedConfigurationId?.let { idToSettings.get(it) }
  }

  override fun setSelectedConfiguration(settings: RunnerAndConfigurationSettings?) {
    setSelectedConfigurationId(settings?.uniqueID)
    fireRunConfigurationSelected()
  }

  private fun setSelectedConfigurationId(id: String?) {
    mySelectedConfigurationId = id
    if (mySelectedConfigurationId != null) {
      myLoadedSelectedConfigurationUniqueName = null
    }
  }

  override fun getSortedConfigurations(): MutableCollection<RunnerAndConfigurationSettings> {
    if (myOrdered) {
      return idToSettings.values
    }

    val order = ArrayList<Pair<String, RunnerAndConfigurationSettings>>(idToSettings.size)
    val folderNames = SmartList<String>()
    for (each in idToSettings.values) {
      order.add(Pair.create(each.uniqueID, each))
      val folderName = each.folderName
      if (folderName != null && !folderNames.contains(folderName)) {
        folderNames.add(folderName)
      }
    }
    folderNames.add(null)
    idToSettings.clear()

    if (myOrder.isEmpty()) {
      // IDEA-63663 Sort run configurations alphabetically if clean checkout
      order.sort { o1, o2 ->
        val temporary1 = o1.getSecond().isTemporary
        val temporary2 = o2.getSecond().isTemporary
        when {
          temporary1 == temporary2 -> o1.first.compareTo(o2.first)
          temporary1 -> 1
          else -> -1
        }
      }
    }
    else {
      order.sort { o1, o2 ->
        val i1 = folderNames.indexOf(o1.getSecond().folderName)
        val i2 = folderNames.indexOf(o2.getSecond().folderName)
        if (i1 != i2) {
          return@sort i1 -i2
        }

        val temporary1 = o1.getSecond().isTemporary
        val temporary2 = o2.getSecond().isTemporary
        when {
          temporary1 == temporary2 -> {
            val index1 = myOrder.indexOf(o1.first)
            val index2 = myOrder.indexOf(o2.first)
            if (index1 == -1 && index2 == -1) {
              o1.second.name.compareTo(o2.second.name)
            }
            else {
              index1 - index2
            }
          }
          temporary1 -> 1
          else -> -1
        }
      }
    }

    for (each in order) {
      val setting = each.second
      idToSettings.put(setting.uniqueID, setting)
    }

    myOrdered = true
    return idToSettings.values
  }

  @Suppress("DEPRECATION")
  override fun getState(): Element {
    val element = Element("state")

    schemeManager.save()
    // backward compatibility - write templates in the end
    schemeManagerProvider.writeState(element, Comparator { n1, n2 ->
      val w1 = if (n1.startsWith("<template> of ")) 1 else 0
      val w2 = if (n2.startsWith("<template> of ")) 1 else 0
      if (w1 != w2) {
        w1 - w2
      }
      else {
        n1.compareTo(n2)
      }
    })

    selectedConfiguration?.let {
      element.setAttribute(SELECTED_ATTR, it.uniqueID)
    }

    if (idToSettings.size > 1) {
      var order: JDOMExternalizableStringList? = null
      for (each in idToSettings.values) {
        if (each.type is UnknownConfigurationType) {
          continue
        }

        if (order == null) {
          order = JDOMExternalizableStringList()
        }
        order.add(each.uniqueID)
      }
      if (order != null) {
        order.writeExternal(element)
      }
    }

    val recentList = JDOMExternalizableStringList()
    for (each in myRecentlyUsedTemporaries) {
      if (each.type is UnknownConfigurationType) {
        continue
      }
      val settings = getSettings(each) ?: continue
      recentList.add(settings.uniqueID)
    }
    if (!recentList.isEmpty()) {
      val recent = Element(RECENT)
      element.addContent(recent)
      recentList.writeExternal(recent)
    }
    return element
  }

  fun writeContext(element: Element) {
    val values = ArrayList(idToSettings.values)
    for (configurationSettings in values) {
      if (configurationSettings.isTemporary) {
        addConfigurationElement(element, configurationSettings)
      }
    }

    selectedConfiguration?.let {
      element.setAttribute(SELECTED_ATTR, it.uniqueID)
    }
  }

  fun addConfigurationElement(parentNode: Element, settings: RunnerAndConfigurationSettings) {
    val configurationElement = Element(CONFIGURATION)
    parentNode.addContent(configurationElement)
    (settings as RunnerAndConfigurationSettingsImpl).writeExternal(configurationElement)

    settings.configuration?.let {
      writeBeforeRunTasks(it, settings.isTemplate, configurationElement)
    }
  }

  internal fun writeBeforeRunTasks(configuration: RunConfiguration, isTemplate: Boolean, configurationElement: Element) {
    if (configuration is UnknownRunConfiguration) {
      return
    }

    val tasks = if (isTemplate) configuration.beforeRunTasks else getEffectiveBeforeRunTasks(configuration, ownIsOnlyEnabled = false, isDisableTemplateTasks = true)
    if (tasks.isEmpty()) {
      return
    }

    val methodsElement = Element(METHOD)
    for (task in tasks) {
      val child = Element(OPTION)
      child.setAttribute(NAME_ATTR, task.providerId.toString())
      task.writeExternal(child)
      methodsElement.addContent(child)
    }

    configurationElement.addContent(methodsElement)
  }

  override fun loadState(parentNode: Element) {
    clear(false)

    schemeManagerProvider.load(parentNode) {
      var name = it.getAttributeValue("name")
      if (name == "<template>" || name == null) {
        // scheme name must be unique
        it.getAttributeValue("type")?.let {
          if (name == null) {
            name = "<template>"
          }
          name += " of type ${it}"
        }
      }
      name
    }
    schemeManager.reload()

    myOrder.readExternal(parentNode)

    // migration (old ids to UUIDs)
    readList(myOrder)

    myRecentlyUsedTemporaries.clear()
    val recentNode = parentNode.getChild(RECENT)
    if (recentNode != null) {
      @Suppress("DEPRECATION")
      val list = JDOMExternalizableStringList()
      list.readExternal(recentNode)
      readList(list)
      for (name in list) {
        val settings = idToSettings[name]
        if (settings != null) {
          myRecentlyUsedTemporaries.add(settings.configuration)
        }
      }
    }
    myOrdered = false

    myLoadedSelectedConfigurationUniqueName = parentNode.getAttributeValue(SELECTED_ATTR)
    setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName)

    fireBeforeRunTasksUpdated()
    fireRunConfigurationSelected()
  }

  private fun readList(@Suppress("DEPRECATION") list: JDOMExternalizableStringList) {
    for (i in list.indices) {
      for (settings in idToSettings.values) {
        val configuration = settings.configuration
        @Suppress("DEPRECATION")
        if (configuration != null && list.get(i) == "${configuration.type.displayName}.${configuration.name}${(configuration as? UnknownRunConfiguration)?.uniqueID ?: ""}") {
          list.set(i, settings.uniqueID)
          break
        }
      }
    }
  }

  fun readContext(parentNode: Element) {
    myLoadedSelectedConfigurationUniqueName = parentNode.getAttributeValue(SELECTED_ATTR)

    for (aChildren in parentNode.children) {
      val element = aChildren
      val config = loadConfiguration(element, false)
      if (myLoadedSelectedConfigurationUniqueName == null
          && config != null
          && java.lang.Boolean.parseBoolean(element.getAttributeValue(SELECTED_ATTR))) {
        myLoadedSelectedConfigurationUniqueName = config.uniqueID
      }
    }

    setSelectedConfigurationId(myLoadedSelectedConfigurationUniqueName)

    fireRunConfigurationSelected()
  }

  fun findExistingConfigurationId(settings: RunnerAndConfigurationSettings?): String? {
    if (settings != null) {
      for ((key, value) in idToSettings) {
        if (value === settings) {
          return key
        }
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
    val configurations: MutableList<RunnerAndConfigurationSettings>
    if (allConfigurations) {
      this.idToSettings.clear()
      sharedConfigurations.clear()
      mySelectedConfigurationId = null
      configurations = ArrayList(this.idToSettings.values)
    }
    else {
      configurations = SmartList<RunnerAndConfigurationSettings>()
      val iterator = this.idToSettings.values.iterator()
      while (iterator.hasNext()) {
        val configuration = iterator.next()
        if (configuration.isTemporary || !isConfigurationShared(configuration)) {
          iterator.remove()

          sharedConfigurations.remove(configuration.uniqueID)
          configurations.add(configuration)
        }
      }

      if (mySelectedConfigurationId != null && this.idToSettings.containsKey(mySelectedConfigurationId!!)) {
        mySelectedConfigurationId = null
      }
    }

    lock.write {
      templateIdToConfiguration.clear()
    }
    myLoadedSelectedConfigurationUniqueName = null
    iconCache.clear()
    myRecentlyUsedTemporaries.clear()
    fireRunConfigurationsRemoved(configurations)
  }

  fun loadConfiguration(element: Element, isShared: Boolean): RunnerAndConfigurationSettings? {
    val settings = RunnerAndConfigurationSettingsImpl(this)
    LOG.catchAndLog {
      settings.readExternal(element)
    }

    val factory = settings.factory ?: return null
    doLoadConfiguration(element, isShared, settings, factory)
    return settings
  }

  private fun doLoadConfiguration(element: Element, isShared: Boolean, settings: RunnerAndConfigurationSettingsImpl, factory: ConfigurationFactory) {
    val tasks = element.getChild(METHOD)?.let { readStepsBeforeRun(it, settings) } ?: emptyList()
    settings.configuration?.beforeRunTasks = tasks
    if (settings.isTemplate) {
      lock.write {
        templateIdToConfiguration.put("${factory.type.id}.${factory.name}", settings)
      }
    }
    else {
      addConfiguration(settings, isShared, null, false)
      if (element.getAttributeValue(SELECTED_ATTR).toBoolean()) {
        // to support old style
        selectedConfiguration = settings
      }
    }
  }

  private fun readStepsBeforeRun(child: Element, settings: RunnerAndConfigurationSettings): List<BeforeRunTask<*>> {
    var result: MutableList<BeforeRunTask<*>>? = null
    for (methodElement in child.getChildren(OPTION)) {
      val id = getProviderKey(methodElement.getAttributeValue(NAME_ATTR))
      val beforeRunTask = getProvider(id).createTask(settings.configuration)
      if (beforeRunTask != null) {
        beforeRunTask.readExternal(methodElement)
        if (result == null) {
          result = SmartList()
        }
        result.add(beforeRunTask)
      }
    }
    return result ?: emptyList()
  }

  fun getConfigurationType(typeName: String) = idToType.get(typeName)

  @JvmOverloads
  fun getFactory(typeName: String?, _factoryName: String?, checkUnknown: Boolean = false): ConfigurationFactory? {
    var type = idToType.get(typeName)
    if (type == null) {
      if (checkUnknown && typeName != null) {
        UnknownFeaturesCollector.getInstance(project).registerUnknownRunConfiguration(typeName)
      }
      type = idToType.get(UnknownConfigurationType.NAME) ?: return null
    }

    if (type is UnknownConfigurationType) {
      return type.getConfigurationFactories().get(0)
    }

    val factoryName = _factoryName ?: type.configurationFactories.get(0).name
    return type.configurationFactories.firstOrNull { it.name == factoryName }
  }

  override fun getComponentName() = "RunManager"

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

  fun getStableConfigurations(shared: Boolean): Collection<RunnerAndConfigurationSettings> {
    var result: MutableList<RunnerAndConfigurationSettings>? = null
    for (configuration in idToSettings.values) {
      if (!configuration.isTemporary && isConfigurationShared(configuration) == shared) {
        if (result == null) {
          result = SmartList<RunnerAndConfigurationSettings>()
        }
        result.add(configuration)
      }
    }
    return ContainerUtil.notNullize(result)
  }

  internal val configurationSettings: Collection<RunnerAndConfigurationSettings>
    get() = idToSettings.values

  override fun getTempConfigurationsList() = idToSettings.values.filter { it.isTemporary }

  override fun makeStable(settings: RunnerAndConfigurationSettings) {
    settings.isTemporary = false
    myRecentlyUsedTemporaries.remove(settings.configuration)
    if (!myOrder.isEmpty()) {
      setOrdered(false)
    }
    fireRunConfigurationChanged(settings)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun makeStable(configuration: RunConfiguration) {
    getSettings(configuration)?.let {
      makeStable(it)
    }
  }

  override fun isConfigurationShared(settings: RunnerAndConfigurationSettings): Boolean {
    if (settings.isTemporary) {
      return false
    }
    return sharedConfigurations.get(settings.uniqueID) ?: sharedConfigurations.get(getConfigurationTemplate(settings.factory!!).uniqueID) ?: false
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(taskProviderId: Key<T>): List<T> {
    val tasks = ArrayList<T>()
    val checkedTemplates = ArrayList<RunnerAndConfigurationSettings>()
    for (settings in idToSettings.values.toTypedArray()) {
      for (task in getBeforeRunTasks(settings.configuration)) {
        if (task.isEnabled && task.providerId === taskProviderId) {
          @Suppress("UNCHECKED_CAST")
          tasks.add(task as T)
        }
        else {
          val template = getConfigurationTemplate(settings.factory!!)
          if (!checkedTemplates.contains(template)) {
            checkedTemplates.add(template)
            for (templateTask in getBeforeRunTasks(template.configuration!!)) {
              if (templateTask.isEnabled && templateTask.providerId === taskProviderId) {
                @Suppress("UNCHECKED_CAST")
                tasks.add(templateTask as T)
              }
            }
          }
        }
      }
    }
    return tasks
  }

  override fun getConfigurationIcon(settings: RunnerAndConfigurationSettings, withLiveIndicator: Boolean): Icon {
    val uniqueID = settings.uniqueID
    val selectedConfiguration = selectedConfiguration
    val selectedId = if (selectedConfiguration != null) selectedConfiguration.uniqueID else ""
    if (selectedId == uniqueID) {
      iconCache.checkValidity(uniqueID)
    }
    var icon = iconCache.get(uniqueID, settings, project)
    if (withLiveIndicator) {
      val runningDescriptors = ExecutionManagerImpl.getInstance(project).getRunningDescriptors { it === settings }
      if (runningDescriptors.size == 1) {
        icon = ExecutionUtil.getLiveIndicator(icon)
      }
      if (runningDescriptors.size > 1) {
        icon = IconUtil.addText(icon, runningDescriptors.size.toString())
      }
    }
    return icon
  }

  fun getConfigurationById(id: String) = idToSettings.get(id)

  override fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings? {
    if (name == null) {
      return null
    }
    return idToSettings.values.firstOrNull { it.name == name }
  }

  fun findConfigurationByTypeAndName(typeId: String, name: String): RunnerAndConfigurationSettings? {
    return sortedConfigurations.firstOrNull {
      val t = it.type
      t != null && typeId == t.id && name == it.name
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

    val template = getConfigurationTemplate(configuration.factory)
    val templateConfiguration = template.configuration
    if (templateConfiguration == null || templateConfiguration is UnknownRunConfiguration) {
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

  private fun getEffectiveBeforeRunTaskList(ownTasks: List<BeforeRunTask<*>>,
                                            templateTasks: List<BeforeRunTask<*>>,
                                            ownIsOnlyEnabled: Boolean,
                                            isDisableTemplateTasks: Boolean): MutableList<BeforeRunTask<*>> {
    val idToSet = ownTasks.mapSmartSet { it.providerId }
    val result = ownTasks.filterSmartMutable { !ownIsOnlyEnabled || it.isEnabled }
    var i = 0
    for (templateTask in templateTasks) {
      if (templateTask.isEnabled && !idToSet.contains(templateTask.providerId)) {
        val effectiveTemplateTask = if (isDisableTemplateTasks) {
          val clone = templateTask.clone()
          clone.isEnabled = false
          clone
        }
        else {
          templateTask
        }
        result.add(i, effectiveTemplateTask)
        i++
      }
    }
    return result
  }

  private fun getTemplateBeforeRunTasks(templateConfiguration: RunConfiguration): List<BeforeRunTask<*>> {
    return templateConfiguration.beforeRunTasks.nullize() ?: getHardcodedBeforeRunTasks(templateConfiguration)
  }

  private fun getHardcodedBeforeRunTasks(configuration: RunConfiguration): List<BeforeRunTask<*>> {
    var result: MutableList<BeforeRunTask<*>>? = null
    for (provider in Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, project)) {
      val task = provider.createTask(configuration)
      if (task != null && task.isEnabled) {
        configuration.factory.configureBeforeRunTaskDefaults(provider.id, task)
        if (task.isEnabled) {
          if (result == null) {
            result = SmartList<BeforeRunTask<*>>()
          }
          result.add(task)
        }
      }
    }
    return result.orEmpty()
  }

  fun shareConfiguration(settings: RunnerAndConfigurationSettings?, shareConfiguration: Boolean) {
    val shouldFire = settings != null && isConfigurationShared(settings) != shareConfiguration
    if (shareConfiguration && settings!!.isTemporary) {
      makeStable(settings)
    }
    sharedConfigurations.put(settings!!.uniqueID, shareConfiguration)
    if (shouldFire) {
      fireRunConfigurationChanged(settings)
    }
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
      val templateTasks = if (templateConfiguration == null || templateConfiguration === configuration) {
        getHardcodedBeforeRunTasks(configuration)
      }
      else {
        getTemplateBeforeRunTasks(templateConfiguration)
      }

      if (templateConfiguration === configuration) {
        // we must update all existing configuration tasks to ensure that effective tasks (own + template) are the same as before template configuration change
        // see testTemplates test
        lock.read {
          for (otherSettings in idToSettings.values) {
            val otherConfiguration = otherSettings.configuration ?: continue
            if (otherConfiguration !is WrappingRunConfiguration<*> && otherConfiguration.factory === templateConfiguration.factory) {
              otherConfiguration.beforeRunTasks = getEffectiveBeforeRunTasks(otherConfiguration, isDisableTemplateTasks = true, newTemplateTasks = tasks)
            }
          }
        }
      }

      if (tasks == templateTasks) {
        result = emptyList()
      }
      else  {
        result = getEffectiveBeforeRunTaskList(tasks, templateTasks = templateTasks, ownIsOnlyEnabled = false, isDisableTemplateTasks = true)
      }
    }

    configuration.beforeRunTasks = result
    fireBeforeRunTasksUpdated()
  }

  fun removeNotExistingSharedConfigurations(existing: Set<String>) {
    var removed: MutableList<RunnerAndConfigurationSettings>? = null
    val it = idToSettings.entries.iterator()
    while (it.hasNext()) {
      val entry = it.next()
      val settings = entry.value
      if (!settings.isTemplate && isConfigurationShared(settings) && !existing.contains(settings.uniqueID)) {
        if (removed == null) {
          removed = SmartList<RunnerAndConfigurationSettings>()
        }
        removed.add(settings)
        it.remove()
      }
    }
    fireRunConfigurationsRemoved(removed)
  }

  fun fireBeginUpdate() {
    myDispatcher.multicaster.beginUpdate()
  }

  fun fireEndUpdate() {
    myDispatcher.multicaster.endUpdate()
  }

  fun fireRunConfigurationChanged(settings: RunnerAndConfigurationSettings) {
    myDispatcher.multicaster.runConfigurationChanged(settings, null)
  }

  private fun fireRunConfigurationsRemoved(removed: List<RunnerAndConfigurationSettings>?) {
    if (removed != null && !removed.isEmpty()) {
      myRecentlyUsedTemporaries.removeAll(removed.map { it.configuration })
      for (settings in removed) {
        myDispatcher.multicaster.runConfigurationRemoved(settings)
      }
    }
  }

  private fun fireRunConfigurationSelected() {
    myDispatcher.multicaster.runConfigurationSelected()
  }

  override fun addRunManagerListener(listener: RunManagerListener) {
    myDispatcher.addListener(listener)
  }

  override fun removeRunManagerListener(listener: RunManagerListener) {
    myDispatcher.removeListener(listener)
  }

  fun fireBeforeRunTasksUpdated() {
    myDispatcher.multicaster.beforeRunTasksChanged()
  }

  private var myBeforeStepsMap: MutableMap<Key<out BeforeRunTask<*>>, BeforeRunTaskProvider<*>>? = null
  private var myProviderKeysMap: MutableMap<String, Key<out BeforeRunTask<*>>>? = null

  @Synchronized private fun getProvider(providerId: Key<out BeforeRunTask<*>>): BeforeRunTaskProvider<*> {
    if (myBeforeStepsMap == null) {
      initProviderMaps()
    }
    return myBeforeStepsMap!!.get(providerId)!!
  }

  @Synchronized private fun getProviderKey(keyString: String): Key<out BeforeRunTask<*>> {
    if (myProviderKeysMap == null) {
      initProviderMaps()
    }
    var id: Key<out BeforeRunTask<*>>? = myProviderKeysMap!![keyString]
    if (id == null) {
      val provider = UnknownBeforeRunTaskProvider(keyString)
      id = provider.id!!
      myProviderKeysMap!!.put(keyString, id)
      myBeforeStepsMap!!.put(id, provider)
    }
    return id
  }

  private fun initProviderMaps() {
    myBeforeStepsMap = LinkedHashMap()
    myProviderKeysMap = LinkedHashMap()
    for (provider in Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, project)) {
      val id = provider.id
      myBeforeStepsMap!!.put(id, provider)
      myProviderKeysMap!!.put(id.toString(), id)
    }
  }

  override fun removeConfiguration(settings: RunnerAndConfigurationSettings?) {
    if (settings == null) {
      return
    }

    val it = sortedConfigurations.iterator()
    for (otherSettings in it) {
      if (otherSettings === settings) {
        if (mySelectedConfigurationId != null && mySelectedConfigurationId === settings.uniqueID) {
          selectedConfiguration = null
        }

        it.remove()
        sharedConfigurations.remove(settings.uniqueID)
        myRecentlyUsedTemporaries.remove(settings.configuration)
        myDispatcher.multicaster.runConfigurationRemoved(otherSettings)
      }

      var changed = false
      val otherConfiguration = otherSettings.configuration ?: continue
      val newList = otherConfiguration.beforeRunTasks.nullize()?.toMutableSmartList() ?: continue
      val beforeRunTaskIterator = newList.iterator()
      for (task in beforeRunTaskIterator) {
        if (task is RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask && task.settings === settings) {
          beforeRunTaskIterator.remove()
          changed = true
          myDispatcher.multicaster.runConfigurationChanged(otherSettings, null)
        }
      }
      if (changed) {
        otherConfiguration.beforeRunTasks = newList
      }
    }
  }
}

interface RunConfigurationScheme : Scheme

private class UnknownRunConfigurationScheme(private val name: String) : RunConfigurationScheme, SerializableScheme {
  override fun getSchemeState() = SchemeState.UNCHANGED

  override fun writeScheme() = throw AssertionError("Must be not called")

  override fun getName() = name
}