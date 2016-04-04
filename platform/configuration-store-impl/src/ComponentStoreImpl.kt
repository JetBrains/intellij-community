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

import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
import com.intellij.openapi.application.runBatchUpdate
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.components.impl.stores.StateStorageManager.ExternalizationSession
import com.intellij.openapi.components.store.ReadOnlyModificationException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NamedJDOMExternalizable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.ui.AppUIUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.JDOMXIncluder
import gnu.trove.THashMap
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import com.intellij.openapi.util.Pair as JBPair

internal val LOG = Logger.getInstance(ComponentStoreImpl::class.java)

internal val deprecatedComparator = Comparator<Storage> { o1, o2 ->
  val w1 = if (o1.deprecated) 1 else 0
  val w2 = if (o2.deprecated) 1 else 0
  w1 - w2
}

abstract class ComponentStoreImpl : IComponentStore {
  private val components = Collections.synchronizedMap(THashMap<String, Any>())
  private val settingsSavingComponents = CopyOnWriteArrayList<SettingsSavingComponent>()

  protected open val project: Project?
    get() = null

  open val loadPolicy: StateLoadPolicy
    get() = StateLoadPolicy.LOAD

  abstract val storageManager: StateStorageManager

  override final fun getStateStorageManager() = storageManager

  override final fun initComponent(component: Any, service: Boolean) {
    if (component is SettingsSavingComponent) {
      settingsSavingComponents.add(component)
    }

    @Suppress("DEPRECATION")
    if (!(component is JDOMExternalizable || component is PersistentStateComponent<*>)) {
      return
    }

    val componentNameIfStateExists: String?
    try {
      componentNameIfStateExists = if (component is PersistentStateComponent<*>) {
        val stateSpec = StoreUtil.getStateSpec(component)
        doAddComponent(stateSpec.name, component)
        @Suppress("UNCHECKED_CAST")
        initPersistentComponent(stateSpec, component as PersistentStateComponent<Any>, null, false)
      }
      else {
        @Suppress("DEPRECATION")
        initJdomExternalizable(component as JDOMExternalizable)
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      return
    }

    // if not service, so, component manager will check it later for all components
    if (componentNameIfStateExists != null && service) {
      val project = this.project
      val app = ApplicationManager.getApplication()
      if (project != null && !app.isHeadlessEnvironment && !app.isUnitTestMode && project.isInitialized) {
        notifyUnknownMacros(this, project, componentNameIfStateExists)
      }
    }
  }

  override fun save(readonlyFiles: MutableList<JBPair<StateStorage.SaveSession, VirtualFile>>) {
    val externalizationSession = if (components.isEmpty()) null else storageManager.startExternalization()
    if (externalizationSession != null) {
      val names = ArrayUtilRt.toStringArray(components.keys)
      Arrays.sort(names)
      val timeLogPrefix = "Saving"
      var timeLog = if (LOG.isDebugEnabled) StringBuilder(timeLogPrefix) else null
      for (name in names) {
        val start = if (timeLog == null) 0 else System.currentTimeMillis()
        commitComponent(externalizationSession, components[name]!!, name)
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

    var errors: MutableList<Throwable>? = null
    for (settingsSavingComponent in settingsSavingComponents) {
      try {
        settingsSavingComponent.save()
      }
      catch (e: Throwable) {
        if (errors == null) {
          errors = SmartList<Throwable>()
        }
        errors.add(e)
      }
    }

    if (externalizationSession != null) {
      errors = doSave(externalizationSession.createSaveSessions(), readonlyFiles, errors)
    }
    CompoundRuntimeException.throwIfNotEmpty(errors)
  }

  override @TestOnly fun saveApplicationComponent(component: Any) {
    val externalizationSession = storageManager.startExternalization() ?: return

    commitComponent(externalizationSession, component, null)
    val sessions = externalizationSession.createSaveSessions()
    if (sessions.isEmpty()) {
      return
    }

    val absolutePath: String
    val state = StoreUtil.getStateSpec(component.javaClass)
    if (state != null) {
      absolutePath = Paths.get(storageManager.expandMacros(findNonDeprecated(state.storages).path)).toAbsolutePath().toString()
    }
    else if (component is ExportableApplicationComponent && component is NamedJDOMExternalizable) {
      absolutePath = PathManager.getOptionsFile(component).absolutePath
    }
    else {
      throw AssertionError("${component.javaClass} doesn't have @State annotation and doesn't implement ExportableApplicationComponent")
    }

    runWriteAction {
      try {
        VfsRootAccess.allowRootAccess(absolutePath)
        CompoundRuntimeException.throwIfNotEmpty(doSave(sessions))
      }
      finally {
        VfsRootAccess.disallowRootAccess(absolutePath)
      }
    }
  }

  private fun commitComponent(session: ExternalizationSession, component: Any, componentName: String?) {
    @Suppress("DEPRECATION")
    if (component is PersistentStateComponent<*>) {
      val state = component.state
      if (state != null) {
        val stateSpec = StoreUtil.getStateSpec(component)
        session.setState(getStorageSpecs(component, stateSpec, StateStorageOperation.WRITE), component, componentName ?: stateSpec.name, state)
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

  private fun initJdomExternalizable(@Suppress("DEPRECATION") component: JDOMExternalizable): String? {
    val componentName = ComponentManagerImpl.getComponentName(component)
    doAddComponent(componentName, component)

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

  private fun doAddComponent(name: String, component: Any) {
    val existing = components.put(name, component)
    if (existing != null && existing !== component) {
      components.put(name, existing)
      LOG.error("Conflicting component name '$name': ${existing.javaClass} and ${component.javaClass}")
    }
  }

  private fun <T: Any> initPersistentComponent(stateSpec: State, component: PersistentStateComponent<T>, changedStorages: Set<StateStorage>?, reloadData: Boolean): String? {
    if (loadPolicy == StateLoadPolicy.NOT_LOAD) {
      return null
    }

    val name = stateSpec.name
    val stateClass = ComponentSerializationUtil.getStateClass<T>(component.javaClass)
    if (!stateSpec.defaultStateAsResource && LOG.isDebugEnabled && getDefaultState(component, name, stateClass) != null) {
      LOG.error("$name has default state, but not marked to load it")
    }

    val defaultState = if (stateSpec.defaultStateAsResource) getDefaultState(component, name, stateClass) else null
    if (loadPolicy == StateLoadPolicy.LOAD) {
      val storageSpecs = getStorageSpecs(component, stateSpec, StateStorageOperation.READ)
      val storageChooser = component as? StateStorageChooserEx
      for (storageSpec in storageSpecs) {
        if (storageChooser?.getResolution(storageSpec, StateStorageOperation.READ) == Resolution.SKIP) {
          continue
        }

        val storage = storageManager.getStateStorage(storageSpec)
        var stateGetter = if (isUseLoadedStateAsExisting(storage) && (ApplicationManager.getApplication().isUnitTestMode || Registry.`is`("use.loaded.state.as.existing", false))) {
          (storage as? StorageBaseEx<*>)?.createGetSession(component, name, stateClass)
        }
        else {
          null
        }
        var state = if (stateGetter == null) storage.getState(component, name, stateClass, defaultState, reloadData) else stateGetter.getState(defaultState)
        if (state == null) {
          if (changedStorages != null && changedStorages.contains(storage)) {
            // state will be null if file deleted
            // we must create empty (initial) state to reinit component
            state = DefaultStateSerializer.deserializeState(Element("state"), stateClass, null)!!
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
        return name
      }
    }

    // we load default state even if isLoadComponentState false - required for app components (for example, at least one color scheme must exists)
    if (defaultState != null) {
      component.loadState(defaultState)
    }
    return name
  }

  protected open fun isUseLoadedStateAsExisting(storage: StateStorage): Boolean = (storage as? XmlElementStorage)?.roamingType != RoamingType.DISABLED

  protected open fun getPathMacroManagerForDefaults(): PathMacroManager? = null

  private fun <T : Any> getDefaultState(component: Any, componentName: String, stateClass: Class<T>): T? {
    val url = DecodeDefaultsUtil.getDefaults(component, componentName) ?: return null
    try {
      val documentElement = JDOMXIncluder.resolve(JDOMUtil.loadDocument(url), url.toExternalForm()).detachRootElement()
      getPathMacroManagerForDefaults()?.expandPaths(documentElement)
      return DefaultStateSerializer.deserializeState(documentElement, stateClass, null)
    }
    catch (e: Throwable) {
      throw IOException("Error loading default state from $url", e)
    }
  }

  protected open fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): Array<out Storage> {
    val storages = stateSpec.storages
    if (storages.size == 1 || component is StateStorageChooserEx) {
      return storages
    }

    if (storages.isEmpty()) {
      if (stateSpec.defaultStateAsResource) {
        return storages
      }

      throw AssertionError("No storage specified")
    }
    return storages.sortByDeprecated()
  }

  override final fun isReloadPossible(componentNames: MutableSet<String>) = !componentNames.any { isNotReloadable(it) }

  private fun isNotReloadable(component: Any?) = component != null && (component !is PersistentStateComponent<*> || !StoreUtil.getStateSpec(component).reloadable)

  fun getNotReloadableComponents(componentNames: Collection<String>): Collection<String> {
    var notReloadableComponents: MutableSet<String>? = null
    for (componentName in componentNames) {
      if (isNotReloadable(components[componentName])) {
        if (notReloadableComponents == null) {
          notReloadableComponents = LinkedHashSet<String>()
        }
        notReloadableComponents.add(componentName)
      }
    }
    return notReloadableComponents ?: emptySet<String>()
  }

  override final fun reloadStates(componentNames: MutableSet<String>, messageBus: MessageBus) {
    runBatchUpdate(messageBus) {
      reinitComponents(componentNames)
    }
  }

  override final fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
    val stateSpec = StoreUtil.getStateSpecOrError(componentClass)
    @Suppress("UNCHECKED_CAST")
    val component = components[stateSpec.name] as PersistentStateComponent<Any>?
    if (component != null) {
      initPersistentComponent(stateSpec, component, emptySet(), true)
    }
  }

  private fun reloadState(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    @Suppress("UNCHECKED_CAST")
    val component = components[componentName] as PersistentStateComponent<Any>?
    if (component == null) {
      return false
    }
    else {
      val changedStoragesEmpty = changedStorages.isEmpty()
      initPersistentComponent(StoreUtil.getStateSpec(component), component, if (changedStoragesEmpty) null else changedStorages, changedStoragesEmpty)
      return true
    }
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

private fun findNonDeprecated(storages: Array<Storage>): Storage {
  for (storage in storages) {
    if (!storage.deprecated) {
      return storage
    }
  }
  throw AssertionError("All storages are deprecated")
}

enum class StateLoadPolicy {
  LOAD, LOAD_ONLY_DEFAULT, NOT_LOAD
}

internal fun Array<Storage>.sortByDeprecated(): Array<out Storage> {
  if (isEmpty()) {
    return this
  }

  if (!this[0].deprecated) {
    var othersAreDeprecated = true
    for (i in 1..size - 1) {
      if (!this[i].deprecated) {
        othersAreDeprecated = false
        break
      }
    }

    if (othersAreDeprecated) {
      return this
    }
  }

  return sortedArrayWith(deprecatedComparator)
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
    StorageUtil.doNotify(macros, project, Collections.singletonMap(substitutor, store))
  }, project.disposed)
}