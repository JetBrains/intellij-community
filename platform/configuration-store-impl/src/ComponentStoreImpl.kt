/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.configurationStore.StateStorageManager.ExternalizationSession
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.impl.stores.UnknownMacroNotification
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.project.isDirectoryBased
import com.intellij.ui.AppUIUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.JDOMXIncluder
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import io.netty.util.internal.SystemPropertyUtil
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Paths
import java.util.*
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

abstract class ComponentStoreImpl : IComponentStore {
  private val components = Collections.synchronizedMap(THashMap<String, ComponentInfo>())
  private val settingsSavingComponents = com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList<SettingsSavingComponent>()
  
  internal open val project: Project?
    get() = null

  open val loadPolicy: StateLoadPolicy
    get() = StateLoadPolicy.LOAD

  abstract val storageManager: StateStorageManager

  override final fun getStateStorageManager() = storageManager

  override final fun initComponent(component: Any, isService: Boolean) {
    if (component is SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }

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
    initPersistenceStateComponent(PersistenceStateAdapter(component), StateAnnotation(key, FileStorageAnnotation(StoragePathMacros.WORKSPACE_FILE, false)), false)
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

  override fun save(readonlyFiles: MutableList<JBPair<StateStorage.SaveSession, VirtualFile>>) {
    var errors: MutableList<Throwable>? = null

    // component state uses scheme manager in an ipr project, so, we must save it before
    val isIprProject = project?.let { !it.isDirectoryBased } ?: false
    if (isIprProject) {
      settingsSavingComponents.firstOrNull { it is SchemeManagerFactoryBase }?.let {
        try {
          it.save()
        }
        catch (e: Throwable) {
          if (errors == null) {
            errors = SmartList<Throwable>()
          }
          errors!!.add(e)
        }
      }
    }

    val isUseModificationCount = Registry.`is`("store.save.use.modificationCount", true)
    val externalizationSession = if (components.isEmpty()) null else storageManager.startExternalization()
    if (externalizationSession != null) {
      val names = ArrayUtilRt.toStringArray(components.keys)
      Arrays.sort(names)
      val timeLogPrefix = "Saving"
      val timeLog = if (LOG.isDebugEnabled) StringBuilder(timeLogPrefix) else null
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

          commitComponent(externalizationSession, info, name)
          info.updateModificationCount(currentModificationCount)
        }
        catch (e: Throwable) {
          if (errors == null) {
            errors = SmartList<Throwable>()
          }
          errors!!.add(Exception("Cannot get $name component state", e))
        }

        timeLog?.let {
          val duration = System.currentTimeMillis() - start
          if (duration > 10) {
            it.append("\n").append(name).append(" took ").append(duration).append(" ms: ").append((duration / 60000)).append(" min ").append(((duration % 60000) / 1000)).append("sec")
          }
        }
      }

      if (timeLog != null && timeLog.length > timeLogPrefix.length) {
        LOG.debug(timeLog.toString())
      }
    }

    for (settingsSavingComponent in settingsSavingComponents) {
      try {
        if (!isIprProject || settingsSavingComponent !is SchemeManagerFactoryBase) {
          settingsSavingComponent.save()
        }
      }
      catch (e: Throwable) {
        if (errors == null) {
          errors = SmartList<Throwable>()
        }
        errors!!.add(e)
      }
    }

    if (externalizationSession != null) {
      errors = doSave(externalizationSession.createSaveSessions(), readonlyFiles, errors)
    }
    CompoundRuntimeException.throwIfNotEmpty(errors)
  }

  override @TestOnly fun saveApplicationComponent(component: PersistentStateComponent<*>) {
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
        CompoundRuntimeException.throwIfNotEmpty(doSave(sessions))
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

  protected open fun doSave(saveSessions: List<SaveSession>, readonlyFiles: MutableList<JBPair<SaveSession, VirtualFile>> = arrayListOf(), prevErrors: MutableList<Throwable>? = null): MutableList<Throwable>? {
    var errors = prevErrors
    for (session in saveSessions) {
      errors = executeSave(session, readonlyFiles, prevErrors)
    }
    return errors
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

    val element = storageManager.getOldStorage(component, componentName, StateStorageOperation.READ)?.getState(component, componentName, Element::class.java, null, false) ?: return null
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
    val newInfo = when (component) {
      is ModificationTracker -> ComponentWithModificationTrackerInfo(component, stateSpec)
      is PersistentStateComponentWithModificationTracker<*> -> ComponentWithStateModificationTrackerInfo(component, stateSpec!!)
      else -> ComponentInfoImpl(component, stateSpec)
    }

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

  private fun doInitComponent(stateSpec: State, component: PersistentStateComponent<Any>, changedStorages: Set<StateStorage>?, reloadData: Boolean): Boolean {
    val name = stateSpec.name
    @Suppress("UNCHECKED_CAST")
    val stateClass: Class<Any> = if (component is PersistenceStateAdapter) component.component::class.java as Class<Any> else ComponentSerializationUtil.getStateClass<Any>(component.javaClass)
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
        val stateGetter = if (isUseLoadedStateAsExisting(storage, name)) (storage as? StorageBaseEx<*>)?.createGetSession(component, name, stateClass) else null
        var state = if (stateGetter == null) storage.getState(component, name, stateClass, defaultState, reloadData) else stateGetter.getState(defaultState)
        if (state == null) {
          if (changedStorages != null && changedStorages.contains(storage)) {
            // state will be null if file deleted
            // we must create empty (initial) state to reinit component
            state = deserializeState(Element("state"), stateClass, null)!!
          }
          else {
            continue
          }
        }

        try {
          component.loadState(state)
        }
        finally {
          stateGetter?.close()
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

  // todo "ProjectModuleManager" investigate why after loadState we get empty state on getState, test CMakeWorkspaceContentRootsTest
  // todo fix FacetManager
  // use.loaded.state.as.existing used in upsource
  private fun isUseLoadedStateAsExisting(storage: StateStorage, name: String): Boolean {
    return isUseLoadedStateAsExisting(storage) &&
           name != "AntConfiguration" &&
           name != "ProjectModuleManager" &&
           name != "FacetManager" &&
           name != "ProjectRunConfigurationManager" && /* ProjectRunConfigurationManager is used only for IPR, avoid relatively cost call getState */
           name != "NewModuleRootManager" /* will be changed only on actual user change, so, to speed up module loading, skip it */ &&
           name != "DeprecatedModuleOptionManager" /* doesn't make sense to check it */ &&
           SystemPropertyUtil.getBoolean("use.loaded.state.as.existing", true)
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

  protected open fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
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

  final override fun isReloadPossible(componentNames: MutableSet<String>) = !componentNames.any { isNotReloadable(it) }

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

  override final fun reloadStates(componentNames: MutableSet<String>, messageBus: MessageBus) {
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
  fun reinitComponents(componentNames: Set<String>, changedStorages: Set<StateStorage> = emptySet(), notReloadableComponents: Collection<String> = emptySet()) {
    for (componentName in componentNames) {
      if (!notReloadableComponents.contains(componentName)) {
        reloadState(componentName, changedStorages)
      }
    }
  }

  @TestOnly fun removeComponent(name: String) {
    components.remove(name)
  }
}

internal fun executeSave(session: SaveSession, readonlyFiles: MutableList<JBPair<SaveSession, VirtualFile>>, previousErrors: MutableList<Throwable>?): MutableList<Throwable>? {
  var errors = previousErrors
  try {
    session.save()
  }
  catch (e: ReadOnlyModificationException) {
    LOG.warn(e)
    readonlyFiles.add(JBPair.create<SaveSession, VirtualFile>(e.session ?: session, e.file))
  }
  catch (e: Exception) {
    if (errors == null) {
      errors = SmartList<Throwable>()
    }
    errors.add(e)
  }

  return errors
}

private fun findNonDeprecated(storages: Array<Storage>) = storages.firstOrNull { !it.deprecated } ?: throw AssertionError("All storages are deprecated")

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
  val substitutor = store.stateStorageManager.macroSubstitutor ?: return

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

private interface ComponentInfo {
  val component: Any
  val stateSpec: State?

  val lastModificationCount: Long
  val currentModificationCount: Long

  val isModificationTrackingSupported: Boolean

  fun updateModificationCount(newCount: Long = currentModificationCount) {
  }
}

private class ComponentInfoImpl(override val component: Any, override val stateSpec: State?) : ComponentInfo {
  override val isModificationTrackingSupported = false

  override val lastModificationCount: Long
    get() = -1

  override val currentModificationCount: Long
    get() = -1
}

private abstract class ModificationTrackerAwareComponentInfo : ComponentInfo {
  override final val isModificationTrackingSupported = true

  override abstract var lastModificationCount: Long

  override final fun updateModificationCount(newCount: Long) {
    lastModificationCount = newCount
  }
}

private class ComponentWithStateModificationTrackerInfo(override val component: PersistentStateComponentWithModificationTracker<*>, override val stateSpec: State) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.stateModificationCount

  override var lastModificationCount = currentModificationCount
}

private class ComponentWithModificationTrackerInfo(override val component: ModificationTracker, override val stateSpec: State?) : ModificationTrackerAwareComponentInfo() {
  override val currentModificationCount: Long
    get() = component.modificationCount

  override var lastModificationCount = currentModificationCount
}