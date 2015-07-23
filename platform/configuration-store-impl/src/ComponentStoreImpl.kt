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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ex.DecodeDefaultsUtil
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
import com.intellij.openapi.util
import com.intellij.openapi.util.*
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.SmartHashSet
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.JDOMXIncluder
import gnu.trove.THashMap
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.jvm.java

private val LOG = Logger.getInstance(javaClass<ComponentStoreImpl>())

public abstract class ComponentStoreImpl : IComponentStore {
  private val myComponents = Collections.synchronizedMap(THashMap<String, Any>())
  private val mySettingsSavingComponents = CopyOnWriteArrayList<SettingsSavingComponent>()

  protected open val defaultStorageChooser: StateStorageChooser<PersistentStateComponent<*>>? = null

  override fun initComponent(component: Any, service: Boolean) {
    if (component is SettingsSavingComponent) {
      mySettingsSavingComponents.add(component)
    }

    if (!(component is JDOMExternalizable || component is PersistentStateComponent<*>)) {
      return
    }

    val token = ReadAction.start()
    try {
      val componentNameIfStateExists: String?
      if (component is PersistentStateComponent<*>) {
        componentNameIfStateExists = initPersistentComponent(component, null, false)
      }
      else {
        componentNameIfStateExists = initJdomExternalizable(component as JDOMExternalizable)
      }

      // if not service, so, component manager will check it later for all components
      if (componentNameIfStateExists != null && service) {
        val project = getProject()
        val app = ApplicationManager.getApplication()
        if (project != null && !app.isHeadlessEnvironment() && !app.isUnitTestMode() && project.isInitialized()) {
          val substitutor = getStateStorageManager().getMacroSubstitutor()
          if (substitutor != null) {
            StorageUtil.notifyUnknownMacros(substitutor, project, componentNameIfStateExists)
          }
        }
      }
    }
    catch (e: StateStorageException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    finally {
      token.finish()
    }
  }

  override fun save(readonlyFiles: MutableList<util.Pair<StateStorage.SaveSession, VirtualFile>>) {
    val externalizationSession = if (myComponents.isEmpty()) null else getStateStorageManager().startExternalization()
    if (externalizationSession != null) {
      val names = ArrayUtilRt.toStringArray(myComponents.keySet())
      Arrays.sort(names)
      for (name in names) {
        commitComponent(externalizationSession, myComponents.get(name)!!, name)
      }
    }

    var errors: MutableList<Throwable>? = null
    for (settingsSavingComponent in mySettingsSavingComponents) {
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

    errors = doSave(externalizationSession?.createSaveSessions(), readonlyFiles, errors)
    CompoundRuntimeException.doThrow(errors)
  }

  override TestOnly fun saveApplicationComponent(component: Any) {
    val externalizationSession = getStateStorageManager().startExternalization() ?: return

    commitComponent(externalizationSession, component, null)
    val sessions = externalizationSession.createSaveSessions()
    if (sessions.isEmpty()) {
      return
    }

    val file: File
    val state = StoreUtil.getStateSpec(component.javaClass)
    if (state != null) {
      file = File(getStateStorageManager().expandMacros(findNonDeprecated(state.storages).file))
    }
    else if (component is ExportableApplicationComponent && component is NamedJDOMExternalizable) {
      file = PathManager.getOptionsFile(component)
    }
    else {
      throw AssertionError("${component.javaClass} doesn't have @State annotation and doesn't implement ExportableApplicationComponent")
    }

    val token = WriteAction.start()
    try {
      VfsRootAccess.allowRootAccess(file.getAbsolutePath())
      CompoundRuntimeException.doThrow(doSave(sessions, arrayListOf(), null))
    }
    finally {
      try {
        VfsRootAccess.disallowRootAccess(file.getAbsolutePath())
      }
      finally {
        token.finish()
      }
    }
  }

  private fun commitComponent(externalizationSession: ExternalizationSession, component: Any, componentName: String?) {
    if (component is PersistentStateComponent<*>) {
      commitPersistentComponent(component, externalizationSession, componentName)
    }
    else if (component is JDOMExternalizable) {
      externalizationSession.setStateInOldStorage(component, componentName ?: ComponentManagerImpl.getComponentName(component), component)
    }
  }

  protected open fun doSave(saveSessions: List<SaveSession>?, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, prevErrors: MutableList<Throwable>?): MutableList<Throwable>? {
    var errors = prevErrors
    if (saveSessions != null) {
      for (session in saveSessions) {
        errors = executeSave(session, readonlyFiles, prevErrors)
      }
    }
    return errors
  }

  private fun <T> commitPersistentComponent(component: PersistentStateComponent<T>, session: ExternalizationSession, componentName: String?) {
    val state = component.getState()
    if (state != null) {
      val storageSpecs = getComponentStorageSpecs(component, StoreUtil.getStateSpec(component), StateStorageOperation.WRITE)
      session.setState(storageSpecs, component, componentName ?: StoreUtil.getComponentName(component), state)
    }
  }

  private fun initJdomExternalizable(component: JDOMExternalizable): String? {
    val componentName = ComponentManagerImpl.getComponentName(component)
    doAddComponent(componentName, component)

    if (optimizeTestLoading()) {
      return null
    }

    loadJdomDefaults(component, componentName)

    val stateStorage = getStateStorageManager().getOldStorage(component, componentName, StateStorageOperation.READ) ?: return null
    val element = stateStorage.getState<Element>(component, componentName, javaClass<Element>(), null) ?: return null
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Loading configuration for " + component.javaClass)
      }
      component.readExternal(element)
    }
    catch (e: InvalidDataException) {
      LOG.error(e)
      return null
    }


    return componentName
  }

  private fun doAddComponent(componentName: String, component: Any) {
    val existing = myComponents.get(componentName)
    if (existing != null && existing !== component) {
      LOG.error("Conflicting component name '" + componentName + "': " + existing.javaClass + " and " + component.javaClass)
    }
    myComponents.put(componentName, component)
  }

  private fun loadJdomDefaults(component: JDOMExternalizable, componentName: String) {
    try {
      val defaultState = getDefaultState(component, componentName, javaClass<Element>())
      if (defaultState != null) {
        component.readExternal(defaultState)
      }
    }
    catch (e: Exception) {
      LOG.error("Cannot load defaults for " + component.javaClass, e)
    }

  }

  protected open fun getProject(): Project? = null

  private fun <T> initPersistentComponent(component: PersistentStateComponent<T>, changedStorages: Set<StateStorage>?, reloadData: Boolean): String? {
    val stateSpec = StoreUtil.getStateSpec(component)
    val name = stateSpec.name
    if (changedStorages == null || !reloadData) {
      doAddComponent(name, component)
    }
    if (optimizeTestLoading()) {
      return null
    }

    val stateClass = ComponentSerializationUtil.getStateClass<T>(component.javaClass)
    if (!stateSpec.defaultStateAsResource && LOG.isDebugEnabled() && getDefaultState(component, name, stateClass) != null) {
      LOG.error(name + " has default state, but not marked to load it")
    }

    var state = if (stateSpec.defaultStateAsResource) getDefaultState(component, name, stateClass) else null
    val storageSpecs = getComponentStorageSpecs(component, stateSpec, StateStorageOperation.READ)
    val stateStorageChooser = component as? StateStorageChooserEx
    for (storageSpec in storageSpecs) {
      val resolution = if (stateStorageChooser == null) Resolution.DO else stateStorageChooser.getResolution(storageSpec, StateStorageOperation.READ)
      if (resolution === Resolution.SKIP) {
        continue
      }

      val stateStorage = getStateStorageManager().getStateStorage(storageSpec)
      if (stateStorage != null) {
        var forcedState = false
        if (!stateStorage.hasState(component, name, stateClass, reloadData)) {
          forcedState = changedStorages != null && changedStorages.contains(stateStorage)
          if (!forcedState) {
            continue
          }
        }

        state = stateStorage.getState(component, name, stateClass, state)
        if (state == null && forcedState) {
          // state will be null if file deleted
          // we must create empty (initial) state to reinit component
          state = DefaultStateSerializer.deserializeState(Element("state"), stateClass, null)
        }
        break
      }
    }

    if (state != null) {
      component.loadState(state)
    }

    return name
  }

  protected open fun getPathMacroManagerForDefaults(): PathMacroManager? = null

  protected fun <T : Any> getDefaultState(component: Any, componentName: String, stateClass: Class<T>): T? {
    val url = DecodeDefaultsUtil.getDefaults(component, componentName) ?: return null
    try {
      val documentElement = JDOMXIncluder.resolve(JDOMUtil.loadDocument(url), url.toExternalForm()).detachRootElement()
      getPathMacroManagerForDefaults()?.expandPaths(documentElement)
      return DefaultStateSerializer.deserializeState<T>(documentElement, stateClass, null)
    }
    catch (e: IOException) {
      throw StateStorageException("Error loading state from " + url, e)
    }
    catch (e: JDOMException) {
      throw StateStorageException("Error loading state from " + url, e)
    }

  }

  protected open fun <T> getComponentStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): Array<Storage> {
    val storages = stateSpec.storages
    if (storages.size() == 1 || component is StateStorageChooserEx) {
      return storages
    }
    assert(!storages.isEmpty())

    var storageChooserClass = stateSpec.storageChooser.java
    if (storageChooserClass != javaClass<StateStorageChooser<*>>()) {
      @suppress("UNCHECKED_CAST")
      val stateStorageChooser: StateStorageChooser<Any> = ReflectionUtil.newInstance(stateSpec.storageChooser.java as Class<out StateStorageChooser<Any>>)
      return stateStorageChooser.selectStorages(storages, component, operation)
    }

    val defaultChooser = defaultStorageChooser
    if (defaultChooser != null) {
      return defaultChooser.selectStorages(storages, component, operation)
    }

    var actualStorageCount = 0
    for (storage in storages) {
      if (!storage.deprecated) {
        actualStorageCount++
      }
    }

    if (actualStorageCount > 1) {
      LOG.error("State chooser not specified for: " + component.javaClass)
    }

    if (!storages[0].deprecated) {
      var othersAreDeprecated = true
      for (i in 1..storages.size() - 1) {
        if (!storages[i].deprecated) {
          othersAreDeprecated = false
          break
        }
      }

      if (othersAreDeprecated) {
        return storages
      }
    }

    val sorted = Arrays.copyOf(storages, storages.size())
    Arrays.sort(sorted, object : Comparator<Storage> {
      override fun compare(o1: Storage, o2: Storage): Int {
        val w1 = if (o1.deprecated) 1 else 0
        val w2 = if (o2.deprecated) 1 else 0
        return w1 - w2
      }
    })
    return sorted
  }

  protected open fun optimizeTestLoading(): Boolean = false

  override fun isReloadPossible(componentNames: Set<String>): Boolean {
    for (componentName in componentNames) {
      val component = myComponents.get(componentName)
      if (component != null && (component !is PersistentStateComponent<*> || !StoreUtil.getStateSpec(component).reloadable)) {
        return false
      }
    }

    return true
  }

  override fun getNotReloadableComponents(componentNames: Collection<String>): Collection<String> {
    var notReloadableComponents: MutableSet<String>? = null
    for (componentName in componentNames) {
      val component = myComponents.get(componentName)
      if (component != null && (component !is PersistentStateComponent<*> || !StoreUtil.getStateSpec(component).reloadable)) {
        if (notReloadableComponents == null) {
          notReloadableComponents = LinkedHashSet<String>()
        }
        notReloadableComponents.add(componentName)
      }
    }
    return if (notReloadableComponents == null) emptySet<String>() else notReloadableComponents
  }

  override fun reinitComponents(componentNames: Set<String>, reloadData: Boolean) {
    reinitComponents(componentNames, emptySet<String>(), emptySet<StateStorage>())
  }

  override fun reinitComponent(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    val component = myComponents.get(componentName) as PersistentStateComponent<*>?
    if (component == null) {
      return false
    }
    else {
      val changedStoragesEmpty = changedStorages.isEmpty()
      initPersistentComponent(component, if (changedStoragesEmpty) null else changedStorages, changedStoragesEmpty)
      return true
    }
  }

  protected abstract fun getMessageBus(): MessageBus

  override fun reload(changedStorages: MultiMap<StateStorage, VirtualFile>): Collection<String>? {
    if (changedStorages.isEmpty()) {
      return emptySet()
    }

    val componentNames = SmartHashSet<String>()
    for (storage in changedStorages.keySet()) {
      try {
        // we must update (reload in-memory storage data) even if non-reloadable component will be detected later
        // not saved -> user does own modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeed(changedStorages.get(storage), componentNames)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }

    }

    if (componentNames.isEmpty()) {
      return emptySet()
    }

    val notReloadableComponents = getNotReloadableComponents(componentNames)
    reinitComponents(componentNames, notReloadableComponents, changedStorages.keySet())
    return if (notReloadableComponents.isEmpty()) null else notReloadableComponents
  }

  // used in settings repository plugin
  public fun reinitComponents(componentNames: Set<String>, notReloadableComponents: Collection<String>, changedStorages: Set<StateStorage>) {
    val messageBus = getMessageBus()
    messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted()
    try {
      for (componentName in componentNames) {
        if (!notReloadableComponents.contains(componentName)) {
          reinitComponent(componentName, changedStorages)
        }
      }
    }
    finally {
      messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished()
    }
  }

  companion object {
    private fun findNonDeprecated(storages: Array<Storage>): Storage {
      for (storage in storages) {
        if (!storage.deprecated) {
          return storage
        }
      }
      throw AssertionError("All storages are deprecated")
    }

    protected fun executeSave(session: SaveSession, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, previousErrors: MutableList<Throwable>?): MutableList<Throwable>? {
      var errors = previousErrors
      try {
        session.save()
      }
      catch (e: ReadOnlyModificationException) {
        LOG.warn(e)
        readonlyFiles.add(util.Pair.create<SaveSession, VirtualFile>(if (e.getSession() == null) session else e.getSession(), e.getFile()))
      }
      catch (e: Exception) {
        if (errors == null) {
          errors = SmartList<Throwable>()
        }
        errors.add(e)
      }

      return errors
    }
  }
}
