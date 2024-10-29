// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("OVERRIDE_DEPRECATION", "ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.module.impl

import com.intellij.configurationStore.RenameableStateStorageManager
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.emptyConstructorMethodType
import com.intellij.serviceContainer.findConstructorOrNull
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path

private val moduleMethodType = MethodType.methodType(Void.TYPE, Module::class.java)

private val LOG: Logger
  get() = logger<ModuleImpl>()

open class ModuleImpl @ApiStatus.Internal constructor(
  name: String,
  project: Project,
) : ComponentManagerImpl(parent = project as ComponentManagerImpl), ModuleEx, Queryable {
  private val project: Project
  protected var imlFilePointer: VirtualFilePointer? = null

  @Volatile
  private var isModuleAdded = false
  private var name: String? = null
  private val moduleScopeProvider: ModuleScopeProvider

  @ApiStatus.Internal
  constructor(name: String, project: Project, virtualFilePointer: VirtualFilePointer?) : this(name, project) {
    imlFilePointer = virtualFilePointer
  }

  init {
    @Suppress("LeakingThis")
    registerServiceInstance(serviceInterface = Module::class.java, instance = this, pluginDescriptor = fakeCorePluginDescriptor)
    this.project = project
    @Suppress("LeakingThis")
    moduleScopeProvider = project.service<ModuleScopeProviderFactory>().createProvider(this)
    this.name = name
  }

  final override fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return (lookup.findConstructorOrNull(aClass, moduleMethodType)?.invoke(this)
            ?: lookup.findConstructorOrNull(aClass, emptyConstructorMethodType)?.invoke()
            ?: RuntimeException("Cannot find suitable constructor, expected (Module) or ()")) as T
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = java.util.List.of(
    moduleMethodType,
    emptyConstructorMethodType,
  )

  override fun init() {
    // do not measure (activityNamePrefix method not overridden by this class)
    // because there are a lot of modules and no need to measure each one
    registerComponents()
    if (!isPersistent) {
      registerService(
        serviceInterface = IComponentStore::class.java,
        implementation = NonPersistentModuleStore::class.java,
        pluginDescriptor = fakeCorePluginDescriptor,
        override = true,
      )
    }
    @Suppress("DEPRECATION")
    createComponents()
  }

  private val isPersistent: Boolean
    get() = imlFilePointer != null

  override fun isDisposed(): Boolean {
    // in case of light project in tests when it's temporarily disposed, the module should be treated as disposed too.
    @Suppress("TestOnlyProblems")
    return super.isDisposed() || (project as ProjectEx).isLight && project.isDisposed()
  }

  @Internal
  override fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    if (!super.isComponentSuitable(componentConfig)) {
      return false
    }

    val options = componentConfig.options
    if (options.isNullOrEmpty()) {
      return true
    }

    for (optionName in options.keys) {
      if ("workspace" == optionName || "overrides" == optionName) {
        continue
      }

      // we cannot filter using module options because at this moment module file data could be not loaded
      val message = "Don't specify $optionName in the component registration," +
                    " transform component to service and implement your logic in your getInstance() method"
      if (ApplicationManager.getApplication().isUnitTestMode) {
        LOG.error(message)
      }
      else {
        LOG.warn(message)
      }
    }
    return true
  }

  override fun getModuleFile(): VirtualFile? = imlFilePointer?.file

  override fun rename(newName: String, notifyStorage: Boolean) {
    name = newName
    if (notifyStorage) {
      (store.storageManager as RenameableStateStorageManager).rename(newName + ModuleFileType.DOT_DEFAULT_EXTENSION)
    }
  }

  protected val store: IComponentStore
    get() = getService(IComponentStore::class.java)!!

  override fun canStoreSettings(): Boolean = store !is NonPersistentModuleStore

  override fun getModuleNioFile(): Path {
    return if (isPersistent) store.storageManager.expandMacro(StoragePathMacros.MODULE_FILE) else Path.of("")
  }

  @Synchronized
  override fun dispose() {
    isModuleAdded = false
    runCatching {
      serviceIfCreated<IComponentStore>()?.release()
    }.getOrLogException(LOG)
    super.dispose()
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl) = pluginDescriptor.moduleContainerDescriptor

  override fun getProject(): Project = project

  override fun getName(): String = name!!

  override fun isLoaded(): Boolean = isModuleAdded

  override fun markAsLoaded() {
    isModuleAdded = true
  }

  override fun setOption(key: String, value: String?) {
    val manager = optionManager
    if (value == null) {
      if (manager.state.options.remove(key) != null) {
        manager.incModificationCount()
      }
    }
    else if (value != manager.state.options.put(key, value)) {
      manager.incModificationCount()
    }
  }

  private val optionManager: DeprecatedModuleOptionManager
    get() = (this as Module).getService(DeprecatedModuleOptionManager::class.java)

  override fun getOptionValue(key: String): String? = optionManager.state.options.get(key)

  override fun getModuleScope(): GlobalSearchScope = moduleScopeProvider.moduleScope

  override fun getModuleScope(includeTests: Boolean): GlobalSearchScope = moduleScopeProvider.getModuleScope(includeTests)

  override fun getModuleWithLibrariesScope(): GlobalSearchScope = moduleScopeProvider.moduleWithLibrariesScope

  override fun getModuleWithDependenciesScope(): GlobalSearchScope = moduleScopeProvider.moduleWithDependenciesScope

  override fun getModuleContentScope(): GlobalSearchScope = moduleScopeProvider.moduleContentScope

  override fun getModuleContentWithDependenciesScope(): GlobalSearchScope = moduleScopeProvider.moduleContentWithDependenciesScope

  override fun getModuleWithDependenciesAndLibrariesScope(includeTests: Boolean): GlobalSearchScope {
    return moduleScopeProvider.getModuleWithDependenciesAndLibrariesScope(includeTests)
  }

  override fun getModuleWithDependentsScope(): GlobalSearchScope = moduleScopeProvider.moduleWithDependentsScope

  override fun getModuleTestsWithDependentsScope(): GlobalSearchScope = moduleScopeProvider.moduleTestsWithDependentsScope

  override fun getModuleRuntimeScope(includeTests: Boolean): GlobalSearchScope = moduleScopeProvider.getModuleRuntimeScope(includeTests)

  override fun getModuleProductionSourceScope(): GlobalSearchScope = moduleScopeProvider.moduleProductionSourceScope

  override fun getModuleTestSourceScope(): GlobalSearchScope = moduleScopeProvider.moduleTestSourceScope

  override fun clearScopesCache() {
    moduleScopeProvider.clearCache()
  }

  override fun toString(): String {
    return if (name == null) "Module (not initialized)" else "Module: '${getName()}'${if (isDisposed) " (disposed)" else ""}"
  }

  override fun putInfo(info: MutableMap<in String, in String>) {
    info.put("id", "Module")
    info.put("name", getName())
  }

  override fun debugString(short: Boolean): String = if (short) javaClass.simpleName else super.debugString(short = false)
}

@State(name = "DeprecatedModuleOptionManager", useLoadedStateAsExisting = false)
internal class DeprecatedModuleOptionManager(private val module: Module)
  : SimpleModificationTracker(), PersistentStateComponent<DeprecatedModuleOptionManager.State?>, ProjectModelElement {
  override fun getExternalSource(): ProjectModelExternalSource? {
    if (state.options.size > 1 || state.options.size == 1 && !state.options.containsKey(Module.ELEMENT_TYPE)) {
      return null
    }
    else {
      return ExternalProjectSystemRegistry.getInstance().getExternalSource(module)
    }
  }

  class State {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundKeyWithTag = false, surroundValueWithTag = false, surroundWithTag = false, entryTagName = "option")
    val options: MutableMap<String, String?> = HashMap()
  }

  private var state = State()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }
}
