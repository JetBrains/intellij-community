// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ClonableOrderEntry
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.jps.serialization.getLegacyLibraryName
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal abstract class OrderEntryBridge(
  private val rootModel: ModuleRootModelBridge,
  protected val index: Int,
  var item: ModuleDependencyItem,
  private val itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntry {
  protected val ownerModuleBridge: ModuleBridge
    get() = rootModel.moduleBridge

  protected val updater: ((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit
    get() = itemUpdater ?: error("This mode is read-only. Call from a modifiable model")

  fun getRootModel(): ModuleRootModelBridge = rootModel

  override fun getOwnerModule() = ownerModuleBridge
  override fun compareTo(other: OrderEntry?) = index.compareTo((other as OrderEntryBridge).index)
  override fun isValid() = true
  override fun isSynthetic() = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OrderEntryBridge

    if (ownerModuleBridge != other.ownerModuleBridge) return false
    if (index != other.index) return false
    if (item != other.item) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ownerModuleBridge.hashCode()
    result = 31 * result + index
    result = 31 * result + item.hashCode()
    return result
  }
}

internal abstract class ExportableOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  exportableDependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
): OrderEntryBridge(rootModel, index, exportableDependencyItem, itemUpdater), ExportableOrderEntry {

  private var exportedVar = exportableDependencyItem.exported
  private var scopeVar = exportableDependencyItem.scope

  override fun isExported() = exportedVar
  override fun setExported(value: Boolean) {
    if (exportedVar == value) return
    updater { (it as ModuleDependencyItem.Exportable).withExported(value) }
    exportedVar = value
    item = (item as ModuleDependencyItem.Exportable).withExported(value)
  }

  override fun getScope() = scopeVar.toDependencyScope()
  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater { (it as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope()) }
    scopeVar = scope.toEntityDependencyScope()
    item = (item as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope())
  }
}

internal abstract class ModuleOrderEntryBaseBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  dependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryBridge(rootModel, index, dependencyItem, itemUpdater), ModuleOrderEntry, ClonableOrderEntry {

  override fun getFiles(type: OrderRootType): Array<VirtualFile> {
    return getEnumerator(type)?.roots ?: VirtualFile.EMPTY_ARRAY
  }

  override fun getUrls(rootType: OrderRootType): Array<String> {
    return getEnumerator(rootType)?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY
  }

  private fun getEnumerator(rootType: OrderRootType) = ownerModuleBridge.let { ModuleRootManagerImpl.getCachingEnumeratorForType(rootType, it) }

  override fun getPresentableName() = moduleName
  override fun isValid(): Boolean = module != null

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitModuleOrderEntry(this, initialValue)
}

internal class ModuleOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  private val moduleDependencyItem: ModuleDependencyItem.Exportable.ModuleDependency,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ModuleOrderEntryBaseBridge(rootModel, index, moduleDependencyItem, itemUpdater) {

  private var productionOnTestVar = moduleDependencyItem.productionOnTest

  override fun getModule(): Module? {
    val storage = getRootModel().storage
    val moduleEntity = storage.resolve(moduleDependencyItem.module)
    val module = moduleEntity?.let {
      storage.moduleMap.getDataByEntity(it)
    }
    return getRootModel().accessor.getModule(module, moduleName)
  }

  override fun getModuleName() = moduleDependencyItem.module.name

  override fun isProductionOnTestDependency() = productionOnTestVar

  override fun setProductionOnTestDependency(productionOnTestDependency: Boolean) {
    if (productionOnTestVar == productionOnTestDependency) return
    updater { item -> (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency) }
    productionOnTestVar = productionOnTestDependency
    item = (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency)
  }

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleOrderEntryBridge(
    rootModel as ModuleRootModelBridge,
    index, moduleDependencyItem.copy(), null
  )
}

fun ModuleDependencyItem.DependencyScope.toDependencyScope() = when (this) {
  ModuleDependencyItem.DependencyScope.COMPILE -> DependencyScope.COMPILE
  ModuleDependencyItem.DependencyScope.RUNTIME -> DependencyScope.RUNTIME
  ModuleDependencyItem.DependencyScope.PROVIDED -> DependencyScope.PROVIDED
  ModuleDependencyItem.DependencyScope.TEST -> DependencyScope.TEST
}

fun DependencyScope.toEntityDependencyScope(): ModuleDependencyItem.DependencyScope = when (this) {
  DependencyScope.COMPILE -> ModuleDependencyItem.DependencyScope.COMPILE
  DependencyScope.RUNTIME -> ModuleDependencyItem.DependencyScope.RUNTIME
  DependencyScope.PROVIDED -> ModuleDependencyItem.DependencyScope.PROVIDED
  DependencyScope.TEST -> ModuleDependencyItem.DependencyScope.TEST
}

internal abstract class SdkOrderEntryBaseBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  item: ModuleDependencyItem,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntryBridge(rootModel, index, item, itemUpdater), LibraryOrSdkOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)
}

internal abstract class LibraryOrderEntryBaseBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  item: ModuleDependencyItem.Exportable,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryBridge(rootModel, index, item, itemUpdater), LibraryOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)

  override fun isValid(): Boolean = library != null
}

internal class LibraryOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  internal val libraryDependencyItem: ModuleDependencyItem.Exportable.LibraryDependency,
  itemUpdater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : LibraryOrderEntryBaseBridge(rootModel, index, libraryDependencyItem, itemUpdater), LibraryOrderEntry, ClonableOrderEntry {

  override fun getPresentableName(): String = libraryName ?: getPresentableNameForUnnamedLibrary()

  @Nls
  private fun getPresentableNameForUnnamedLibrary(): String {
    val url = getUrls(OrderRootType.CLASSES).firstOrNull()
    return if (url != null) PathUtil.toPresentableUrl(url) else ProjectModelBundle.message("empty.library.title")
  }

  override val rootProvider: RootProvider?
    get() = library?.rootProvider

  override fun getLibraryLevel() = libraryDependencyItem.library.tableId.level

  override fun getLibraryName() = getLegacyLibraryName(libraryDependencyItem.library)

  override fun getLibrary(): Library? {
    val libraryId = libraryDependencyItem.library
    val tableId = libraryId.tableId
    val library = if (tableId is LibraryTableId.GlobalLibraryTableId) {
      LibraryTablesRegistrar.getInstance()
        .getLibraryTableByLevel(tableId.level, ownerModuleBridge.project)
        ?.getLibraryByName(libraryId.name)
    }
    else {
      val storage = getRootModel().storage
      val libraryEntity = storage.resolve(libraryId)
      libraryEntity?.let { storage.libraryMap.getDataByEntity(libraryEntity) }
    }

    return if (tableId is LibraryTableId.ModuleLibraryTableId) {
      // model.accessor.getLibrary is not applicable to module libraries
      library
    } else {
      getRootModel().accessor.getLibrary(library, libraryName, libraryLevel)
    }
  }

  override fun isModuleLevel() = libraryLevel == JpsLibraryTableSerializer.MODULE_LEVEL

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitLibraryOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry {
    return LibraryOrderEntryBridge(getRootModel(), index, libraryDependencyItem, null)
  }

  override fun isSynthetic(): Boolean = isModuleLevel
}

internal class SdkOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  internal val sdkDependencyItem: ModuleDependencyItem.SdkDependency
) : SdkOrderEntryBaseBridge(rootModel, index, sdkDependencyItem, null), ModuleJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getPresentableName() = "< ${jdk?.name ?: sdkDependencyItem.sdkName} >"

  override fun isValid(): Boolean = jdk != null

  override fun getJdk(): Sdk? {
    val jdkTable = ProjectJdkTable.getInstance()

    val sdkType = sdkDependencyItem.sdkType
    val sdk = jdkTable.findJdk(sdkDependencyItem.sdkName, sdkType)
    return getRootModel().accessor.getSdk(sdk, sdkDependencyItem.sdkName)
  }

  override fun getJdkName() = sdkDependencyItem.sdkName

  override fun getJdkTypeName() = sdkDependencyItem.sdkType

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = SdkOrderEntryBridge(rootModel as ModuleRootModelBridge, index, sdkDependencyItem.copy())

  override fun isSynthetic(): Boolean = true
}

internal class InheritedSdkOrderEntryBridge(rootModel: ModuleRootModelBridge, index: Int, item: ModuleDependencyItem.InheritedSdkDependency)
  : SdkOrderEntryBaseBridge(rootModel, index, item, null), InheritedJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getJdk(): Sdk? = getRootModel().accessor.getProjectSdk(getRootModel().moduleBridge.project)
  override fun getJdkName(): String? = getRootModel().accessor.getProjectSdkName(getRootModel().moduleBridge.project)

  override fun isValid(): Boolean = jdk != null

  override fun getPresentableName() = "< $jdkName >"

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitInheritedJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = InheritedSdkOrderEntryBridge(
    rootModel as ModuleRootModelBridge, index, ModuleDependencyItem.InheritedSdkDependency
  )
}

internal class ModuleSourceOrderEntryBridge(rootModel: ModuleRootModelBridge, index: Int, item: ModuleDependencyItem.ModuleSourceDependency)
  : OrderEntryBridge(rootModel, index, item, null), ModuleSourceOrderEntry, ClonableOrderEntry {
  override fun getFiles(type: OrderRootType): Array<out VirtualFile> = if (type == OrderRootType.SOURCES) rootModel.sourceRoots else VirtualFile.EMPTY_ARRAY

  override fun getUrls(rootType: OrderRootType): Array<out String> = if (rootType == OrderRootType.SOURCES) rootModel.sourceRootUrls else ArrayUtil.EMPTY_STRING_ARRAY

  override fun getPresentableName(): String = ProjectModelBundle.message("project.root.module.source")

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? =
    policy.visitModuleSourceOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = ModuleSourceOrderEntryBridge(
    rootModel as ModuleRootModelBridge, index, ModuleDependencyItem.ModuleSourceDependency
  )

  override fun isSynthetic(): Boolean = true
}
