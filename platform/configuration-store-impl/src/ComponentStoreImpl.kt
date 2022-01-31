// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.configurationStore

import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents
import com.intellij.diagnostic.PluginException
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.util.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.toArray
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal val LOG = logger<ComponentStoreImpl>()
private val SAVE_MOD_LOG = Logger.getInstance("#configurationStore.save.skip")

internal val deprecatedComparator = Comparator<Storage> { o1, o2 ->
  val w1 = if (o1.deprecated) 1 else 0
  val w2 = if (o2.deprecated) 1 else 0
  w1 - w2
}

private class PersistenceStateAdapter(val component: Any) : PersistentStateComponent<Any> {
  override fun getState() = component

  override fun loadState(state: Any) {
    XmlSerializerUtil.copyBean(state, component)
  }
}

private val NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT = TimeUnit.MINUTES.toSeconds(5).toInt()
private var NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT

@TestOnly
internal fun restoreDefaultNotRoamableComponentSaveThreshold() {
  NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT
}

@TestOnly
internal fun setRoamableComponentSaveThreshold(thresholdInSeconds: Int) {
  NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = thresholdInSeconds
}

@ApiStatus.Internal
abstract class ComponentStoreImpl : IComponentStore {
  private val components = ConcurrentHashMap<String, ComponentInfo>()

  open val project: Project?
    get() = null

  open val loadPolicy: StateLoadPolicy
    get() = StateLoadPolicy.LOAD

  abstract override val storageManager: StateStorageManager

  internal fun getComponents(): Map<String, ComponentInfo> = components

  override fun clearCaches() {
    components.values.forEach(Consumer {
      it.updateModificationCount(-1)
    })
    (storageManager as? StateStorageManagerImpl)?.clearStorages()
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    var componentName: String? = null
    try {
      @Suppress("DEPRECATION")
      if (component is PersistentStateComponent<*>) {
        val stateSpec = getStateSpec(component.javaClass)
        if (stateSpec == null) {
          if (loadPolicy != StateLoadPolicy.LOAD) {
            component.noStateLoaded()
            component.initializeComponent()
            return
          }

          val componentInfo = createComponentInfo(component, null, serviceDescriptor)
          initComponent(info = componentInfo, changedStorages = null, reloadData = ThreeState.NO)
        }
        else {
          componentName = stateSpec.name
          val componentInfo = createComponentInfo(component = component, stateSpec = stateSpec, serviceDescriptor = serviceDescriptor)
          // still must be added to component list to support explicit save later
          if (!stateSpec.allowLoadInTests && !(loadPolicy == StateLoadPolicy.LOAD ||
                                               (loadPolicy == StateLoadPolicy.LOAD_ONLY_DEFAULT && stateSpec.defaultStateAsResource))) {
            component.noStateLoaded()
            component.initializeComponent()
            registerComponent(name = componentName, info = componentInfo)
            return
          }

          if (initComponent(componentInfo, changedStorages = null, reloadData = ThreeState.NO) && serviceDescriptor != null) {
            // if not service, so, component manager will check it later for all components
            val project = project
            if (project != null) {
              val app = ApplicationManager.getApplication()
              if (!app.isHeadlessEnvironment && !app.isUnitTestMode && project.isInitialized) {
                notifyUnknownMacros(this, project, componentName)
              }
            }
          }
          registerComponent(name = componentName, info = componentInfo)
        }
        component.initializeComponent()
      }
      else if (loadPolicy == StateLoadPolicy.LOAD && component is com.intellij.openapi.util.JDOMExternalizable) {
        componentName = getComponentName(component)
        val componentInfo = createComponentInfo(component = component, stateSpec = null, serviceDescriptor = null)
        val element = storageManager.getOldStorage(component, componentName, StateStorageOperation.READ)
                        ?.getState(component, componentName, Element::class.java, null, false)
        if (element != null) {
          component.readExternal(element)
        }
        registerComponent(name = componentName, info = componentInfo)
      }
    }
    catch (e: Exception) {
      if (e is ControlFlowException) {
        throw e
      }
      LOG.error(PluginException("Cannot init component state " +
                                "(componentName=$componentName, componentClass=${component.javaClass.simpleName})", e, pluginId))
    }
  }

  override fun unloadComponent(component: Any) {
    @Suppress("DEPRECATION")
    val name = when (component) {
      is PersistentStateComponent<*> -> getStateSpec(component.javaClass)?.name ?: return
      is com.intellij.openapi.util.JDOMExternalizable -> getComponentName(component)
      else -> return
    }
    removeComponent(name)
  }

  final override fun initPersistencePlainComponent(component: Any, key: String) {
    val stateSpec = StateAnnotation(key, FileStorageAnnotation(StoragePathMacros.WORKSPACE_FILE, false))
    registerComponent(name = stateSpec.name,
                      info = createComponentInfo(PersistenceStateAdapter(component), stateSpec, serviceDescriptor = null))
  }

  override suspend fun save(forceSavingAllSettings: Boolean) {
    val result = SaveResult()
    doSave(result, forceSavingAllSettings)
    result.throwIfErrored()
  }

  internal abstract suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean)

  internal suspend inline fun <T> withEdtContext(crossinline task: suspend () -> T): T {
    return withEdtContext(storageManager.componentManager, task)
  }

  internal suspend fun commitComponentsOnEdt(saveResult: SaveResult, forceSavingAllSettings: Boolean,
                                             saveSessionProducerManager: SaveSessionProducerManager) {
    withEdtContext {
      val errors = SmartList<Throwable>()
      commitComponents(forceSavingAllSettings, saveSessionProducerManager, errors)
      saveResult.addErrors(errors)
      saveSessionProducerManager
    }
  }

  @RequiresEdt
  internal open fun commitComponents(isForce: Boolean, session: SaveSessionProducerManager, errors: MutableList<Throwable>) {
    if (components.isEmpty()) {
      return
    }

    val isUseModificationCount = Registry.`is`("store.save.use.modificationCount", true)

    val names = ArrayUtilRt.toStringArray(components.keys)
    Arrays.sort(names)
    @NonNls var timeLog: StringBuilder? = null

    // well, strictly speaking each component saving takes some time, but +/- several seconds doesn't matter
    val nowInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
    val isSaveModLogEnabled = SAVE_MOD_LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode
    for (name in names) {
      val start = System.currentTimeMillis()
      try {
        val info = components.get(name) ?: continue
        var currentModificationCount = -1L

        if (info.lastSaved != -1) {
          if (isForce || (nowInSeconds - info.lastSaved) > NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD) {
            info.lastSaved = nowInSeconds
          }
          else {
            if (isSaveModLogEnabled) {
              SAVE_MOD_LOG.debug("Skip $name: was already saved in last ${TimeUnit.SECONDS.toMinutes(NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT.toLong())} minutes" +
                                 " (lastSaved ${info.lastSaved}, now: $nowInSeconds)")
            }
            continue
          }
        }
        var modificationCountChanged = false
        if (info.isModificationTrackingSupported) {
          currentModificationCount = info.currentModificationCount
          if (currentModificationCount == info.lastModificationCount) {
            if (isSaveModLogEnabled) {
              SAVE_MOD_LOG.debug("${if (isUseModificationCount) "Skip " else ""}$name: modificationCount $currentModificationCount equals to last saved")
            }
            if (isUseModificationCount) {
              continue
            }
          }
          else {
            modificationCountChanged = true
          }
        }

        commitComponent(session, info, name, modificationCountChanged)
        info.updateModificationCount(currentModificationCount)
      }
      catch (e: Throwable) {
        errors.add(Exception("Cannot get $name component state", e))
      }

      val duration = System.currentTimeMillis() - start
      if (duration > 10) {
        if (timeLog == null) {
          timeLog = StringBuilder("Saving " + toString())
        }
        else {
          timeLog.append(", ")
        }
        timeLog.append(name).append(" took ").append(duration).append(" ms")
      }
    }

    if (timeLog != null) {
      LOG.info(timeLog.toString())
    }
  }

  @TestOnly
  @RequiresEdt
  override fun saveComponent(component: PersistentStateComponent<*>) {
    val stateSpec = getStateSpec(component)
    LOG.debug { "saveComponent is called for ${stateSpec.name}" }
    val saveManager = createSaveSessionProducerManager()
    commitComponent(saveManager, ComponentInfoImpl(component, stateSpec), null, false)
    val absolutePath = storageManager.expandMacro(findNonDeprecated(getStorageSpecs(component, stateSpec, StateStorageOperation.WRITE)).path).toString()
    Disposer.newDisposable().use {
      VfsRootAccess.allowRootAccess(it, absolutePath)
      runBlocking {
        val saveResult = saveManager.save()
        saveResult.throwIfErrored()

        if (!saveResult.isChanged) {
          LOG.info("saveApplicationComponent is called for ${stateSpec.name} but nothing to save")
        }
      }
    }
  }

  open fun createSaveSessionProducerManager() = SaveSessionProducerManager()

  private fun commitComponent(session: SaveSessionProducerManager,
                              info: ComponentInfo,
                              componentName: String?,
                              modificationCountChanged: Boolean) {
    val component = info.component
    @Suppress("DEPRECATION")
    if (component is com.intellij.openapi.util.JDOMExternalizable) {
      val effectiveComponentName = componentName ?: getComponentName(component)
      storageManager.getOldStorage(component, effectiveComponentName, StateStorageOperation.WRITE)?.let {
        session.getProducer(it)?.setState(component, effectiveComponentName, component)
      }
      return
    }

    var state: Any? = null
    // state can be null, so, we cannot compare to null to check is state was requested or not
    var stateRequested = false

    val stateSpec = info.stateSpec!!
    val effectiveComponentName = componentName ?: stateSpec.name
    val stateStorageChooser = component as? StateStorageChooserEx
    @Suppress("UNCHECKED_CAST")
    val storageSpecs = getStorageSpecs(component as PersistentStateComponent<Any>, stateSpec, StateStorageOperation.WRITE)
    for (storageSpec in storageSpecs) {
      @Suppress("IfThenToElvis")
      var resolution = if (stateStorageChooser == null) Resolution.DO else stateStorageChooser.getResolution(storageSpec, StateStorageOperation.WRITE)
      if (resolution == Resolution.SKIP) {
        continue
      }

      val storage = storageManager.getStateStorage(storageSpec)

      if (resolution == Resolution.DO) {
        resolution = storage.getResolution(component, StateStorageOperation.WRITE)
        if (resolution == Resolution.SKIP) {
          continue
        }
      }

      val sessionProducer = session.getProducer(storage) ?: continue
      if (resolution == Resolution.CLEAR ||
          storageSpec.deprecated && storageSpecs.none { !it.deprecated && it.value == storageSpec.value }) {
        sessionProducer.setState(component, effectiveComponentName, null)
      }
      else {
        if (!stateRequested) {
          stateRequested = true
          state = (info.component as PersistentStateComponent<*>).state
        }

        if (modificationCountChanged && state != null && isReportStatisticAllowed(stateSpec, storageSpec)) {
          LOG.runAndLogException {
            FeatureUsageSettingsEvents.logConfigurationChanged(effectiveComponentName, state, project)
          }
        }

        setStateToSaveSessionProducer(state, info, effectiveComponentName, sessionProducer)
      }
    }
  }

  // method is not called if storage is deprecated or clear was requested (state in these cases is null), but called if state is null if returned so from component
  protected open fun setStateToSaveSessionProducer(state: Any?, info: ComponentInfo, effectiveComponentName: String, sessionProducer: SaveSessionProducer) {
    sessionProducer.setState(info.component, effectiveComponentName, state)
  }

  private fun registerComponent(name: String, info: ComponentInfo): ComponentInfo {
    val existing = components.putIfAbsent(name, info)
    if (existing != null && existing.component !== info.component) {
      LOG.error("Conflicting component name '$name': ${existing.component.javaClass} and ${info.component.javaClass} (componentManager=${storageManager.componentManager})")
      return existing
    }
    else {
      return info
    }
  }

  private fun initComponent(info: ComponentInfo, changedStorages: Set<StateStorage>?, reloadData: ThreeState): Boolean {
    @Suppress("UNCHECKED_CAST")
    val component = info.component as PersistentStateComponent<Any>
    if (info.stateSpec == null) {
      val configurationSchemaKey = info.configurationSchemaKey
                                   ?: throw UnsupportedOperationException("configurationSchemaKey must be specified for ${component.javaClass.name}")
      return initComponentWithoutStateSpec(component, configurationSchemaKey)
    }
    else {
      return doInitComponent(info, component, changedStorages, reloadData)
    }
  }

  protected fun initComponentWithoutStateSpec(component: PersistentStateComponent<Any>, configurationSchemaKey: String): Boolean {
    @Suppress("UNCHECKED_CAST")
    val stateClass = ComponentSerializationUtil.getStateClass<Any>(component.javaClass)
    val storage = getReadOnlyStorage(component.javaClass, stateClass, configurationSchemaKey)
    val state = storage?.getState(component, "", stateClass, null, reload = false)
    if (state == null) {
      component.noStateLoaded()
    }
    else {
      component.loadState(state)
    }
    return true
  }

  protected open fun getReadOnlyStorage(componentClass: Class<Any>, stateClass: Class<Any>, configurationSchemaKey: String): StateStorage? {
    return null
  }

  private fun doInitComponent(info: ComponentInfo, component: PersistentStateComponent<Any>, changedStorages: Set<StateStorage>?, reloadData: ThreeState): Boolean {
    @Suppress("UNCHECKED_CAST")
    val stateClass: Class<Any> = when (component) {
      is PersistenceStateAdapter -> component.component::class.java as Class<Any>
      else -> ComponentSerializationUtil.getStateClass(component.javaClass)
    }

    val stateSpec = info.stateSpec!!
    val name = stateSpec.name

    // KT-39968: PathMacrosImpl could increase modCount on loadState and the change has to be persisted
    // all other components follow general rule: initial modCount is calculated after loadState phase
    val postLoadStateUpdateModificationCount = name != "PathMacrosImpl"

    val defaultState = if (stateSpec.defaultStateAsResource) getDefaultState(component, name, stateClass) else null
    if (loadPolicy == StateLoadPolicy.LOAD || info.stateSpec?.allowLoadInTests == true) {
      val storageChooser = component as? StateStorageChooserEx
      for (storageSpec in getStorageSpecs(component, stateSpec, StateStorageOperation.READ)) {
        if (storageChooser?.getResolution(storageSpec, StateStorageOperation.READ) == Resolution.SKIP) {
          continue
        }

        val storage = storageManager.getStateStorage(storageSpec)

        // if storage marked as  changed, it means that analyzeExternalChangesAndUpdateIfNeeded was called for it and storage is already reloaded
        val isReloadDataForStorage = if (reloadData == ThreeState.UNSURE) isStorageChanged(changedStorages!!, storage) else reloadData.toBoolean()

        val stateGetter = doCreateStateGetter(isReloadDataForStorage, storage, info, name, stateClass)
        var state = stateGetter.getState(defaultState)
        if (state == null) {
          if (changedStorages != null && isStorageChanged(changedStorages, storage)) {
            // state will be null if file deleted
            // we must create empty (initial) state to reinit component
            state = deserializeState(Element("state"), stateClass, null)!!
          }
          else {
            if (isReportStatisticAllowed(stateSpec, storageSpec)) {
              FeatureUsageSettingsEvents.logDefaultConfigurationState(name, stateClass, project)
            }
            continue
          }
        }

        if (!postLoadStateUpdateModificationCount) {
          info.updateModificationCount(info.currentModificationCount)
        }
        component.loadState(state)
        val stateAfterLoad = stateGetter.archiveState()
        if (isReportStatisticAllowed(stateSpec, storageSpec)) {
          LOG.runAndLogException {
            FeatureUsageSettingsEvents.logConfigurationState(name, stateAfterLoad ?: state, project)
          }
        }

        if (postLoadStateUpdateModificationCount) {
          info.updateModificationCount(info.currentModificationCount)
        }
        return true
      }
    }

    // we load default state even if isLoadComponentState false - required for app components (for example, at least one color scheme must exist)
    if (defaultState == null) {
      component.noStateLoaded()
    }
    else {
      if (!postLoadStateUpdateModificationCount) {
        info.updateModificationCount(info.currentModificationCount)
      }
      component.loadState(defaultState)
      if (postLoadStateUpdateModificationCount) {
        info.updateModificationCount(info.currentModificationCount)
      }
    }
    return true
  }

  protected open fun isReportStatisticAllowed(stateSpec: State, storageSpec: Storage): Boolean {
    return !storageSpec.deprecated &&
           stateSpec.reportStatistic &&
           storageSpec.value != StoragePathMacros.CACHE_FILE
  }

  private fun isStorageChanged(changedStorages: Set<StateStorage>, storage: StateStorage): Boolean {
    return changedStorages.contains(storage) || storage is ExternalStorageWithInternalPart && changedStorages.contains(storage.internalStorage)
  }

  protected open fun doCreateStateGetter(reloadData: Boolean,
                                         storage: StateStorage,
                                         info: ComponentInfo,
                                         name: String,
                                         stateClass: Class<Any>): StateGetter<Any> {
    // use.loaded.state.as.existing used in upsource
    val isUseLoadedStateAsExisting = info.stateSpec!!.useLoadedStateAsExisting && isUseLoadedStateAsExisting(storage)
    @Suppress("UNCHECKED_CAST")
    return createStateGetter(isUseLoadedStateAsExisting, storage, info.component as PersistentStateComponent<Any>, name, stateClass, reloadData)
  }

  protected open fun isUseLoadedStateAsExisting(storage: StateStorage): Boolean {
    return (storage as? XmlElementStorage)?.roamingType != RoamingType.DISABLED
           && SystemProperties.getBooleanProperty("use.loaded.state.as.existing", true)
  }

  protected open fun getPathMacroManagerForDefaults(): PathMacroManager? = null

  private fun <T : Any> getDefaultState(component: Any, componentName: String, stateClass: Class<T>): T? {
    val classLoader = component.javaClass.classLoader
    val data = ResourceUtil.getResourceAsBytes("$componentName.xml", classLoader) ?: return null
    try {
      val element = JDOMUtil.load(data)
      getPathMacroManagerForDefaults()?.expandPaths(element)
      return deserializeState(element, stateClass, null)
    }
    catch (e: Throwable) {
      throw IOException("Error loading default state for $componentName", e)
    }
  }

  protected open fun <T> getStorageSpecs(component: PersistentStateComponent<T>,
                                         stateSpec: State,
                                         operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages.modifyPerOsStorages()
    if (storages.size == 1 || component is StateStorageChooserEx) {
      return storages.toList()
    }

    if (storages.isEmpty()) {
      if (stateSpec.defaultStateAsResource) {
        return emptyList()
      }

      throw AssertionError("No storage specified")
    }
    return storages.sortByDeprecated()
  }

  private fun Array<out Storage>.modifyPerOsStorages(): Array<Storage> {
    val result = mutableListOf<Storage>()
    for (storage in this) {
      if (storage.roamingType == RoamingType.PER_OS) {
        result.add(StorageImpl.copyWithNewValue(storage, getOsDependentStorage(storage.value)))
        result.add(StorageImpl.deprecatedCopy(storage))
      }
      else {
        result.add(storage)
      }
    }
    return result.toArray(arrayOf())
  }

  final override fun isReloadPossible(componentNames: Set<String>): Boolean = !componentNames.any { isNotReloadable(it) }

  private fun isNotReloadable(name: String): Boolean {
    val component = components.get(name)?.component ?: return false
    return component !is PersistentStateComponent<*> || !getStateSpec(component).reloadable
  }

  fun getNotReloadableComponents(componentNames: Collection<String>): Collection<String> {
    var notReloadableComponents: MutableSet<String>? = null
    for (componentName in componentNames) {
      if (isNotReloadable(componentName)) {
        if (notReloadableComponents == null) {
          notReloadableComponents = LinkedHashSet()
        }
        notReloadableComponents.add(componentName)
      }
    }
    return notReloadableComponents ?: emptySet()
  }

  override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    reinitComponents(componentNames)
  }

  final override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
    val stateSpec = getStateSpecOrError(componentClass)
    val info = components.get(stateSpec.name) ?: return
    (info.component as? PersistentStateComponent<*>)?.let {
      initComponent(info, emptySet(), ThreeState.YES)
    }
  }

  private fun reloadState(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    val info = components.get(componentName) ?: return false
    if (info.component !is PersistentStateComponent<*>) {
      return false
    }

    val isChangedStoragesEmpty = changedStorages.isEmpty()
    initComponent(info, if (isChangedStoragesEmpty) null else changedStorages, ThreeState.UNSURE)
    return true
  }

  /**
   * null if reloaded
   * empty list if nothing to reload
   * list of not reloadable components (reload is not performed)
   */
  open fun reload(changedStorages: Set<StateStorage>): Collection<String>? {
    if (changedStorages.isEmpty()) {
      return emptySet()
    }

    val componentNames = HashSet<String>()
    for (storage in changedStorages) {
      LOG.runAndLogException {
        // we must update (reload in-memory storage data) even if non-reloadable component will be detected later
        // not saved -> user does own modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeeded(componentNames)
      }
    }

    if (componentNames.isEmpty()) {
      return emptySet()
    }

    val notReloadableComponents = getNotReloadableComponents(componentNames)
    reinitComponents(componentNames, changedStorages, notReloadableComponents)
    return notReloadableComponents.ifEmpty { null }
  }

  // used in settings repository plugin
  /**
   * You must call it in batch mode (use runBatchUpdate)
   */
  open fun reinitComponents(componentNames: Set<String>,
                       changedStorages: Set<StateStorage> = emptySet(),
                       notReloadableComponents: Collection<String> = emptySet()) {
    for (componentName in componentNames) {
      if (!notReloadableComponents.contains(componentName)) {
        reloadState(componentName, changedStorages)
      }
    }
  }

  override fun removeComponent(name: String) {
    components.remove(name)
  }

  override fun release() {
    components.clear()
  }

  override fun toString() = storageManager.componentManager.toString()
}

private fun findNonDeprecated(storages: List<Storage>): Storage {
  return storages.firstOrNull { !it.deprecated } ?: throw AssertionError("All storages are deprecated")
}

enum class StateLoadPolicy {
  LOAD, LOAD_ONLY_DEFAULT, NOT_LOAD
}

internal fun Array<out Storage>.sortByDeprecated(): List<Storage> {
  if (size < 2) {
    return toList()
  }

  if (!first().deprecated) {
    val othersAreDeprecated = (1 until size).any { get(it).deprecated }
    if (othersAreDeprecated) {
      return toList()
    }
  }

  return sortedWith(deprecatedComparator)
}

private fun notifyUnknownMacros(store: IComponentStore, project: Project, componentName: String) {
  val substitutor = store.storageManager.macroSubstitutor as? TrackingPathMacroSubstitutor ?: return

  val immutableMacros = substitutor.getUnknownMacros(componentName)
  if (immutableMacros.isEmpty()) {
    return
  }

  val macros = LinkedHashSet(immutableMacros)
  @Suppress("IncorrectParentDisposable")
  AppUIExecutor.onUiThread().expireWith(project).submit {
    var notified: MutableList<String>? = null
    val manager = NotificationsManager.getNotificationsManager()
    for (notification in manager.getNotificationsOfType(UnknownMacroNotification::class.java, project)) {
      if (notified == null) {
        notified = SmartList()
      }
      notified.addAll(notification.macros)
    }
    if (!notified.isNullOrEmpty()) {
      macros.removeAll(notified)
    }

    if (macros.isEmpty()) {
      return@submit
    }

    LOG.debug("Reporting unknown path macros $macros in component $componentName")
    doNotify(macros, project, Collections.singletonMap(substitutor, store))
  }
}

// to make sure that ApplicationStore or ProjectStore will not call incomplete doSave implementation
// (because these stores combine several calls for better control/async instead of simple sequential delegation)
abstract class ChildlessComponentStore : ComponentStoreImpl() {
  override suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean) {
    childlessSaveImplementation(result, forceSavingAllSettings)
  }
}

internal suspend fun ComponentStoreImpl.childlessSaveImplementation(result: SaveResult, forceSavingAllSettings: Boolean) {
  val saveSessionManager = createSaveSessionProducerManager()
  commitComponentsOnEdt(result, forceSavingAllSettings, saveSessionManager)
  saveSessionManager.save().appendTo(result)
}

internal suspend inline fun <T> withEdtContext(disposable: ComponentManager?, crossinline task: suspend () -> T): T {
  var t = AppUIExecutor.onUiThread()
  disposable?.let {
    t = t.expireWith(it)
  }
  return withContext(t.coroutineDispatchingContext()) {
    task()
  }
}

private fun getComponentName(component: Any): String {
  return if (component is NamedComponent) component.componentName else component.javaClass.name
}
