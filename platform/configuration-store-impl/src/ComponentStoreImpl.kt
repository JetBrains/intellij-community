// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.StateStorageManager.ExternalizationSession
import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
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
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.JDOMXIncluder
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import com.intellij.openapi.util.Pair as JBPair

internal val LOG = Logger.getInstance(ComponentStoreImpl::class.java)

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

  override abstract val storageManager: StateStorageManager

  internal fun getComponents(): Map<String, ComponentInfo> {
    return components
  }

  override fun initComponent(component: Any, isService: Boolean) {
    var componentName = ""
    try {
      @Suppress("DEPRECATION")
      if (component is PersistentStateComponent<*>) {
        componentName = initPersistenceStateComponent(component, StoreUtil.getStateSpec(component), isService)
      }
      else if (component is JDOMExternalizable) {
        componentName = ComponentManagerImpl.getComponentName(component)
        @Suppress("DEPRECATION")
        initJdomExternalizable(component, componentName)
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Cannot init $componentName component state", e)
      return
    }
  }

  override fun initPersistencePlainComponent(component: Any, key: String) {
    initPersistenceStateComponent(PersistenceStateAdapter(component),
                                  StateAnnotation(key, FileStorageAnnotation(StoragePathMacros.WORKSPACE_FILE, false)), false)
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

  override final fun save(readonlyFiles: MutableList<SaveSessionAndFile>, isForce: Boolean) {
    val errors: MutableList<Throwable> = SmartList<Throwable>()

    beforeSaveComponents(errors)

    val externalizationSession = if (components.isEmpty()) null else storageManager.startExternalization()
    if (externalizationSession != null) {
      doSaveComponents(isForce, externalizationSession, errors)
    }

    afterSaveComponents(errors)

    try {
      saveAdditionalComponents(isForce)
    }
    catch (e: Throwable) {
      errors.add(e)
    }

    if (externalizationSession != null) {
      doSave(externalizationSession.createSaveSessions(), readonlyFiles, errors)
    }
    CompoundRuntimeException.throwIfNotEmpty(errors)
  }

  protected open fun saveAdditionalComponents(isForce: Boolean) {
  }

  protected open fun beforeSaveComponents(errors: MutableList<Throwable>) {
  }

  protected open fun afterSaveComponents(errors: MutableList<Throwable>) {
  }

  protected  open fun doSaveComponents(isForce: Boolean, externalizationSession: ExternalizationSession, errors: MutableList<Throwable>): MutableList<Throwable>? {
    val isUseModificationCount = Registry.`is`("store.save.use.modificationCount", true)

    val names = ArrayUtilRt.toStringArray(components.keys)
    Arrays.sort(names)
    val timeLogPrefix = "Saving"
    val timeLog = if (LOG.isDebugEnabled) StringBuilder(timeLogPrefix) else null

    // well, strictly speaking each component saving takes some time, but +/- several seconds doesn't matter
    val nowInSeconds: Int = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
    for (name in names) {
      val start = if (timeLog == null) 0 else System.currentTimeMillis()

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

        commitComponent(externalizationSession, info, name)
        info.updateModificationCount(currentModificationCount)
      }
      catch (e: Throwable) {
        errors.add(Exception("Cannot get $name component state", e))
      }

      timeLog?.let {
        val duration = System.currentTimeMillis() - start
        if (duration > 10) {
          it.append("\n").append(name).append(" took ").append(duration).append(" ms: ").append((duration / 60000)).append(" min ").append(
            ((duration % 60000) / 1000)).append("sec")
        }
      }
    }

    if (timeLog != null && timeLog.length > timeLogPrefix.length) {
      LOG.debug(timeLog.toString())
    }
    return errors
  }

  @TestOnly
  override fun saveApplicationComponent(component: PersistentStateComponent<*>) {
    val externalizationSession = storageManager.startExternalization() ?: return

    val stateSpec = StoreUtil.getStateSpec(component)
    commitComponent(externalizationSession, ComponentInfoImpl(component, stateSpec), null)
    val sessions = externalizationSession.createSaveSessions()
    if (sessions.isEmpty()) {
      return
    }

    val absolutePath = Paths.get(storageManager.expandMacros(findNonDeprecated(stateSpec.storages).path)).toAbsolutePath().toString()
    runUndoTransparentWriteAction {
      try {
        VfsRootAccess.allowRootAccess(absolutePath)
        val errors: MutableList<Throwable> = SmartList<Throwable>()
        doSave(sessions, errors = errors)
        CompoundRuntimeException.throwIfNotEmpty(errors)
      }
      finally {
        VfsRootAccess.disallowRootAccess(absolutePath)
      }
    }
  }

  private fun commitComponent(session: ExternalizationSession, info: ComponentInfo, componentName: String?) {
    val component = info.component
    @Suppress("DEPRECATION")
    if (component is PersistentStateComponent<*>) {
      component.state?.let {
        val stateSpec = info.stateSpec!!
        session.setState(getStorageSpecs(component, stateSpec, StateStorageOperation.WRITE), component, componentName ?: stateSpec.name, it)
      }
    }
    else if (component is JDOMExternalizable) {
      session.setStateInOldStorage(component, componentName ?: ComponentManagerImpl.getComponentName(component), component)
    }
  }

  protected open fun doSave(saveSessions: List<SaveSession>,
                            readonlyFiles: MutableList<SaveSessionAndFile> = arrayListOf(),
                            errors: MutableList<Throwable>) {
    for (session in saveSessions) {
      executeSave(session, readonlyFiles, errors)
    }
    return
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

    val element = storageManager.getOldStorage(component, componentName, StateStorageOperation.READ)?.getState(component, componentName,
                                                                                                               Element::class.java, null,
                                                                                                               false) ?: return null
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

        try {
          component.loadState(state)
        }
        finally {
          val stateAfterLoad = stateGetter.close()
          (stateAfterLoad ?: state).let {
            FeatureUsageSettingsEvents.logConfigurationState(name, stateSpec, it, project)
          }
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
      val documentElement = JDOMXIncluder.resolve(JDOMUtil.loadDocument(url), url.toExternalForm()).detachRootElement()
      getPathMacroManagerForDefaults()?.expandPaths(documentElement)
      return deserializeState(documentElement, stateClass, null)
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
    return component !is PersistentStateComponent<*> || !StoreUtil.getStateSpec(component).reloadable
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

  override final fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    runBatchUpdate(messageBus) {
      reinitComponents(componentNames)
    }
  }

  override final fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
    val stateSpec = StoreUtil.getStateSpecOrError(componentClass)
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
}

internal fun executeSave(session: SaveSession, readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>) {
  try {
    session.save()
  }
  catch (e: ReadOnlyModificationException) {
    LOG.warn(e)
    readonlyFiles.add(SaveSessionAndFile(e.session ?: session, e.file))
  }
  catch (e: Exception) {
    errors.add(e)
  }
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
  val substitutor = store.storageManager.macroSubstitutor ?: return

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