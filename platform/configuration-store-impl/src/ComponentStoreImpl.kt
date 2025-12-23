// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(SettingsInternalApi::class)
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.installTemporaryThreadContext
import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents
import com.intellij.diagnostic.PluginException
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.ui
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorageChooserEx.Resolution
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.shared.ConfigFolderChangedListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.ArrayUtilRt
import com.intellij.util.ResourceUtil
import com.intellij.util.ThreeState
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@JvmField
internal val LOG: Logger = logger<ComponentStoreImpl>()

private val SAVE_MOD_LOG = Logger.getInstance("#configurationStore.save.skip")

private val isUseLoadedStateAsExistingVmProperty = System.getProperty("use.loaded.state.as.existing", "true").toBoolean()

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

internal class ComponentStoreImplReloadListener : ConfigFolderChangedListener {
  override suspend fun onChange(changedFileSpecs: Set<String>, deletedFileSpecs: Set<String>, componentStore: IComponentStore) {
    (componentStore as ComponentStoreImpl).reloadComponents(changedFileSpecs, deletedFileSpecs)
  }
}

@Internal
abstract class ComponentStoreImpl : IComponentStore {
  private val components = ConcurrentHashMap<String, ComponentInfo>()

  open val project: Project?
    get() = null

  open val loadPolicy: StateLoadPolicy
    get() = StateLoadPolicy.LOAD

  protected open val allowSavingWithoutModifications: Boolean
    get() = false

  abstract override val storageManager: StateStorageManager

  private val featureUsageSettingManager by lazy {
    val project = project
    @Suppress("IfThenToElvis")
    if (project == null) service<FeatureUsageSettingsEvents>() else project.service<FeatureUsageSettingsEvents>()
  }

  internal fun getComponents(): Map<String, ComponentInfo> = components

  @Internal
  fun incrementModificationCount(componentName: String) {
    components.get(componentName)?.let { info ->
      info.updateModificationCount(info.lastModificationCount + 1)
    }
  }

  fun getComponentNames(): Set<String> = HashSet(components.keys)

  override fun clearCaches() {
    for (info in components.values) {
      info.updateModificationCount(-1)
    }
    storageManager.clearStorages()
  }

  override suspend fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId, parentScope: CoroutineScope?) {
    val nonCancelableInvocator: suspend (action: () -> Unit) -> Unit = if (parentScope == null) {
      { it() }
    }
    else {
      // If a service is requested during highlighting (under impatient=true),
      // then it's initialization might be broken forever.
      // Impatient reader is a property of thread (at the moment, before IJPL-53 is completed),
      // so it leaks to initializeComponent call, where it might cause ReadMostlyRWLock.throwIfImpatient() to throw,
      // for example, if a service obtains a read action in loadState.
      // Non-cancellable section is required to silence throwIfImpatient().
      // In general, we want initialization to be cancellable, and it must be canceled only on parent scope cancellation,
      // which happens only on project/application shutdown, or on plugin unload.
      // See LazyInstanceHolder.initialize
      val existingCoroutineContext = currentCoroutineContext()
      val curatedContext = parentScope.coroutineContext.fold(existingCoroutineContext) { newCtx, key -> newCtx.minusKey(key.key) };
      { action ->
        installTemporaryThreadContext(curatedContext).use {
          action()
        }
      }
    }

    var componentName: String? = null
    try {
      @Suppress("DEPRECATION")
      if (component is PersistentStateComponent<*>) {
        val stateSpec = getStateSpec(component.javaClass)
        if (stateSpec == null) {
          if (loadPolicy == StateLoadPolicy.LOAD) {
            val componentInfo = createComponentInfo(component = component, stateSpec = null, serviceDescriptor = serviceDescriptor, pluginId = pluginId)
            val configurationSchemaKey = componentInfo.configurationSchemaKey
                                         ?: throw UnsupportedOperationException("configurationSchemaKey must be specified for ${component.javaClass.name}")
            @Suppress("UNCHECKED_CAST")
            initComponentWithoutStateSpec(component as PersistentStateComponent<Any>, configurationSchemaKey, componentInfo.pluginId) {
              nonCancelableInvocator(it)
              component.initializeComponent()
            }
          }
          else {
            nonCancelableInvocator {
              component.noStateLoaded()
              component.initializeComponent()
            }
          }
        }
        else {
          componentName = stateSpec.name
          val componentInfo = createComponentInfo(component, stateSpec, serviceDescriptor, pluginId)
          // still must be added to a component list to support explicit save later
          if (stateSpec.allowLoadInTests || loadPolicy == StateLoadPolicy.LOAD || (loadPolicy == StateLoadPolicy.LOAD_ONLY_DEFAULT && stateSpec.defaultStateAsResource)) {
            @Suppress("UNCHECKED_CAST")
            doInitComponent(info = componentInfo, component = component as PersistentStateComponent<Any>, changedStorages = null, reloadData = ThreeState.NO) {
              nonCancelableInvocator(it)
              component.initializeComponent()
            }

            // if not service, so, component manager will check it later for all components
            if (serviceDescriptor != null) {
              val project = project
              if (project != null && project.isInitialized) {
                val app = ApplicationManager.getApplication()
                if (!app.isHeadlessEnvironment && !app.isUnitTestMode) {
                  notifyUnknownMacros(store = this, project, componentName = componentName)
                }
              }
            }
          }
          else {
            nonCancelableInvocator {
              component.noStateLoaded()
              component.initializeComponent()
            }
          }
          registerComponent(componentName, componentInfo)
        }
      }
      else if (loadPolicy == StateLoadPolicy.LOAD && component is JDOMExternalizable) {
        componentName = initJdom(component, pluginId)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      if (e is ControlFlowException) {
        throw e
      }
      error(PluginException("Cannot init component state (componentName=$componentName, componentClass=${component.javaClass.simpleName})", e, pluginId))
    }
  }

  protected open fun error(error: PluginException) {
    LOG.error(error)
  }

  private fun initJdom(@Suppress("DEPRECATION") component: JDOMExternalizable, pluginId: PluginId): String {
    if (component.javaClass.name !in ignoredDeprecatedJDomExternalizableComponents) {
      LOG.error(PluginException("""
          |Component ${component.javaClass.name} implements deprecated JDOMExternalizable interface to serialize its state.
          |IntelliJ Platform will stop supporting such components in the future, so it must be migrated to use PersistentStateComponent.
          |See https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html for details.
          """.trimMargin(), pluginId))
    }

    val componentName = getComponentName(component)
    val componentInfo = createComponentInfo(component = component, stateSpec = null, serviceDescriptor = null, pluginId = pluginId)
    val element = storageManager.getOldStorage(component = component, componentName = componentName, operation = StateStorageOperation.READ)
      ?.getState(component = component, componentName = componentName, pluginId = pluginId, stateClass = Element::class.java, mergeInto = null, reload = false)
    if (element != null) {
      component.readExternal(element)
    }
    registerComponent(componentName, componentInfo)
    return componentName
  }

  @Suppress("DEPRECATION")
  private fun getComponentName(component: Any): String = if (component is NamedComponent) component.componentName else component.javaClass.name

  override fun unloadComponent(component: Any) {
    @Suppress("DEPRECATION")
    val name = when (component) {
      is PersistentStateComponent<*> -> getStateSpec(component.javaClass)?.name ?: return
      is JDOMExternalizable -> getComponentName(component)
      else -> return
    }
    removeComponent(name)
  }

  final override fun initPersistencePlainComponent(component: Any, key: String, pluginId: PluginId) {
    val stateSpec = StateAnnotation(key, FileStorageAnnotation(StoragePathMacros.WORKSPACE_FILE, false))
    val componentInfo = createComponentInfo(component = PersistenceStateAdapter(component), stateSpec = stateSpec, serviceDescriptor = null, pluginId = pluginId)
    registerComponent(stateSpec.name, componentInfo)
  }

  final override suspend fun save(forceSavingAllSettings: Boolean) {
    val saveResult = SaveResult()
    doSave(saveResult = saveResult, forceSavingAllSettings = forceSavingAllSettings)
    saveResult.rethrow()
  }

  internal open suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    val saveSessionManager = createSaveSessionProducerManager()
    commitComponents(isForce = forceSavingAllSettings, sessionManager = saveSessionManager, saveResult = saveResult)
    saveSessionManager.save(saveResult, collectVfsEventsDuringSave)
  }

  protected open val collectVfsEventsDuringSave: Boolean
    get() = false

  private fun getClientAwareComponentInfo(name: String): ComponentInfo? {
    val info = components.get(name) ?: return null
    if (info.stateSpec?.perClient != true || ClientId.isCurrentlyUnderLocalId) {
      return info
    }

    val componentManager = storageManager.componentManager ?: application
    val componentClass = info.component.javaClass
    val clientComponent = componentManager.getService(componentClass)
    if (clientComponent == null || clientComponent === info.component) {
      return info
    }

    return ComponentInfoImpl(info.pluginId, clientComponent, info.stateSpec)
  }

  internal open suspend fun commitComponents(isForce: Boolean, sessionManager: SaveSessionProducerManager, saveResult: SaveResult) {
    val names = ArrayUtilRt.toStringArray(components.keys)
    if (names.isEmpty()) {
      return
    }

    names.sort()

    var timeLog: StringBuilder? = null
    val isUseModificationCount = Registry.`is`("store.save.use.modificationCount", true)

    val isSaveModLogEnabled = SAVE_MOD_LOG.isDebugEnabled && !ApplicationManager.getApplication().isUnitTestMode
    val isExternalSystemStorageEnabled = storageManager.isExternalSystemStorageEnabled

    // well, strictly speaking, each component saving takes some time, but +/- several seconds don't matter
    val nowInSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
    for (name in names) {
      val start = System.currentTimeMillis()
      try {
        val info = getClientAwareComponentInfo(name) ?: continue
        var currentModificationCount = -1L

        if (info.lastSaved != -1) {
          if (isForce || (nowInSeconds - info.lastSaved) > NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD) {
            info.lastSaved = nowInSeconds
          }
          else {
            if (isSaveModLogEnabled) {
              SAVE_MOD_LOG.debug(
                "Skip $name: was already saved in last " +
                "${TimeUnit.SECONDS.toMinutes(NOT_ROAMABLE_COMPONENT_SAVE_THRESHOLD_DEFAULT.toLong())} minutes " +
                "(lastSaved ${info.lastSaved}, now: $nowInSeconds)"
              )
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
            if (isUseModificationCount && !(isForce && allowSavingWithoutModifications)) {
              continue
            }
          }
          else {
            modificationCountChanged = true
          }
        }
        commitComponent(
          sessionManager = sessionManager,
          info = info,
          componentName = name,
          modificationCountChanged = modificationCountChanged,
          isExternalSystemStorageEnabled = isExternalSystemStorageEnabled,
        )
        info.updateModificationCount(currentModificationCount)
      }
      catch (e: Throwable) {
        saveResult.addError(Exception("Cannot get $name component state", e))
      }

      val duration = System.currentTimeMillis() - start
      if (duration > 10) {
        if (timeLog == null) {
          timeLog = StringBuilder("Saving ").append(toString())
        }
        else {
          timeLog.append(", ")
        }
        timeLog!!.append(name).append(" took ").append(duration).append(" ms")
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
    val storages = getStorageSpecs(component, stateSpec, StateStorageOperation.WRITE)
    val storage = storages.firstOrNull { !it.deprecated } ?: throw AssertionError("All storages are deprecated")
    val absolutePath = storageManager.expandMacro(storage.path).toString()
    val componentInfo = components.get(stateSpec.name)
    Disposer.newDisposable().use {
      VfsRootAccess.allowRootAccess(it, absolutePath)
      @Suppress("DEPRECATION")
      runUnderModalProgressIfIsEdt {
        val pluginId = PluginManager.getPluginByClass(component::class.java)?.pluginId ?: PluginManagerCore.CORE_ID
        val componentInfo = componentInfo ?: ComponentInfoImpl(pluginId = pluginId, component = component, stateSpec = stateSpec)
        commitComponent(
          sessionManager = saveManager,
          info = componentInfo,
          componentName = null,
          modificationCountChanged = false,
          isExternalSystemStorageEnabled = storageManager.isExternalSystemStorageEnabled,
        )
        val saveResult = SaveResult()
        saveManager.save(saveResult, collectVfsEventsDuringSave)
        saveResult.rethrow()
      }
    }
  }

  internal open fun createSaveSessionProducerManager(): SaveSessionProducerManager = SaveSessionProducerManager()

  private suspend fun commitComponent(
    sessionManager: SaveSessionProducerManager,
    info: ComponentInfo,
    componentName: String?,
    modificationCountChanged: Boolean,
    isExternalSystemStorageEnabled: Boolean,
  ) {
    val component = info.component
    @Suppress("DEPRECATION")
    if (component is JDOMExternalizable) {
      val effectiveComponentName = componentName ?: getComponentName(component)
      storageManager.getOldStorage(component = component, componentName = effectiveComponentName, operation = StateStorageOperation.WRITE)?.let {
        sessionManager.getProducer(it)?.setState(component = component, componentName = effectiveComponentName, pluginId = info.pluginId, state = component)
      }
      return
    }

    var state: Any? = null
    // the state might be null, so we need an additional flag to keep track of whether the state was requested
    var stateRequested = false

    val stateSpec = info.stateSpec!!
    val effectiveComponentName = componentName ?: stateSpec.name
    val stateStorageChooser = component as? StateStorageChooserEx

    @Suppress("UNCHECKED_CAST")
    val storageSpecs = getStorageSpecs(component as PersistentStateComponent<Any>, stateSpec, StateStorageOperation.WRITE)
    for (storageSpec in storageSpecs) {
      var resolution = stateStorageChooser?.getResolution(storageSpec, StateStorageOperation.WRITE) ?: Resolution.DO
      if (resolution == Resolution.SKIP) {
        continue
      }

      val storage = storageManager.getStateStorage(storageSpec)

      if (resolution == Resolution.DO) {
        resolution = storage.getResolution(component, StateStorageOperation.WRITE, isExternalSystemStorageEnabled)
        if (resolution == Resolution.SKIP) {
          continue
        }
      }

      val sessionProducer = sessionManager.getProducer(storage) ?: continue
      if (resolution == Resolution.CLEAR ||
          (storageSpec.deprecated && storageSpecs.none { !it.deprecated && it.value == storageSpec.value })) {
        sessionProducer.setState(component = component, componentName = effectiveComponentName, pluginId = info.pluginId, state = null)
      }
      else {
        if (!stateRequested) {
          stateRequested = true
          state = getStateForComponent(component, stateSpec)
        }

        if (modificationCountChanged && state != null && isReportStatisticAllowed(stateSpec, storageSpec)) {
          featureUsageSettingManager.logConfigurationChanged(effectiveComponentName, state)
        }
        setStateToSaveSessionProducer(state, info, effectiveComponentName, sessionProducer)
      }
    }
  }

  // the method is not called if storage is deprecated or clear was requested (the state in these cases is `null`),
  // but is called if the state is `null` if returned so from a component
  protected open fun setStateToSaveSessionProducer(
    state: Any?,
    info: ComponentInfo,
    effectiveComponentName: String,
    sessionProducer: SaveSessionProducer,
  ) {
    sessionProducer.setState(info.component, effectiveComponentName, info.pluginId, state)
  }

  private fun registerComponent(name: String, info: ComponentInfo): ComponentInfo? {
    if (info.stateSpec?.perClient == true && !ClientId.isCurrentlyUnderLocalId) {
      // Register per-client components only for `ClientId.localId`
      return null
    }

    val existing = components.putIfAbsent(name, info)
    if (existing != null && existing.component !== info.component) {
      LOG.error(
        "Conflicting component name '$name': ${existing.component.javaClass} and " +
        "${info.component.javaClass} (componentManager=${storageManager.componentManager})"
      )
      return existing
    }
    else {
      return info
    }
  }

  private suspend fun initComponentImpl(info: ComponentInfo, changedStorages: Set<StateStorage>?, reloadData: ThreeState) {
    @Suppress("UNCHECKED_CAST")
    val component = info.component as PersistentStateComponent<Any>
    if (info.stateSpec == null) {
      val configurationSchemaKey = info.configurationSchemaKey ?: throw UnsupportedOperationException("configurationSchemaKey must be specified for ${component.javaClass.name}")
      initComponentWithoutStateSpec(component, configurationSchemaKey, info.pluginId) {
        it()
      }
    }
    else {
      doInitComponent(info, component, changedStorages, reloadData) {
        it()
      }
    }
  }

  protected inline fun initComponentWithoutStateSpec(
    component: PersistentStateComponent<Any>,
    configurationSchemaKey: String,
    pluginId: PluginId,
    nonCancelableInvocator: (action: () -> Unit) -> Unit,
  ) {
    val stateClass = ComponentSerializationUtil.getStateClass<Any>(component.javaClass)
    val storage = getReadOnlyStorage(component.javaClass, stateClass, configurationSchemaKey)
    val state = storage?.getState(component = component, componentName = "", pluginId = pluginId, stateClass = stateClass, mergeInto = null, reload = false)
    nonCancelableInvocator {
      if (state == null) {
        component.noStateLoaded()
      }
      else {
        component.loadState(state)
      }
    }
  }

  protected open fun getReadOnlyStorage(componentClass: Class<Any>, stateClass: Class<Any>, configurationSchemaKey: String): StateStorage? = null

  private suspend inline fun doInitComponent(
    info: ComponentInfo,
    component: PersistentStateComponent<Any>,
    changedStorages: Set<StateStorage>?,
    reloadData: ThreeState,
    stateConsumer: (action: () -> Unit) -> Unit,
  ) {
    @Suppress("UNCHECKED_CAST")
    val stateClass: Class<Any> = when (component) {
      is PersistenceStateAdapter -> component.component::class.java as Class<Any>
      else -> ComponentSerializationUtil.getStateClass(component.javaClass)
    }

    val stateSpec = info.stateSpec!!
    val name = stateSpec.name

    // KT-39968: `PathMacrosImpl` could increase `modCount` on `loadState`, and the change has to be persisted;
    // all other components follow a general rule: initial `modCount` is calculated after the ` loadState ` phase
    val postLoadStateUpdateModificationCount = name != "PathMacrosImpl"

    val defaultState = if (stateSpec.defaultStateAsResource) getDefaultState(component, name, stateClass) else null
    if (loadPolicy == StateLoadPolicy.LOAD || info.stateSpec?.allowLoadInTests == true) {
      val storageChooser = component as? StateStorageChooserEx
      for (storageSpec in getStorageSpecs(component = component, stateSpec = stateSpec, operation = StateStorageOperation.READ)) {
        if (storageChooser?.getResolution(storageSpec, StateStorageOperation.READ) == Resolution.SKIP) {
          continue
        }

        val storage = storageManager.getStateStorage(storageSpec)

        // if storage marked as changed, it means that analyzeExternalChangesAndUpdateIfNeeded was called for it and storage is already reloaded
        val isReloadDataForStorage = if (reloadData == ThreeState.UNSURE) {
          changedStorages == null || isStorageChanged(changedStorages, storage)
        }
        else {
          reloadData.toBoolean()
        }

        val stateGetter = doCreateStateGetter(
          reloadData = isReloadDataForStorage,
          storage = storage,
          info = info,
          componentName = name,
          stateClass = stateClass,
          useLoadedStateAsExisting = stateSpec.useLoadedStateAsExisting,
        )
        var state = stateGetter.getState(defaultState)
        if (state == null) {
          if (changedStorages != null && isStorageChanged(changedStorages, storage)) {
            // the state will be `null` if a file is deleted;
            // we must create an empty (initial) state to reinit the component
            state = deserializeState(Element("state"), stateClass)!!
          }
          else {
            if (isReportStatisticAllowed(stateSpec, storageSpec)) {
              featureUsageSettingManager.logDefaultConfigurationState(name, stateClass)
            }
            continue
          }
        }

        if (!postLoadStateUpdateModificationCount) {
          info.updateModificationCount(info.currentModificationCount)
        }

        stateConsumer {
          component.loadState(state)
        }

        val stateAfterLoad = stateGetter.archiveState()
        if (isReportStatisticAllowed(stateSpec, storageSpec)) {
          featureUsageSettingManager.logConfigurationState(name, stateAfterLoad ?: state)
        }

        if (postLoadStateUpdateModificationCount) {
          info.updateModificationCount(info.currentModificationCount)
        }
        return
      }
    }

    // we load the default state even if isLoadComponentState false - required for app components
    // (for example, at least one color scheme must exist)
    if (defaultState == null) {
      stateConsumer {
        component.noStateLoaded()
      }
    }
    else {
      if (!postLoadStateUpdateModificationCount) {
        info.updateModificationCount(info.currentModificationCount)
      }

      stateConsumer {
        component.loadState(defaultState)
      }

      if (postLoadStateUpdateModificationCount) {
        info.updateModificationCount(info.currentModificationCount)
      }
    }
  }

  protected open fun isReportStatisticAllowed(stateSpec: State, storageSpec: Storage): Boolean {
    return !storageSpec.deprecated && stateSpec.reportStatistic && storageSpec.value != StoragePathMacros.CACHE_FILE
  }

  protected open fun doCreateStateGetter(
    reloadData: Boolean,
    storage: StateStorage,
    info: ComponentInfo,
    componentName: String,
    stateClass: Class<Any>,
    useLoadedStateAsExisting: Boolean,
  ): StateGetter<Any> {
    @Suppress("UNCHECKED_CAST")
    val component = info.component as PersistentStateComponent<Any>

    // getting state after loading with an active controller can lead to unusual issues - disable write protection
    if (useLoadedStateAsExisting && storage is XmlElementStorage && (storage.controller == null || project != null) && isUseLoadedStateAsExisting(storage)) {
      return storage.createGetSession(component = component, componentName = componentName, pluginId = info.pluginId, stateClass = stateClass, reload = reloadData)
    }

    return object : StateGetter<Any> {
      override suspend fun getState(mergeInto: Any?): Any? {
        return storage.getState(component = component, componentName = componentName, pluginId = info.pluginId, stateClass = stateClass, mergeInto = mergeInto, reload = reloadData)
      }

      override fun archiveState(): Any? = null
    }
  }

  protected open fun isUseLoadedStateAsExisting(storage: StateStorage): Boolean {
    return (storage as? XmlElementStorage)?.roamingType != RoamingType.DISABLED && isUseLoadedStateAsExistingVmProperty
  }

  protected open fun getPathMacroManagerForDefaults(): PathMacroManager? = null

  private fun <T : Any> getDefaultState(component: Any, componentName: String, stateClass: Class<T>): T? {
    val classLoader = component.javaClass.classLoader
    val data = ResourceUtil.getResourceAsBytes("$componentName.xml", classLoader) ?: return null
    try {
      val element = buildNsUnawareJdom(data)
      getPathMacroManagerForDefaults()?.expandPaths(element)
      return deserializeState(stateElement = element, stateClass = stateClass)
    }
    catch (e: Throwable) {
      throw RuntimeException("Error loading default state for $componentName", e)
    }
  }

  protected open fun <T : Any> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
  ): List<Storage> {
    return getStorageSpecGenericImpl(stateSpec, component)
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

  override suspend fun reloadStates(componentNames: Set<String>) {
    reinitComponents(componentNames = componentNames, changedStorages = emptySet(), notReloadableComponents = emptySet())
  }

  internal suspend fun batchReloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    val publisher = messageBus.syncPublisher(BatchUpdateListener.TOPIC)
    publisher.onBatchUpdateStarted()
    try {
      reinitComponents(componentNames = componentNames, changedStorages = emptySet(), notReloadableComponents = emptySet())
    }
    finally {
      publisher.onBatchUpdateFinished()
    }
  }

  private suspend fun reloadPerClientState(
    componentClass: Class<out PersistentStateComponent<*>>,
    info: ComponentInfo,
    changedStorages: Set<StateStorage>,
  ) {
    if (ClientId.isCurrentlyUnderLocalId) {
      throw AssertionError("This method must be called under remote client id")
    }

    val perClientComponent = (storageManager.componentManager ?: application).getServiceForClient(componentClass) ?: return
    if (perClientComponent === info.component) {
      LOG.error(
        "Failed to reload per-client component '${info.stateSpec?.name ?: componentClass.simpleName}': " +
        "looks like it is not registered as a per-client service (componentManager=${storageManager.componentManager})"
      )
      return
    }

    val newInfo = ComponentInfoImpl(info.pluginId, perClientComponent, info.stateSpec)
    initComponentImpl(info = newInfo, changedStorages = changedStorages.ifEmpty { null }, reloadData = ThreeState.YES)
  }

  final override suspend fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
    val stateSpec = getStateSpecOrError(componentClass)
    val info = components.get(stateSpec.name) ?: return
    (info.component as? PersistentStateComponent<*>)?.let {
      if (stateSpec.perClient && !ClientId.isCurrentlyUnderLocalId) {
        reloadPerClientState(componentClass = it.javaClass, info = info, changedStorages = emptySet())
        return
      }

      initComponentImpl(info = info, changedStorages = emptySet(), reloadData = ThreeState.YES)
    }
  }

  private suspend fun reloadState(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    val info = components.get(componentName) ?: return false
    val component = info.component
    if (component !is PersistentStateComponent<*>) {
      return false
    }

    if (info.stateSpec?.perClient == true && !ClientId.isCurrentlyUnderLocalId) {
      reloadPerClientState(component.javaClass, info, changedStorages)
      return true
    }

    val isChangedStoragesEmpty = changedStorages.isEmpty()
    initComponentImpl(info = info, changedStorages = if (isChangedStoragesEmpty) null else changedStorages, reloadData = ThreeState.UNSURE)
    return true
  }

  /**
   * `null` if reloaded, an empty list when nothing to reload, or a list of not reloadable components (reload is not performed)
   */
  open suspend fun reload(changedStorages: Set<StateStorage>): Collection<String>? {
    if (changedStorages.isEmpty()) {
      @Suppress("GrazieInspection")
      LOG.debug("There is no changed storages to reload")
      return emptySet()
    }

    val componentNames = HashSet<String>()
    for (storage in changedStorages) {
      runCatching {
        // we must update (reload in-memory storage data) even if a non-reloadable component is detected later
        // not saved -> user does a modification -> new (on disk) state will be overwritten and not applied
        storage.analyzeExternalChangesAndUpdateIfNeeded(componentNames)
      }.getOrLogException(LOG)
    }

    if (componentNames.isEmpty()) {
      return emptySet()
    }

    LOG.debug { "Reload components: $componentNames" }
    val notReloadableComponents = getNotReloadableComponents(componentNames)
    reinitComponents(componentNames = componentNames, changedStorages = changedStorages, notReloadableComponents = notReloadableComponents)
    return notReloadableComponents.ifEmpty { null }
  }

  // used in settings repository plugin
  open suspend fun reinitComponents(componentNames: Set<String>, changedStorages: Set<StateStorage>, notReloadableComponents: Collection<String>) {
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
    storageManager.release()
  }

  override fun toString(): String = storageManager.componentManager.toString()
}

@Internal
enum class StateLoadPolicy {
  LOAD, LOAD_ONLY_DEFAULT, NOT_LOAD
}

@Internal
interface ExternalStorageWithInternalPart {
  val internalStorage: StateStorage
}

/**
 * Provides a way to temporarily ignore a known component extending the deprecated JDOMExternalizable interface to avoid having unnecessary
 * errors in the log. Each entry must be accompanied by a link to the corresponding YouTrack issue.
 */
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val ignoredDeprecatedJDomExternalizableComponents = java.util.Set.of(
  "jetbrains.buildServer.codeInspection.InspectionPassRegistrar", //TW-82189
)

private fun notifyUnknownMacros(store: IComponentStore, project: Project, componentName: String) {
  val substitutor = store.storageManager.macroSubstitutor as? TrackingPathMacroSubstitutor ?: return

  val immutableMacros = substitutor.getUnknownMacros(componentName)
  if (immutableMacros.isEmpty()) {
    return
  }

  val macros = LinkedHashSet(immutableMacros)
  project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.ui(UiDispatcherKind.RELAX)) {
    var notified: MutableList<String>? = null
    val manager = NotificationsManager.getNotificationsManager()
    for (notification in manager.getNotificationsOfType(
      UnknownMacroNotification::class.java, project)) {
      if (notified == null) {
        notified = ArrayList()
      }
      notified.addAll(notification.macros)
    }
    if (!notified.isNullOrEmpty()) {
      @Suppress("ConvertArgumentToSet")
      macros.removeAll(notified)
    }

    if (macros.isEmpty()) {
      return@launch
    }

    LOG.debug("Reporting unknown path macros $macros in component $componentName")
    doNotify(macros = macros, project = project, substitutorToStore = java.util.Map.of(substitutor, store))
  }
}

internal suspend fun getStateForComponent(component: PersistentStateComponent<*>, stateSpec: State): Any? {
  return when {
    component is SerializablePersistentStateComponent<*> -> component.state
    // maybe read action
    stateSpec.getStateRequiresEdt -> withContext(Dispatchers.EDT) { component.state }
    else -> readAction { component.state }
  }
}

private fun isStorageChanged(changedStorages: Set<StateStorage>, storage: StateStorage): Boolean {
  return changedStorages.contains(storage) || (storage is ExternalStorageWithInternalPart && changedStorages.contains(storage.internalStorage))
}

internal fun <T : Any> getStorageSpecGenericImpl(
  stateSpec: State,
  component: PersistentStateComponent<T>,
): List<Storage> {
  val storages = getWithPerOsStorages(stateSpec.storages)
  if (storages.size == 1 || component is StateStorageChooserEx) {
    return storages
  }

  if (storages.isEmpty()) {
    if (stateSpec.defaultStateAsResource) {
      return emptyList()
    }
    throw AssertionError("No storage specified for $component")
  }

  return sortStoragesByDeprecated(storages)
}

private fun getWithPerOsStorages(storages: Array<Storage>): List<Storage> {
  val result = mutableListOf<Storage>()
  for (storage in storages) {
    if (storage.roamingType == RoamingType.PER_OS) {
      result.add(StorageImpl.copyWithNewValue(storage, getOsDependentStorage(storage.value)))
      result.add(StorageImpl.deprecatedCopy(storage))
    }
    else {
      result.add(storage)
    }
  }
  return result
}