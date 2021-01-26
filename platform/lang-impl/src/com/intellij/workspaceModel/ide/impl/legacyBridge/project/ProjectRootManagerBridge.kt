// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar.APPLICATION_LEVEL
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.jps.serialization.levelToLibraryTableId
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.OrderRootsCacheBridge
import com.intellij.workspaceModel.storage.bridgeEntities.*

@Suppress("ComponentNotRegistered")
class ProjectRootManagerBridge(project: Project) : ProjectRootManagerComponent(project) {
  companion object {
    private const val LIBRARY_NAME_DELIMITER = ":"
  }

  private val LOG = Logger.getInstance(javaClass)
  private val globalLibraryTableListener = GlobalLibraryTableListener()
  private val jdkChangeListener = JdkChangeListener()

  init {
    val bus = project.messageBus.connect(this)

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(bus, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        if (myProject.isDisposed) return

        // Roots changed event should be fired for the global libraries linked with module
        val moduleChanges = event.getChanges(ModuleEntity::class.java)
        for (change in moduleChanges) {
          when (change) {
            is EntityChange.Added -> addTrackedLibraryAndJdkFromEntity(change.entity)
            is EntityChange.Removed -> removeTrackedLibrariesAndJdkFromEntity(change.entity)
            is EntityChange.Replaced -> {
              removeTrackedLibrariesAndJdkFromEntity(change.oldEntity)
              addTrackedLibraryAndJdkFromEntity(change.newEntity)
            }
          }
        }
      }
    })

    bus.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkChangeListener)
  }

  override fun getActionToRunWhenProjectJdkChanges(): Runnable {
    return Runnable {
      super.getActionToRunWhenProjectJdkChanges().run()
      if (jdkChangeListener.hasProjectSdkDependency()) fireRootsChanged()
    }
  }

  override fun projectClosed() {
    super.projectClosed()
    unsubscribeListeners()
  }

  override fun dispose() {
    super.dispose()
    unsubscribeListeners()
  }

  override fun getOrderRootsCache(project: Project): OrderRootsCache {
    return OrderRootsCacheBridge(project, project)
  }

  fun isFiringEvent(): Boolean = isFiringEvent

  private fun unsubscribeListeners() {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val globalLibraryTable = libraryTablesRegistrar.libraryTable
    globalLibraryTableListener.getLibraryLevels().forEach { libraryLevel ->
      val libraryTable = when (libraryLevel) {
        APPLICATION_LEVEL -> globalLibraryTable
        else -> libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)
      }
      libraryTable?.libraryIterator?.forEach { (it as? RootProvider)?.removeRootSetChangedListener(globalLibraryTableListener) }
      libraryTable?.removeListener(globalLibraryTableListener)
    }
    globalLibraryTableListener.clear()
    jdkChangeListener.unsubscribeListeners()
  }

  fun setupTrackedLibrariesAndJdks() {
    val currentStorage = WorkspaceModel.getInstance(project).entityStorage.current
    for (moduleEntity in currentStorage.entities(ModuleEntity::class.java)) {
      addTrackedLibraryAndJdkFromEntity(moduleEntity);
    }
  }

  private fun addTrackedLibraryAndJdkFromEntity(moduleEntity: ModuleEntity) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.debug { "Add tracked global libraries and JDK from ${moduleEntity.name}" }
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.forEach {
      when {
        it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId is LibraryTableId.GlobalLibraryTableId -> {
          val libraryName = it.library.name
          val libraryLevel = it.library.tableId.level
          val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
          if (globalLibraryTableListener.isEmpty(libraryLevel)) libraryTable.addListener(globalLibraryTableListener)
          globalLibraryTableListener.addTrackedLibrary(moduleEntity, libraryTable, libraryName)
        }
        it is ModuleDependencyItem.SdkDependency || it is ModuleDependencyItem.InheritedSdkDependency -> {
          jdkChangeListener.addTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  private fun removeTrackedLibrariesAndJdkFromEntity(moduleEntity: ModuleEntity) {
    LOG.debug { "Removed tracked global libraries and JDK from ${moduleEntity.name}" }
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    moduleEntity.dependencies.forEach {
      when {
        it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId is LibraryTableId.GlobalLibraryTableId -> {
          val libraryName = it.library.name
          val libraryLevel = it.library.tableId.level
          val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEach
          globalLibraryTableListener.unTrackLibrary(moduleEntity, libraryTable, libraryName)
          if (globalLibraryTableListener.isEmpty(libraryLevel)) libraryTable.removeListener(globalLibraryTableListener)
        }
        it is ModuleDependencyItem.SdkDependency || it is ModuleDependencyItem.InheritedSdkDependency -> {
          jdkChangeListener.removeTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  private fun fireRootsChanged() {
    if (myProject.isOpen) {
      makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }
  }

  // Listener for global libraries linked to module
  private inner class GlobalLibraryTableListener : LibraryTable.Listener, RootSetChangedListener {
    private val librariesPerModuleMap = BidirectionalMultiMap<ModuleId, String>()

    private var insideRootsChange = false

    fun addTrackedLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier)) {
        (library as? RootProvider)?.addRootSetChangedListener(this)
      }
      librariesPerModuleMap.put(moduleEntity.persistentId(), libraryIdentifier)
    }

    fun unTrackLibrary(moduleEntity: ModuleEntity, libraryTable: LibraryTable, libraryName: String) {
      val library = libraryTable.getLibraryByName(libraryName)
      val libraryIdentifier = getLibraryIdentifier(libraryTable, libraryName)
      librariesPerModuleMap.remove(moduleEntity.persistentId(), libraryIdentifier)
      if (!librariesPerModuleMap.containsValue(libraryIdentifier)) {
        (library as? RootProvider)?.removeRootSetChangedListener(this)
      }
    }

    fun isEmpty(libraryLevel: String) = librariesPerModuleMap.values.none{ it.startsWith("$libraryLevel$LIBRARY_NAME_DELIMITER") }

    fun getLibraryLevels() = librariesPerModuleMap.values.mapTo(HashSet()) { it.substringBefore(LIBRARY_NAME_DELIMITER) }

    override fun afterLibraryAdded(newLibrary: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(newLibrary))) fireRootsChanged()
    }

    override fun afterLibraryRemoved(library: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(library))) fireRootsChanged()
    }

    override fun afterLibraryRenamed(library: Library, oldName: String?) {
      val libraryTable = library.table
      val newName = library.name
      if (libraryTable != null && oldName != null && newName != null) {
        val affectedModules = librariesPerModuleMap.getKeys(getLibraryIdentifier(libraryTable, oldName))
        if (affectedModules.isNotEmpty()) {
          val libraryTableId = levelToLibraryTableId(libraryTable.tableLevel)
          WorkspaceModel.getInstance(myProject).updateProjectModel { builder ->
            //maybe it makes sense to simplify this code by reusing code from PEntityStorageBuilder.updateSoftReferences
            affectedModules.mapNotNull { builder.resolve(it) }.forEach { module ->
              val updated = module.dependencies.map {
                when {
                  it is ModuleDependencyItem.Exportable.LibraryDependency && it.library.tableId == libraryTableId && it.library.name == oldName ->
                    it.copy(library = LibraryId(newName, libraryTableId))
                  else -> it
                }
              }
              builder.modifyEntity(ModifiableModuleEntity::class.java, module) {
                dependencies = updated
              }
            }
          }
        }
      }
    }

    override fun rootSetChanged(wrapper: RootProvider) {
      if (insideRootsChange) return
      insideRootsChange = true
      try {
        fireRootsChanged()
      }
      finally {
        insideRootsChange = false
      }
    }

    private fun getLibraryIdentifier(library: Library) = "${library.table.tableLevel}$LIBRARY_NAME_DELIMITER${library.name}"
    private fun getLibraryIdentifier(libraryTable: LibraryTable,
                                     libraryName: String) = "${libraryTable.tableLevel}$LIBRARY_NAME_DELIMITER$libraryName"

    fun clear() = librariesPerModuleMap.clear()
  }

  private inner class JdkChangeListener : ProjectJdkTable.Listener, RootSetChangedListener {
    private val sdkDependencies = MultiMap.createSet<ModuleDependencyItem, ModuleId>()
    private val watchedSdks = HashSet<RootProvider>()

    override fun jdkAdded(jdk: Sdk) {
      if (hasDependencies(jdk)) {
        if (watchedSdks.add(jdk.rootProvider)) {
          jdk.rootProvider.addRootSetChangedListener(this)
        }
        fireRootsChanged()
      }
    }

    override fun jdkNameChanged(jdk: Sdk, previousName: String) {
      val sdkDependency = ModuleDependencyItem.SdkDependency(previousName, jdk.sdkType.name)
      val affectedModules = sdkDependencies.get(sdkDependency)
      if (affectedModules.isNotEmpty()) {
        WorkspaceModel.getInstance(myProject).updateProjectModel { builder ->
          for (moduleId in affectedModules) {
            val module = moduleId.resolve(builder) ?: continue
            val updated = module.dependencies.map {
              when (it) {
                is ModuleDependencyItem.SdkDependency -> ModuleDependencyItem.SdkDependency(jdk.name, jdk.sdkType.name)
                else -> it
              }
            }
            builder.modifyEntity(ModifiableModuleEntity::class.java, module) {
              dependencies = updated
            }
          }
        }
      }
    }

    override fun jdkRemoved(jdk: Sdk) {
      if (watchedSdks.remove(jdk.rootProvider)) {
        jdk.rootProvider.removeRootSetChangedListener(this)
      }
      if (hasDependencies(jdk)) {
        fireRootsChanged()
      }
    }

    override fun rootSetChanged(wrapper: RootProvider) {
      fireRootsChanged()
    }

    fun addTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      val sdk = findSdk(sdkDependency)
      if (sdk != null && watchedSdks.add(sdk.rootProvider)) {
        sdk.rootProvider.addRootSetChangedListener(this)
      }
      sdkDependencies.putValue(sdkDependency, moduleEntity.persistentId())
    }

    fun removeTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      sdkDependencies.remove(sdkDependency, moduleEntity.persistentId())
      val sdk = findSdk(sdkDependency)
      if (sdk != null && !hasDependencies(sdk) && watchedSdks.remove(sdk.rootProvider)) {
        sdk.rootProvider.removeRootSetChangedListener(this)
      }
    }

    fun hasProjectSdkDependency(): Boolean {
      return sdkDependencies.get(ModuleDependencyItem.InheritedSdkDependency).isNotEmpty()
    }

    private fun findSdk(sdkDependency: ModuleDependencyItem): Sdk? = when (sdkDependency) {
      is ModuleDependencyItem.InheritedSdkDependency -> projectSdk
      is ModuleDependencyItem.SdkDependency -> ProjectJdkTable.getInstance().findJdk(sdkDependency.sdkName, sdkDependency.sdkType)
      else -> null
    }

    private fun hasDependencies(jdk: Sdk): Boolean {
      return sdkDependencies.get(ModuleDependencyItem.SdkDependency(jdk.name, jdk.sdkType.name)).isNotEmpty()
             || jdk.name == projectSdkName && jdk.sdkType.name == projectSdkTypeName && hasProjectSdkDependency()
    }

    fun unsubscribeListeners() {
      watchedSdks.forEach {
        it.removeRootSetChangedListener(this)
      }
      watchedSdks.clear()
    }
  }
}