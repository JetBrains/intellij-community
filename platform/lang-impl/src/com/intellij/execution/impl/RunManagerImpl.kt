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
import com.intellij.util.containers.ContainerUtil
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import javax.swing.Icon
import kotlin.properties.Delegates

private val LOG = logger<RunManagerImpl>()

@State(name = "RunManager", defaultStateAsResource = true, storages = arrayOf(Storage(StoragePathMacros.WORKSPACE_FILE)))
abstract class RunManagerImpl(internal val project: Project, propertiesComponent: PropertiesComponent) : RunManagerEx(), PersistentStateComponent<Element>, NamedComponent, Disposable {
  companion object {
    @JvmField
    val CONFIGURATION = "configuration"
    protected val RECENT = "recent_temporary"
    @JvmField
    val NAME_ATTR = "name"
    protected val SELECTED_ATTR = "selected"
    private val METHOD = "method"
    private val OPTION = "option"

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

  private val typeByName = LinkedHashMap<String, ConfigurationType>()

  protected val templateConfigurationMap = ContainerUtil.newConcurrentMap<String, RunnerAndConfigurationSettingsImpl>()
  private val myConfigurations = LinkedHashMap<String, RunnerAndConfigurationSettings>() // template configurations are not included here
  protected val mySharedConfigurations: MutableMap<String, Boolean> = ConcurrentHashMap()

  // When readExternal not all configuration may be loaded, so we need to remember the selected configuration
  // so that when it is eventually loaded, we can mark is as a selected.
  private var myLoadedSelectedConfigurationUniqueName: String? = null
  protected var mySelectedConfigurationId: String? = null

  private val myIconCache = TimedIconCache()
  private var myTypes: Array<ConfigurationType> by Delegates.notNull()
  private val myConfig = RunManagerConfig(propertiesComponent)

  private var myUnknownElements: List<Element>? = null
  @Suppress("DEPRECATION")
  private val myOrder = JDOMExternalizableStringList()
  protected val myRecentlyUsedTemporaries = ArrayList<RunConfiguration>()
  private var myOrdered = true

  protected val myDispatcher = EventDispatcher.create(RunManagerListener::class.java)!!

  protected val schemeManagerProvider = SchemeManagerIprProvider("configuration")

  protected val schemeManager = SchemeManagerFactory.getInstance(project).create("workspace",
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
          myIconCache.remove(configuration.uniqueID)
        }
      }
    })
  }

  // separate method needed for tests
  fun initializeConfigurationTypes(factories: Array<ConfigurationType>) {
    factories.sortBy { it.displayName }

    val types = factories.toMutableList()
    types.add(UnknownConfigurationType.INSTANCE)
    myTypes = types.toTypedArray()

    for (type in factories) {
      typeByName.put(type.id, type)
    }

    val broken = UnknownConfigurationType.INSTANCE
    typeByName.put(broken.id, broken)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun createConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    val template = getConfigurationTemplate(factory)
    return createConfiguration(factory.createConfiguration(name, template.configuration), template)
  }

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory) = createConfiguration(runConfiguration, getConfigurationTemplate(factory))

  private fun createConfiguration(runConfiguration: RunConfiguration, template: RunnerAndConfigurationSettingsImpl): RunnerAndConfigurationSettings {
    val settings = RunnerAndConfigurationSettingsImpl(this, runConfiguration, false)
    settings.importRunnerAndConfigurationSettings(template)
    if (!mySharedConfigurations.containsKey(settings.uniqueID)) {
      shareConfiguration(settings, isConfigurationShared(template))
    }
    return settings
  }

  override fun dispose() {
    templateConfigurationMap.clear()
  }

  override fun getConfig() = myConfig

  override fun getConfigurationFactories() = myTypes.clone()

  fun getConfigurationFactories(includeUnknown: Boolean): Array<ConfigurationType> {
    if (!includeUnknown) {
      return myTypes.filter { it !is UnknownConfigurationType }.toTypedArray()
    }
    return myTypes.clone()
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
    if (sortedConfigurations.isEmpty()) {
      return emptyList()
    }

    val result = ArrayList<RunConfiguration>(sortedConfigurations.size)
    for (settings in sortedConfigurations) {
      result.add(settings.configuration)
    }
    return result
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

  fun getConfigurationSettings() = myConfigurations.values.toTypedArray()

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
    var template = templateConfigurationMap.get(key)
    if (template == null) {
      template = RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(project, this), true)
      template.isSingleton = factory.isConfigurationSingletonByDefault
      (template.configuration as? UnknownRunConfiguration)?.let {
        it.isDoNotStore = true
      }

      schemeManager.addScheme(template)

      templateConfigurationMap.put(key, template)
    }
    return template
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, shared: Boolean, tasks: List<BeforeRunTask<*>>, addEnabledTemplateTasksIfAbsent: Boolean) {
    val existingId = findExistingConfigurationId(settings)
    val newId = settings.uniqueID
    var existingSettings: RunnerAndConfigurationSettings? = null

    if (existingId != null) {
      existingSettings = myConfigurations.remove(existingId)
      mySharedConfigurations.remove(existingId)
    }

    if (mySelectedConfigurationId != null && mySelectedConfigurationId == existingId) {
      setSelectedConfigurationId(newId)
    }
    myConfigurations.put(newId, settings)

    val configuration = settings.configuration
    if (existingId == null) {
      refreshUsagesList(configuration)
    }
    checkRecentsLimit()

    mySharedConfigurations.put(newId, shared)
    if (shared) {
      settings.isTemporary = false
    }
    setBeforeRunTasks(configuration, tasks, addEnabledTemplateTasksIfAbsent)

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
      val it = myConfigurations.values.iterator()
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
    val sorted = myConfigurations.values.filter { it.type !is UnknownConfigurationType }
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
    return mySelectedConfigurationId?.let { myConfigurations.get(it) }
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
      return myConfigurations.values
    }

    val order = ArrayList<Pair<String, RunnerAndConfigurationSettings>>(myConfigurations.size)
    val folderNames = SmartList<String>()
    for (each in myConfigurations.values) {
      order.add(Pair.create(each.uniqueID, each))
      val folderName = each.folderName
      if (folderName != null && !folderNames.contains(folderName)) {
        folderNames.add(folderName)
      }
    }
    folderNames.add(null)
    myConfigurations.clear()

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
      myConfigurations.put(setting.uniqueID, setting)
    }

    myOrdered = true
    return myConfigurations.values
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

    if (myConfigurations.size > 1) {
      var order: JDOMExternalizableStringList? = null
      for (each in myConfigurations.values) {
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

    if (myUnknownElements != null) {
      for (unloadedElement in myUnknownElements!!) {
        element.addContent(unloadedElement.clone())
      }
    }
    return element
  }

  fun writeContext(element: Element) {
    val values = ArrayList(myConfigurations.values)
    for (configurationSettings in values) {
      if (configurationSettings.isTemporary) {
        addConfigurationElement(element, configurationSettings, CONFIGURATION)
      }
    }

    selectedConfiguration?.let {
      element.setAttribute(SELECTED_ATTR, it.uniqueID)
    }
  }

  fun addConfigurationElement(parentNode: Element, template: RunnerAndConfigurationSettings) {
    addConfigurationElement(parentNode, template, CONFIGURATION)
  }

  private fun addConfigurationElement(parentNode: Element, settings: RunnerAndConfigurationSettings, elementType: String) {
    val configurationElement = Element(elementType)
    parentNode.addContent(configurationElement)
    (settings as RunnerAndConfigurationSettingsImpl).writeExternal(configurationElement)

    if (settings.configuration !is UnknownRunConfiguration) {
      doWriteConfiguration(settings, configurationElement)
    }
  }

  internal fun doWriteConfiguration(settings: RunnerAndConfigurationSettings, configurationElement: Element) {
    val tasks = ArrayList(getBeforeRunTasks(settings.configuration))
    val templateTasks = THashMap<Key<BeforeRunTask<*>>, BeforeRunTask<*>>()
    val beforeRunTasks: List<BeforeRunTask<*>>?
    if (settings.isTemplate) {
      beforeRunTasks = getHardcodedBeforeRunTasks(settings.configuration)
    }
    else {
      beforeRunTasks = getConfigurationTemplate(settings.factory!!).configuration.beforeRunTasks ?: emptyList()
    }
    for (templateTask in beforeRunTasks) {
      @Suppress("UNCHECKED_CAST")
      templateTasks.put(templateTask.providerId as Key<BeforeRunTask<*>>?, templateTask)
      if (templateTask.isEnabled) {
        var found = false
        for (realTask in tasks) {
          if (realTask.providerId === templateTask.providerId) {
            found = true
            break
          }
        }
        if (!found) {
          val clone = templateTask.clone()
          clone.isEnabled = false
          tasks.add(0, clone)
        }
      }
    }

    // we have to always write empty method element otherwise no way to indicate that
    var methodsElement: Element? = if (settings.isTemplate) Element(METHOD) else null
    var i = 0
    val size = tasks.size
    while (i < size) {
      val task = tasks.get(i)
      var j = 0
      var templateTask: BeforeRunTask<*>? = null
      for ((key, value) in templateTasks) {
        if (key === task.providerId) {
          templateTask = value
          break
        }
        j++
      }
      if (task == templateTask && i == j) {
        // not necessary saving if the task is the same as template and on the same place
        i++
        continue
      }
      val child = Element(OPTION)
      child.setAttribute(NAME_ATTR, task.providerId.toString())
      task.writeExternal(child)

      if (methodsElement == null) {
        methodsElement = Element(METHOD)
      }
      methodsElement.addContent(child)
      i++
    }

    if (methodsElement != null) {
      configurationElement.addContent(methodsElement)
    }
  }

  override fun loadState(parentNode: Element) {
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
        val settings = myConfigurations[name]
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
      for (settings in myConfigurations.values) {
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
      for ((key, value) in myConfigurations) {
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
    typeByName.clear()
    initializeConfigurationTypes(emptyArray())
  }

  protected fun clear(allConfigurations: Boolean) {
    val configurations: MutableList<RunnerAndConfigurationSettings>
    if (allConfigurations) {
      myConfigurations.clear()
      mySharedConfigurations.clear()
      mySelectedConfigurationId = null
      configurations = ArrayList(myConfigurations.values)
    }
    else {
      configurations = SmartList<RunnerAndConfigurationSettings>()
      val iterator = myConfigurations.values.iterator()
      while (iterator.hasNext()) {
        val configuration = iterator.next()
        if (configuration.isTemporary || !isConfigurationShared(configuration)) {
          iterator.remove()

          mySharedConfigurations.remove(configuration.uniqueID)
          configurations.add(configuration)
        }
      }

      if (mySelectedConfigurationId != null && myConfigurations.containsKey(mySelectedConfigurationId!!)) {
        mySelectedConfigurationId = null
      }
    }

    myUnknownElements = null
    templateConfigurationMap.clear()
    myLoadedSelectedConfigurationUniqueName = null
    myIconCache.clear()
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
    if (settings.isTemplate) {
      templateConfigurationMap.put("${factory.type.id}.${factory.name}", settings)
      settings.configuration.beforeRunTasks = tasks
    }
    else {
      val configuration = settings.configuration
      if (configuration !is UnknownRunConfiguration) {
        val result = SmartList(tasks)
        addTemplateBeforeTasks(configuration, tasks, result)
        configuration.beforeRunTasks = result
      }

      addConfiguration(settings, isShared, tasks, false)
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

  fun getConfigurationType(typeName: String) = typeByName.get(typeName)

  @JvmOverloads
  fun getFactory(typeName: String?, _factoryName: String?, checkUnknown: Boolean = false): ConfigurationFactory? {
    var type = typeByName.get(typeName)
    if (type == null) {
      if (checkUnknown && typeName != null) {
        UnknownFeaturesCollector.getInstance(project).registerUnknownRunConfiguration(typeName)
      }
      type = typeByName.get(UnknownConfigurationType.NAME) ?: return null
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

    addConfiguration(tempConfiguration, isConfigurationShared(tempConfiguration), getBeforeRunTasks(tempConfiguration.configuration), false)
    if (Registry.`is`("select.run.configuration.from.context")) {
      selectedConfiguration = tempConfiguration
    }
  }

  fun getStableConfigurations(shared: Boolean): Collection<RunnerAndConfigurationSettings> {
    var result: MutableList<RunnerAndConfigurationSettings>? = null
    for (configuration in myConfigurations.values) {
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
    get() = myConfigurations.values

  override fun getTempConfigurationsList() = myConfigurations.values.filter { it.isTemporary }

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

  @Suppress("DEPRECATION")
  override fun createRunConfiguration(name: String, factory: ConfigurationFactory) = createConfiguration(name, factory)

  override fun isConfigurationShared(settings: RunnerAndConfigurationSettings): Boolean {
    var shared: Boolean? = mySharedConfigurations.get(settings.uniqueID)
    if (shared == null) {
      val template = getConfigurationTemplate(settings.factory!!)
      shared = mySharedConfigurations.get(template.uniqueID)
    }
    return shared != null && shared
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(taskProviderID: Key<T>): List<T> {
    val tasks = ArrayList<T>()
    val checkedTemplates = ArrayList<RunnerAndConfigurationSettings>()
    val settingsList = ArrayList(myConfigurations.values)
    for (settings in settingsList) {
      val runTasks = getBeforeRunTasks(settings.configuration)
      for (task in runTasks) {
        if (task.isEnabled && task.providerId === taskProviderID) {
          @Suppress("UNCHECKED_CAST")
          tasks.add(task as T)
        }
        else {
          val template = getConfigurationTemplate(settings.factory!!)
          if (!checkedTemplates.contains(template)) {
            checkedTemplates.add(template)
            val templateTasks = getBeforeRunTasks(template.configuration)
            for (templateTask in templateTasks) {
              if (templateTask.isEnabled && templateTask.providerId === taskProviderID) {
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
      myIconCache.checkValidity(uniqueID)
    }
    var icon = myIconCache.get(uniqueID, settings, project)
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

  fun getConfigurationById(id: String) = myConfigurations.get(id)

  override fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings? {
    if (name == null) {
      return null
    }
    return myConfigurations.values.firstOrNull { it.name == name }
  }

  fun findConfigurationByTypeAndName(typeId: String, name: String): RunnerAndConfigurationSettings? {
    return sortedConfigurations.firstOrNull {
      val t = it.type
      t != null && typeId == t.id && name == it.name
    }
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(settings: RunConfiguration, taskProviderID: Key<T>): List<T> {
    if (settings is WrappingRunConfiguration<*>) {
      return getBeforeRunTasks(settings.peer, taskProviderID)
    }

    val tasks = settings.beforeRunTasks ?: getTemplateBeforeRunTasks(settings)
    val result = SmartList<T>()
    for (task in tasks) {
      if (task.providerId === taskProviderID) {
        @Suppress("UNCHECKED_CAST")
        result.add(task as T)
      }
    }
    return result
  }

  override fun getBeforeRunTasks(settings: RunConfiguration): List<BeforeRunTask<*>> {
    if (settings is WrappingRunConfiguration<*>) {
      return getBeforeRunTasks(settings.peer)
    }

    val tasks = settings.beforeRunTasks ?: getTemplateBeforeRunTasks(settings)
    return getCopies(tasks)
  }

  private fun getTemplateBeforeRunTasks(settings: RunConfiguration): List<BeforeRunTask<*>> {
    val template = getConfigurationTemplate(settings.factory)
    val configuration = template.configuration
    if (configuration is UnknownRunConfiguration) {
      return emptyList()
    }

    val templateTasks = configuration.beforeRunTasks ?: getHardcodedBeforeRunTasks(settings)
    return getCopies(templateTasks)
  }

  fun getHardcodedBeforeRunTasks(settings: RunConfiguration): List<BeforeRunTask<*>> {
    val _tasks = SmartList<BeforeRunTask<*>>()
    for (provider in Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME, project)) {
      val task = provider.createTask(settings)
      if (task != null && task.isEnabled) {
        val providerID = provider.id
        settings.factory.configureBeforeRunTaskDefaults(providerID, task)
        if (task.isEnabled) {
          _tasks.add(task)
        }
      }
    }
    return _tasks
  }

  fun shareConfiguration(settings: RunnerAndConfigurationSettings?, shareConfiguration: Boolean) {
    val shouldFire = settings != null && isConfigurationShared(settings) != shareConfiguration
    if (shareConfiguration && settings!!.isTemporary) {
      makeStable(settings)
    }
    mySharedConfigurations.put(settings!!.uniqueID, shareConfiguration)
    if (shouldFire) {
      fireRunConfigurationChanged(settings)
    }
  }

  override fun setBeforeRunTasks(runConfiguration: RunConfiguration, tasks: List<BeforeRunTask<*>>, addEnabledTemplateTasksIfAbsent: Boolean) {
    if (runConfiguration is UnknownRunConfiguration) {
      return
    }

    val result = SmartList(tasks)
    if (addEnabledTemplateTasksIfAbsent) {
      addTemplateBeforeTasks(runConfiguration, tasks, result)
    }

    runConfiguration.beforeRunTasks = result
    fireBeforeRunTasksUpdated()
  }

  private fun addTemplateBeforeTasks(configuration: RunConfiguration, tasks: List<BeforeRunTask<*>>, result: MutableList<BeforeRunTask<*>>) {
    val templates = getTemplateBeforeRunTasks(configuration)
    val idToSet = THashSet<Key<BeforeRunTask<*>>>()
    @Suppress("UNCHECKED_CAST")
    tasks.mapTo(idToSet) { it.providerId as Key<BeforeRunTask<*>>? }
    var i = 0
    for (template in templates) {
      if (!idToSet.contains(template.providerId)) {
        result.add(i, template)
        i++
      }
    }
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, isShared: Boolean) {
    addConfiguration(settings, isShared, getTemplateBeforeRunTasks(settings.configuration), false)
  }

  fun removeNotExistingSharedConfigurations(existing: Set<String>) {
    var removed: MutableList<RunnerAndConfigurationSettings>? = null
    val it = myConfigurations.entries.iterator()
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
}

private fun getCopies(original: List<BeforeRunTask<*>>): List<BeforeRunTask<*>> {
  val result = SmartList<BeforeRunTask<*>>()
  for (task in original) {
    if (task.isEnabled) {
      result.add(task.clone())
    }
  }
  return result
}


interface RunConfigurationScheme : Scheme

private class UnknownRunConfigurationScheme(private val name: String) : RunConfigurationScheme, SerializableScheme {
  override fun getSchemeState() = SchemeState.UNCHANGED

  override fun writeScheme() = throw AssertionError("Must be not called")

  override fun getName() = name
}