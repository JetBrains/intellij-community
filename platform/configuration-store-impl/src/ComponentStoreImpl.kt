// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.ui.AppUIUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import com.intellij.openapi.util.Pair as JBPair

internal val LOG = logger<ComponentStoreImpl>()

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

private val NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT = TimeUnit.MINUTES.toSeconds(4).toInt()
private var NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT

@TestOnly
internal fun restoreDefaultNotRoamableComponentSaveThreshold() {
  NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT
}

@TestOnly
internal fun setRoamableComponentSaveThreshold(thresholdInSeconds: Int) {
  NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD = thresholdInSeconds
}

abstract class ComponentStoreImpl : IComponentStore {
  private val components = Collections.synchronizedMap(THashMap<String, ComponentInfo>())

  internal open val project: Project?
    get() = null

  open val loadPolicy: StateLoadPolicy
    get() = StateLoadPolicy.LOAD

  abstract override val storageManager: StateStorageManager

  internal fun getComponents(): Map<String, ComponentInfo> = components

  override fun initComponent(component: Any, isService: Boolean) {
    var componentName = ""
    try {
      @Suppress("DEPRECATION")
      if (component is PersistentStateComponent<*>) {
        componentName = initPersistenceStateComponent(component, getStateSpec(component), isService)
      }
      else if (component is JDOMExternalizable) {
        componentName = ComponentManagerImpl.getComponentName(component)
        initJdomExternalizable(component, componentName)
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(PluginManagerCore.createPluginException("Cannot init $componentName component state", e, component.javaClass))
    }
  }

  override fun initPersistencePlainComponent(component: Any, key: String) {
    initPersistenceStateComponent(PersistenceStateAdapter(component),
                                  StateAnnotation(key, FileStorageAnnotation(StoragePathMacros.WORKSPACE_FILE, false)),
                                  isService = false)
  }

  private fun initPersistenceStateComponent(component: PersistentStateComponent<*>, stateSpec: State, isService: Boolean): String {
    val componentName = stateSpec.name
    val info = doAddComponent(componentName, component, stateSpec)
    if (initComponent(info, null, false) && isService) {
      // if not service, so, component manager will check it later for all components
      project?.let {
        val app = ApplicationManager.getApplication()
        if (!app.isHeadlessEnvironment && !app.isUnitTestMode && it.isInitialized) {
          notifyUnknownMacros(this, it, componentName)
        }
      }
    }
    return componentName
  }

  override suspend fun save(isForceSavingAllSettings: Boolean) {
    val result = SaveResult()
    doSave(result, isForceSavingAllSettings)
    result.throwIfErrored()
  }

  internal abstract suspend fun doSave(result: SaveResult, isForceSavingAllSettings: Boolean)

  internal suspend fun createSaveSessionManagerAndSaveComponents(saveResult: SaveResult, isForceSavingAllSettings: Boolean): SaveSessionProducerManager {
    val uiExecutor = AppUIExecutor.onUiThread()
    storageManager.componentManager?.let {
      uiExecutor.inTransaction(it)
    }
    return withContext(uiExecutor.coroutineDispatchingContext()) {
      val errors = SmartList<Throwable>()
      val manager = doCreateSaveSessionManagerAndSaveComponents(isForceSavingAllSettings, errors)
      saveResult.addErrors(errors)
      manager
    }
  }

  @CalledInAwt
  internal fun doCreateSaveSessionManagerAndSaveComponents(isForce: Boolean, errors: MutableList<Throwable>): SaveSessionProducerManager {
    val saveManager = createSaveSessionProducerManager()
    saveComponents(isForce, saveManager, errors)
    return saveManager
  }

  @CalledInAwt
  private fun saveComponents(isForce: Boolean, session: SaveSessionProducerManager, errors: MutableList<Throwable>) {
    if (components.isEmpty()) {
      return
    }

    val isUseModificationCount = Registry.`is`("store.save.use.modificationCount", true)

    val names = ArrayUtilRt.toStringArray(components.keys)
    Arrays.sort(names)
    var timeLog: StringBuilder? = null

    // well, strictly speaking each component saving takes some time, but +/- several seconds doesn't matter
    val nowInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
    for (name in names) {
      val start = System.currentTimeMillis()
      try {
        val info = components.get(name)!!
        var currentModificationCount = -1L

        if (info.isModificationTrackingSupported) {
          currentModificationCount = info.currentModificationCount
          if (currentModificationCount == info.lastModificationCount) {
            LOG.debug { "${if (isUseModificationCount) "Skip " else ""}$name: modificationCount ${currentModificationCount} equals to last saved" }
            if (isUseModificationCount) {
              continue
            }
          }
        }

        if (info.lastSaved != -1) {
          if (isForce || (nowInSeconds - info.lastSaved) > NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD) {
            info.lastSaved = nowInSeconds
          }
          else {
            LOG.debug { "Skip $name: was already saved in last ${TimeUnit.SECONDS.toMinutes(NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT.toLong())} minutes (lastSaved ${info.lastSaved}, now: $nowInSeconds)" }
            continue
          }
        }

        commitComponent(session, info, name)
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
  @CalledInAwt
  override fun saveComponent(component: PersistentStateComponent<*>) {
    val stateSpec = getStateSpec(component)
    LOG.info("saveComponent is called for ${stateSpec.name}")
    val saveManager = createSaveSessionProducerManager()
    commitComponent(saveManager, ComponentInfoImpl(component, stateSpec), null)
    val absolutePath = Paths.get(storageManager.expandMacros(findNonDeprecated(stateSpec.storages).path)).toAbsolutePath().toString()
    val newDisposable = Disposer.newDisposable()
    try {
      VfsRootAccess.allowRootAccess(newDisposable, absolutePath)
      runBlocking {
        val saveResult = saveManager.save()
        saveResult.throwIfErrored()

        val isSomethingChanged = saveResult.isChanged
        if (!isSomethingChanged) {
          LOG.info("saveApplicationComponent is called for ${stateSpec.name} but nothing to save")
        }
      }
    }
    finally {
      Disposer.dispose(newDisposable)
    }
  }

  internal open fun createSaveSessionProducerManager() = SaveSessionProducerManager()

  private fun commitComponent(session: SaveSessionProducerManager, info: ComponentInfo, componentName: String?) {
    val component = info.component
    @Suppress("DEPRECATION")
    if (component is JDOMExternalizable) {
      val effectiveComponentName = componentName ?: ComponentManagerImpl.getComponentName(component)
      storageManager.getOldStorage(component, effectiveComponentName, StateStorageOperation.WRITE)?.let {
        session.getProducer(it)?.setState(component, effectiveComponentName, component)
      }
      return
    }

    val state = (component as PersistentStateComponent<*>).state ?: return
    val stateSpec = info.stateSpec!!
    val effectiveComponentName = componentName ?: stateSpec.name
    val stateStorageChooser = component as? StateStorageChooserEx
    val storageSpecs = getStorageSpecs(component, stateSpec, StateStorageOperation.WRITE)
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

      session.getProducer(storage)?.setState(component, effectiveComponentName, if (storageSpec.deprecated || resolution == Resolution.CLEAR) null else state)
    }
  }

  private fun initJdomExternalizable(@Suppress("DEPRECATION") component: JDOMExternalizable, componentName: String): String? {
    doAddComponent(componentName, component, null)

    if (loadPolicy != StateLoadPolicy.LOAD) {
      return null
    }

    try {
      getDefaultState(component, componentName, Element::class.java)?.let { component.readExternal(it) }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }

    val element = storageManager
                    .getOldStorage(component, componentName, StateStorageOperation.READ)
                    ?.getState(component, componentName, Element::class.java, null, false)
                  ?: return null
    try {
      component.readExternal(element)
    }
    catch (e: InvalidDataException) {
      LOG.error(e)
      return null
    }
    return componentName
  }

  private fun doAddComponent(name: String, component: Any, stateSpec: State?): ComponentInfo {
    val newInfo = createComponentInfo(component, stateSpec)
    val existing = components.put(name, newInfo)
    if (existing != null && existing.component !== component) {
      components.put(name, existing)
      LOG.error("Conflicting component name '$name': ${existing.component.javaClass} and ${component.javaClass}")
      return existing
    }
    return newInfo
  }

  private fun initComponent(info: ComponentInfo, changedStorages: Set<StateStorage>?, reloadData: Boolean): Boolean {
    if (loadPolicy == StateLoadPolicy.NOT_LOAD) {
      return false
    }

    @Suppress("UNCHECKED_CAST")
    if (doInitComponent(info.stateSpec!!, info.component as PersistentStateComponent<Any>, changedStorages, reloadData)) {
      // if component was initialized, update lastModificationCount
      info.updateModificationCount()
      return true
    }
    return false
  }

  private fun doInitComponent(stateSpec: State,
                              component: PersistentStateComponent<Any>,
                              changedStorages: Set<StateStorage>?,
                              reloadData: Boolean): Boolean {
    val name = stateSpec.name
    @Suppress("UNCHECKED_CAST")
    val stateClass: Class<Any> = if (component is PersistenceStateAdapter) component.component::class.java as Class<Any>
    else ComponentSerializationUtil.getStateClass<Any>(component.javaClass)
    if (!stateSpec.defaultStateAsResource && LOG.isDebugEnabled && getDefaultState(component, name, stateClass) != null) {
      LOG.error("$name has default state, but not marked to load it")
    }

    val defaultState = if (stateSpec.defaultStateAsResource) getDefaultState(component, name, stateClass) else null
    if (loadPolicy == StateLoadPolicy.LOAD) {
      val storageChooser = component as? StateStorageChooserEx
      for (storageSpec in getStorageSpecs(component, stateSpec, StateStorageOperation.READ)) {
        if (storageChooser?.getResolution(storageSpec, StateStorageOperation.READ) == Resolution.SKIP) {
          continue
        }

        val storage = storageManager.getStateStorage(storageSpec)
        val stateGetter = createStateGetter(isUseLoadedStateAsExistingForComponent(storage, name), storage, component, name, stateClass,
                                            reloadData = reloadData)
        var state = stateGetter.getState(defaultState)
        if (state == null) {
          if (changedStorages != null && changedStorages.contains(storage)) {
            // state will be null if file deleted
            // we must create empty (initial) state to reinit component
            state = deserializeState(Element("state"), stateClass, null)!!
          }
          else {
            FeatureUsageSettingsEvents.logDefaultConfigurationState(name, stateSpec, stateClass, project)
            continue
          }
        }

        component.loadState(state)
        val stateAfterLoad = stateGetter.archiveState()
        LOG.runAndLogException {
          FeatureUsageSettingsEvents.logConfigurationState(name, stateSpec, stateAfterLoad ?: state, project)
        }
        return true
      }
    }

    // we load default state even if isLoadComponentState false - required for app components (for example, at least one color scheme must exists)
    if (defaultState == null) {
      component.noStateLoaded()
    }
    else {
      component.loadState(defaultState)
    }
    return true
  }

  // todo fix FacetManager
  // use.loaded.state.as.existing used in upsource
  private fun isUseLoadedStateAsExistingForComponent(storage: StateStorage, name: String): Boolean {
    return isUseLoadedStateAsExisting(storage) &&
           name != "AntConfiguration" &&
           name != "ProjectModuleManager" /* why after loadState we get empty state on getState, test CMakeWorkspaceContentRootsTest */ &&
           name != "FacetManager" &&
           name != "ProjectRunConfigurationManager" && /* ProjectRunConfigurationManager is used only for IPR, avoid relatively cost call getState */
           name != "NewModuleRootManager" /* will be changed only on actual user change, so, to speed up module loading, skip it */ &&
           name != "DeprecatedModuleOptionManager" /* doesn't make sense to check it */ &&
           SystemProperties.getBooleanProperty("use.loaded.state.as.existing", true)
  }

  protected open fun isUseLoadedStateAsExisting(storage: StateStorage): Boolean = (storage as? XmlElementStorage)?.roamingType != RoamingType.DISABLED

  protected open fun getPathMacroManagerForDefaults(): PathMacroManager? = null

  private fun <T : Any> getDefaultState(component: Any, componentName: String, stateClass: Class<T>): T? {
    val url = DecodeDefaultsUtil.getDefaults(component, componentName) ?: return null
    try {
      val element = JDOMUtil.load(url)
      getPathMacroManagerForDefaults()?.expandPaths(element)
      return deserializeState(element, stateClass, null)
    }
    catch (e: Throwable) {
      throw IOException("Error loading default state from $url", e)
    }
  }

  protected open fun <T> getStorageSpecs(component: PersistentStateComponent<T>,
                                         stateSpec: State,
                                         operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages
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

  final override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    runBatchUpdate(messageBus) {
      reinitComponents(componentNames)
    }
  }

  final override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
    val stateSpec = getStateSpecOrError(componentClass)
    val info = components.get(stateSpec.name) ?: return
    (info.component as? PersistentStateComponent<*>)?.let {
      initComponent(info, emptySet(), true)
    }
  }

  private fun reloadState(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    val info = components.get(componentName) ?: return false
    if (info.component !is PersistentStateComponent<*>) {
      return false
    }

    val changedStoragesEmpty = changedStorages.isEmpty()
    initComponent(info, if (changedStoragesEmpty) null else changedStorages, changedStoragesEmpty)
    return true
  }

  /**
   * null if reloaded
   * empty list if nothing to reload
   * list of not reloadable components (reload is not performed)
   */
  fun reload(changedStorages: Set<StateStorage>): Collection<String>? {
    if (changedStorages.isEmpty()) {
      return emptySet()
    }

    val componentNames = SmartHashSet<String>()
    for (storage in changedStorages) {
      try {
        // we must update (reload in-memory storage data) even if non-reloadable component will be detected later
        // not saved -> user does own modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeed(componentNames)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    if (componentNames.isEmpty) {
      return emptySet()
    }

    val notReloadableComponents = getNotReloadableComponents(componentNames)
    reinitComponents(componentNames, changedStorages, notReloadableComponents)
    return if (notReloadableComponents.isEmpty()) null else notReloadableComponents
  }

  // used in settings repository plugin
  /**
   * You must call it in batch mode (use runBatchUpdate)
   */
  fun reinitComponents(componentNames: Set<String>,
                       changedStorages: Set<StateStorage> = emptySet(),
                       notReloadableComponents: Collection<String> = emptySet()) {
    for (componentName in componentNames) {
      if (!notReloadableComponents.contains(componentName)) {
        reloadState(componentName, changedStorages)
      }
    }
  }

  @TestOnly
  fun removeComponent(name: String) {
    components.remove(name)
  }

  override fun toString() = storageManager.componentManager.toString()
}

private fun findNonDeprecated(storages: Array<Storage>) = storages.firstOrNull { !it.deprecated } ?: throw AssertionError(
  "All storages are deprecated")

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
  AppUIUtil.invokeOnEdt(Runnable {
    var notified: MutableList<String>? = null
    val manager = NotificationsManager.getNotificationsManager()
    for (notification in manager.getNotificationsOfType(UnknownMacroNotification::class.java, project)) {
      if (notified == null) {
        notified = SmartList<String>()
      }
      notified.addAll(notification.macros)
    }
    if (!notified.isNullOrEmpty()) {
      macros.removeAll(notified!!)
    }

    if (macros.isEmpty()) {
      return@Runnable
    }

    LOG.debug("Reporting unknown path macros $macros in component $componentName")
    doNotify(macros, project, Collections.singletonMap(substitutor, store))
  }, project.disposed)
}

// to make sure that ApplicationStore or ProjectStore will not call incomplete doSave implementation
// (because these stores combine several calls for better control/async instead of simple sequential delegation)
abstract class ChildlessComponentStore : ComponentStoreImpl() {
  override suspend fun doSave(result: SaveResult, isForceSavingAllSettings: Boolean) {
    createSaveSessionManagerAndSaveComponents(result, isForceSavingAllSettings)
      .save()
      .appendTo(result)
  }
}