// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.RootModelBase.CollectDependentModules
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.isEmpty
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.jps.serialization.levelToLibraryTableId
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.CompilerModuleExtensionBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jdom.Element
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.concurrent.ConcurrentHashMap

class ModifiableRootModelBridgeImpl(
  diff: WorkspaceEntityStorageBuilder,
  override val moduleBridge: ModuleBridge,
  private val initialStorage: WorkspaceEntityStorage,
  override val accessor: RootConfigurationAccessor,
  cacheStorageResult: Boolean = true
) : LegacyBridgeModifiableBase(diff, cacheStorageResult), ModifiableRootModelBridge, ModuleRootModelBridge {

  /*
    We save the module entity for the following case:
    - Modifiable model created
    - module disposed
    - modifiable model used

    This case can appear, for example, during maven import

    moduleEntity would be removed from this diff after module disposing
  */
  private var savedModuleEntity: ModuleEntity

  init {
    savedModuleEntity = entityStorageOnDiff.current.findModuleEntity(module) ?: error("Cannot find module entity for '$moduleBridge'")
  }

  override fun getModificationCount(): Long = diff.modificationCount

  private val extensionsDisposable = Disposer.newDisposable()

  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  private val extensionsDelegate = lazy {
    RootModelBridgeImpl.loadExtensions(storage = initialStorage, module = module, writable = true,
                                       parentDisposable = extensionsDisposable)
      .filterNot { compilerModuleExtensionClass.isAssignableFrom(it.javaClass) }
  }
  private val extensions by extensionsDelegate

  private val sourceRootPropertiesMap = ConcurrentHashMap<VirtualFileUrl, JpsModuleSourceRoot>()

  internal val moduleEntity: ModuleEntity
    get() {
      val actualModuleEntity = entityStorageOnDiff.current.findModuleEntity(module) ?: return savedModuleEntity
      savedModuleEntity = actualModuleEntity
      return actualModuleEntity
    }

  private val moduleLibraryTable = ModifiableModuleLibraryTableBridge(this)

  /**
   * Contains instances of OrderEntries edited via [ModifiableRootModel] interfaces; we need to keep references to them to update their indices;
   * it should be used for modifications only, in order to read actual state one need to use [orderEntriesArray].
   */
  private val mutableOrderEntries: ArrayList<OrderEntryBridge> by lazy {
    ArrayList<OrderEntryBridge>().also { addOrderEntries(moduleEntity.dependencies, it) }
  }

  /**
   * Provides cached value for [mutableOrderEntries] converted to an array to avoid creating array each time [getOrderEntries] is called;
   * also it updates instances in [mutableOrderEntries] when underlying entities are changed via [WorkspaceModel] interface (e.g. when a
   * library referenced from [LibraryOrderEntry] is renamed).
   */
  private val orderEntriesArrayValue: CachedValue<Array<OrderEntry>> = CachedValue { storage ->
    val dependencies = storage.findModuleEntity(module)?.dependencies ?: return@CachedValue emptyArray()
    if (mutableOrderEntries.size == dependencies.size) {
      //keep old instances of OrderEntries if possible (i.e. if only some properties of order entries were changes via WorkspaceModel)
      for (i in mutableOrderEntries.indices) {
        if (dependencies[i] != mutableOrderEntries[i].item && dependencies[i].javaClass == mutableOrderEntries[i].item.javaClass) {
          mutableOrderEntries[i].item = dependencies[i]
        }
      }
    }
    else {
      mutableOrderEntries.clear()
      addOrderEntries(dependencies, mutableOrderEntries)
    }
    mutableOrderEntries.toTypedArray()
  }
  private val orderEntriesArray
    get() = entityStorageOnDiff.cachedValue(orderEntriesArrayValue)

  private fun addOrderEntries(dependencies: List<ModuleDependencyItem>, target: MutableList<OrderEntryBridge>) =
    dependencies.mapIndexedTo(target) { index, item ->
      RootModelBridgeImpl.toOrderEntry(item, index, this, this::updateDependencyItem)
    }

  private val contentEntriesImplValue: CachedValue<List<ModifiableContentEntryBridge>> = CachedValue { storage ->
    val moduleEntity = storage.findModuleEntity(module) ?: return@CachedValue emptyList<ModifiableContentEntryBridge>()
    val contentEntries = moduleEntity.contentRoots.sortedBy { it.url.url }.toList()

    contentEntries.map {
      ModifiableContentEntryBridge(
        diff = diff,
        contentEntryUrl = it.url,
        modifiableRootModel = this
      )
    }
  }

  private fun updateDependencyItem(index: Int, transformer: (ModuleDependencyItem) -> ModuleDependencyItem) {
    val oldItem = moduleEntity.dependencies[index]
    val newItem = transformer(oldItem)
    if (oldItem == newItem) return

    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      val copy = dependencies.toMutableList()
      copy[index] = newItem
      dependencies = copy
    }
  }

  override val storage: WorkspaceEntityStorage
    get() = entityStorageOnDiff.current

  override fun getOrCreateJpsRootProperties(sourceRootUrl: VirtualFileUrl, creator: () -> JpsModuleSourceRoot): JpsModuleSourceRoot {
    return sourceRootPropertiesMap.computeIfAbsent(sourceRootUrl) { creator() }
  }

  override fun removeCachedJpsRootProperties(sourceRootUrl: VirtualFileUrl) {
    sourceRootPropertiesMap.remove(sourceRootUrl)
  }

  private val contentEntries
    get() = entityStorageOnDiff.cachedValue(contentEntriesImplValue)

  override fun getProject(): Project = moduleBridge.project

  override fun addContentEntry(root: VirtualFile): ContentEntry =
    addContentEntry(root.url)

  override fun addContentEntry(url: String): ContentEntry {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)
    val existingEntry = contentEntries.firstOrNull { it.contentEntryUrl == virtualFileUrl }
    if (existingEntry != null) {
      return existingEntry
    }

    diff.addContentRootEntity(
        module = moduleEntity,
        excludedUrls = emptyList(),
        excludedPatterns = emptyList(),
        url = virtualFileUrl
    )

    // TODO It's N^2 operations since we need to recreate contentEntries every time
    return contentEntries.firstOrNull { it.contentEntryUrl == virtualFileUrl }
           ?: error("addContentEntry: unable to find content entry after adding: $url to module ${moduleEntity.name}")
  }

  override fun removeContentEntry(entry: ContentEntry) {
    assertModelIsLive()

    val entryImpl = entry as ModifiableContentEntryBridge
    val contentEntryUrl = entryImpl.contentEntryUrl

    val entity = currentModel.contentEntities.firstOrNull { it.url == contentEntryUrl }
                 ?: error("ContentEntry $entry does not belong to modifiableRootModel of module ${moduleBridge.name}")

    entry.clearSourceFolders()
    diff.removeEntity(entity)

    if (assertChangesApplied && contentEntries.any { it.url == contentEntryUrl.url }) {
      error("removeContentEntry: removed content entry url '$contentEntryUrl' still exists after removing")
    }
  }

  override fun addOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()
    when (orderEntry) {
      is LibraryOrderEntryBridge -> {
        if (orderEntry.isModuleLevel) {
          moduleLibraryTable.addLibraryCopy(orderEntry.library as LibraryBridgeImpl, orderEntry.isExported,
                                            orderEntry.libraryDependencyItem.scope)
        }
        else {
          appendDependency(orderEntry.libraryDependencyItem)
        }
      }

      is ModuleOrderEntry -> orderEntry.module?.let { addModuleOrderEntry(it) } ?: error("Module is empty: $orderEntry")
      is ModuleSourceOrderEntry -> appendDependency(ModuleDependencyItem.ModuleSourceDependency)

      is InheritedJdkOrderEntry -> appendDependency(ModuleDependencyItem.InheritedSdkDependency)
      is ModuleJdkOrderEntry -> appendDependency((orderEntry as SdkOrderEntryBridge).sdkDependencyItem)

      else -> error("OrderEntry should not be extended by external systems")
    }
  }

  override fun addLibraryEntry(library: Library): LibraryOrderEntry {
    val libraryId = if (library is LibraryBridge) library.libraryId else {
      val libraryName = library.name
      if (libraryName.isNullOrEmpty()) {
        error("Library name is null or empty: $library")
      }

      LibraryId(libraryName, levelToLibraryTableId(library.table.tableLevel))
    }

    val libraryDependency = ModuleDependencyItem.Exportable.LibraryDependency(
      library = libraryId,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    appendDependency(libraryDependency)


    return (mutableOrderEntries.lastOrNull() as? LibraryOrderEntry ?: error("Unable to find library orderEntry after adding"))
  }

  override fun addInvalidLibrary(name: String, level: String): LibraryOrderEntry {
    val libraryDependency = ModuleDependencyItem.Exportable.LibraryDependency(
      library = LibraryId(name, levelToLibraryTableId(level)),
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    appendDependency(libraryDependency)

    return (mutableOrderEntries.lastOrNull() as? LibraryOrderEntry ?: error("Unable to find library orderEntry after adding"))
  }

  override fun addModuleOrderEntry(module: Module): ModuleOrderEntry {
    val moduleDependency = ModuleDependencyItem.Exportable.ModuleDependency(
      module = (module as ModuleBridge).moduleEntityId,
      productionOnTest = false,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    appendDependency(moduleDependency)

    return mutableOrderEntries.lastOrNull() as? ModuleOrderEntry ?: error("Unable to find module orderEntry after adding")
  }

  override fun addInvalidModuleEntry(name: String): ModuleOrderEntry {
    val moduleDependency = ModuleDependencyItem.Exportable.ModuleDependency(
      module = ModuleId(name),
      productionOnTest = false,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    appendDependency(moduleDependency)

    return mutableOrderEntries.lastOrNull() as? ModuleOrderEntry ?: error("Unable to find module orderEntry after adding")
  }

  internal fun appendDependency(dependency: ModuleDependencyItem) {
    mutableOrderEntries.add(RootModelBridgeImpl.toOrderEntry(dependency, mutableOrderEntries.size, this, this::updateDependencyItem))
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = dependencies + dependency
    }
  }

  internal fun insertDependency(dependency: ModuleDependencyItem, position: Int): OrderEntryBridge {
    val last = position == mutableOrderEntries.size
    val newEntry = RootModelBridgeImpl.toOrderEntry(dependency, position, this, this::updateDependencyItem)
    if (last) {
      mutableOrderEntries.add(newEntry)
    }
    else {
      mutableOrderEntries.add(position, newEntry)
      for (i in position+1 until mutableOrderEntries.size) {
        mutableOrderEntries[i].updateIndex(i)
      }
    }
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = if (last) dependencies + dependency
      else dependencies.subList(0, position) + dependency + dependencies.subList(position, dependencies.size)
    }
    return newEntry
  }

  internal fun removeDependencies(filter: (ModuleDependencyItem) -> Boolean) {
    val newDependencies = ArrayList<ModuleDependencyItem>()
    val newOrderEntries = ArrayList<OrderEntryBridge>()
    val oldDependencies = moduleEntity.dependencies
    for (i in oldDependencies.indices) {
      if (!filter(oldDependencies[i])) {
        newDependencies.add(oldDependencies[i])
        val entryBridge = mutableOrderEntries[i]
        entryBridge.updateIndex(newOrderEntries.size)
        newOrderEntries.add(entryBridge)
      }
    }
    mutableOrderEntries.clear()
    mutableOrderEntries.addAll(newOrderEntries)
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = newDependencies
    }
  }

  override fun findModuleOrderEntry(module: Module): ModuleOrderEntry? {
    return orderEntries.filterIsInstance<ModuleOrderEntry>().firstOrNull { module == it.module }
  }

  override fun findLibraryOrderEntry(library: Library): LibraryOrderEntry? {
    if (library is LibraryBridge) {
      val libraryIdToFind = library.libraryId
      return orderEntries
        .filterIsInstance<LibraryOrderEntry>()
        .firstOrNull { libraryIdToFind == (it.library as? LibraryBridge)?.libraryId }
    }
    else {
      return orderEntries.filterIsInstance<LibraryOrderEntry>().firstOrNull { it.library == library }
    }
  }

  override fun removeOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()

    val entryImpl = orderEntry as OrderEntryBridge
    val item = entryImpl.item

    if (mutableOrderEntries.none { it.item == item }) {
      LOG.error("OrderEntry $item does not belong to modifiableRootModel of module ${moduleBridge.name}")
      return
    }

    if (orderEntry is LibraryOrderEntryBridge && orderEntry.isModuleLevel) {
      moduleLibraryTable.removeLibrary(orderEntry.library as LibraryBridge)
    }
    else {
      removeDependencies { it == item }
    }

    if (assertChangesApplied && mutableOrderEntries.any { it.item == item })
      error("removeOrderEntry: removed order entry $item still exists after removing")
  }

  override fun rearrangeOrderEntries(newOrder: Array<out OrderEntry>) {
    val newOrderEntries = newOrder.mapTo(ArrayList()) { it as OrderEntryBridge }
    val newEntities = newOrderEntries.map { it.item }
    if (newEntities.toSet() != moduleEntity.dependencies.toSet()) {
      error("Expected the same entities as existing order entries, but in a different order")
    }

    mutableOrderEntries.clear()
    mutableOrderEntries.addAll(newOrderEntries)
    for (i in mutableOrderEntries.indices) {
      mutableOrderEntries[i].updateIndex(i)
    }
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = newEntities
    }
  }

  override fun clear() {
    for (library in moduleLibraryTable.libraries) {
      moduleLibraryTable.removeLibrary(library)
    }

    val currentSdk = sdk
    val jdkItem = currentSdk?.let { ModuleDependencyItem.SdkDependency(it.name, it.sdkType.name) }
    if (moduleEntity.dependencies != listOfNotNull(jdkItem, ModuleDependencyItem.ModuleSourceDependency)) {
      removeDependencies { true }
      if (jdkItem != null) {
        appendDependency(jdkItem)
      }
      appendDependency(ModuleDependencyItem.ModuleSourceDependency)
    }

    for (contentRoot in moduleEntity.contentRoots) {
      diff.removeEntity(contentRoot)
    }
  }

  fun collectChangesAndDispose(): WorkspaceEntityStorageBuilder? {
    assertModelIsLive()
    Disposer.dispose(moduleLibraryTable)
    if (!isChanged) {
      moduleLibraryTable.restoreLibraryMappingsAndDisposeCopies()
      disposeWithoutLibraries()
      return null
    }

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) {
      val element = Element("component")

      for (extension in extensions) {
        extension.commit()

        if (extension is PersistentStateComponent<*>) {
          serializeStateInto(extension, element)
        }
        else {
          @Suppress("DEPRECATION")
          extension.writeExternal(element)
        }
      }

      val elementAsString = JDOMUtil.writeElement(element)
      val customImlDataEntity = moduleEntity.customImlData

      if (customImlDataEntity?.rootManagerTagCustomData != elementAsString) {
        when {
          customImlDataEntity == null && !element.isEmpty() -> diff.addModuleCustomImlDataEntity(
            module = moduleEntity,
            rootManagerTagCustomData = elementAsString,
            customModuleOptions = emptyMap(),
            source = moduleEntity.entitySource
          )

          customImlDataEntity == null && element.isEmpty() -> Unit

          customImlDataEntity != null && customImlDataEntity.customModuleOptions.isEmpty() && element.isEmpty() ->
            diff.removeEntity(customImlDataEntity)

          customImlDataEntity != null && customImlDataEntity.customModuleOptions.isNotEmpty() && element.isEmpty() ->
            diff.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java, customImlDataEntity) {
              rootManagerTagCustomData = null
            }

          customImlDataEntity != null && !element.isEmpty() -> diff.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java,
            customImlDataEntity) {
            rootManagerTagCustomData = elementAsString
          }

          else -> error("Should not be reached")
        }
      }
    }

    if (!sourceRootPropertiesMap.isEmpty()) {
      for (sourceRoot in moduleEntity.sourceRoots) {
        val actualSourceRootData = sourceRootPropertiesMap[sourceRoot.url] ?: continue
        SourceRootPropertiesHelper.applyChanges(diff, sourceRoot, actualSourceRootData)
      }
    }

    disposeWithoutLibraries()
    return diff
  }

  private fun areSourceRootPropertiesChanged(): Boolean {
    if (sourceRootPropertiesMap.isEmpty()) return false
    return moduleEntity.sourceRoots.any { sourceRoot ->
      val actualSourceRootData = sourceRootPropertiesMap[sourceRoot.url]
      actualSourceRootData != null && !SourceRootPropertiesHelper.hasEqualProperties(sourceRoot, actualSourceRootData)
    }
  }

  override fun commit() {
    val diff = collectChangesAndDispose() ?: return
    val moduleDiff = module.diff
    if (moduleDiff != null) {
      moduleDiff.addDiff(diff)
    } else {
      WorkspaceModel.getInstance(project).updateProjectModel {
        it.addDiff(diff)
      }
    }
    postCommit()
  }

  override fun prepareForCommit() {
    collectChangesAndDispose()
  }

  override fun postCommit() {
    moduleLibraryTable.disposeOriginalLibrariesAndUpdateCopies()
  }

  override fun dispose() {
    disposeWithoutLibraries()
    moduleLibraryTable.disposeLibraryCopies()
    Disposer.dispose(moduleLibraryTable)
  }

  private fun disposeWithoutLibraries() {
    if (!modelIsCommittedOrDisposed) {
      Disposer.dispose(extensionsDisposable)
    }

    // No assertions here since it is ok to call dispose twice or more
    modelIsCommittedOrDisposed = true
  }

  override fun getModuleLibraryTable(): LibraryTable = moduleLibraryTable

  override fun setSdk(jdk: Sdk?) {
    if (jdk == null) {
      setSdkItem(null)

      if (assertChangesApplied && sdkName != null) {
        error("setSdk: expected sdkName is null, but got: $sdkName")
      }
    } else {
      if (SdkOrderEntryBridge.findSdk(jdk.name, jdk.sdkType.name) == null) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          // TODO Fix all tests and remove this
          (ProjectJdkTable.getInstance() as ProjectJdkTableImpl).addTestJdk(jdk, project)
        } else {
          error("setSdk: sdk '${jdk.name}' type '${jdk.sdkType.name}' is not registered in ProjectJdkTable")
        }
      }

      setInvalidSdk(jdk.name, jdk.sdkType.name)
    }
  }

  override fun setInvalidSdk(sdkName: String, sdkType: String) {
    setSdkItem(ModuleDependencyItem.SdkDependency(sdkName, sdkType))

    if (assertChangesApplied && getSdkName() != sdkName) {
      error("setInvalidSdk: expected sdkName '$sdkName' but got '${getSdkName()}' after doing a change")
    }
  }

  override fun inheritSdk() {
    if (isSdkInherited) return

    setSdkItem(ModuleDependencyItem.InheritedSdkDependency)

    if (assertChangesApplied && !isSdkInherited) {
      error("inheritSdk: Sdk is still not inherited after inheritSdk()")
    }
  }

  // TODO compare by actual values
  override fun isChanged(): Boolean {
    if (!diff.isEmpty()) return true

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) return true

    if (areSourceRootPropertiesChanged()) return true

    return false
  }

  override fun isWritable(): Boolean = true

  override fun <T : OrderEntry?> replaceEntryOfType(entryClass: Class<T>, entry: T) =
    throw NotImplementedError("Not implemented since it was used only by project model implementation")

  override fun getSdkName(): String? = orderEntries.filterIsInstance<JdkOrderEntry>().firstOrNull()?.jdkName

  // TODO
  override fun isDisposed(): Boolean = modelIsCommittedOrDisposed

  private fun setSdkItem(item: ModuleDependencyItem?) {
    removeDependencies { it is ModuleDependencyItem.InheritedSdkDependency || it is ModuleDependencyItem.SdkDependency }
    if (item != null) {
      insertDependency(item, 0)
    }
  }

  private val modelValue = CachedValue { storage ->
    RootModelBridgeImpl(
      moduleEntity = storage.findModuleEntity(moduleBridge),
      storage = storage,
      itemUpdater = null,
      rootModel = this,
      updater = { transformer -> transformer(diff) }
    )
  }

  internal val currentModel
    get() = entityStorageOnDiff.cachedValue(modelValue)

  private val compilerModuleExtension by lazy {
    CompilerModuleExtensionBridge(moduleBridge, entityStorage = entityStorageOnDiff, diff = diff)
  }
  private val compilerModuleExtensionClass = CompilerModuleExtension::class.java

  override fun getExcludeRoots(): Array<VirtualFile> = currentModel.excludeRoots

  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, null)

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    if (compilerModuleExtensionClass.isAssignableFrom(klass)) {
      @Suppress("UNCHECKED_CAST")
      return compilerModuleExtension as T
    }

    return extensions.filterIsInstance(klass).firstOrNull()
  }

  override fun getDependencyModuleNames(): Array<String> {
    val result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries().process(CollectDependentModules(), ArrayList())
    return ArrayUtilRt.toStringArray(result)
  }

  override fun getModule(): ModuleBridge = moduleBridge
  override fun isSdkInherited(): Boolean = orderEntriesArray.any { it is InheritedJdkOrderEntry }
  override fun getOrderEntries(): Array<OrderEntry> = orderEntriesArray
  override fun getSourceRootUrls(): Array<String> = currentModel.sourceRootUrls
  override fun getSourceRootUrls(includingTests: Boolean): Array<String> = currentModel.getSourceRootUrls(includingTests)
  override fun getContentEntries(): Array<ContentEntry> = contentEntries.toTypedArray()
  override fun getExcludeRootUrls(): Array<String> = currentModel.excludeRootUrls
  override fun <R : Any?> processOrder(policy: RootPolicy<R>, initialValue: R): R {
    var result = initialValue
    for (orderEntry in orderEntries) {
      result = orderEntry.accept(policy, result)
    }
    return result
  }

  override fun getSdk(): Sdk? = (orderEntriesArray.find { it is JdkOrderEntry } as JdkOrderEntry?)?.jdk
  override fun getSourceRoots(): Array<VirtualFile> = currentModel.sourceRoots
  override fun getSourceRoots(includingTests: Boolean): Array<VirtualFile> = currentModel.getSourceRoots(includingTests)
  override fun getSourceRoots(rootType: JpsModuleSourceRootType<*>): MutableList<VirtualFile> = currentModel.getSourceRoots(rootType)
  override fun getSourceRoots(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): MutableList<VirtualFile> = currentModel.getSourceRoots(rootTypes)
  override fun getContentRoots(): Array<VirtualFile> = currentModel.contentRoots
  override fun getContentRootUrls(): Array<String> = currentModel.contentRootUrls
  override fun getModuleDependencies(): Array<Module> = getModuleDependencies(true)

  override fun getModuleDependencies(includeTests: Boolean): Array<Module> {
    var result: MutableList<Module>? = null
    for (entry in orderEntriesArray) {
      if (entry is ModuleOrderEntry) {
        val scope = entry.scope
        if (includeTests || scope.isForProductionCompile || scope.isForProductionRuntime) {
          val module = entry.module
          if (module != null) {
            if (result == null) {
              result = SmartList()
            }
            result.add(module)
          }
        }
      }
    }
    return if (result.isNullOrEmpty()) Module.EMPTY_ARRAY else result.toTypedArray()
  }

  companion object {
    private val LOG = logger<ModifiableRootModelBridgeImpl>()
  }
}

