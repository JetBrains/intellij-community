// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ClonableOrderEntry
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.impl.SdkFinder
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
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleByEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal abstract class OrderEntryBridge(
  private val rootModel: ModuleRootModelBridge,
  private val initialIndex: Int,
  var item: ModuleDependencyItem,
  private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : OrderEntry {
  protected var index = initialIndex

  protected val ownerModuleBridge: ModuleBridge
    get() = rootModel.moduleBridge

  protected val updater: (Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit
    get() = itemUpdater ?: error("This mode is read-only. Call from a modifiable model")

  fun getRootModel(): ModuleRootModelBridge = rootModel

  fun updateIndex(newIndex: Int) {
    index = newIndex
  }

  override fun getOwnerModule() = ownerModuleBridge
  override fun compareTo(other: OrderEntry?) = index.compareTo((other as OrderEntryBridge).index)
  override fun isValid() = true
  override fun isSynthetic() = false

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OrderEntryBridge

    if (ownerModuleBridge != other.ownerModuleBridge) return false
    if (initialIndex != other.initialIndex) return false
    if (item != other.item) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ownerModuleBridge.hashCode()
    result = 31 * result + initialIndex
    result = 31 * result + item.hashCode()
    return result
  }
}

internal abstract class ExportableOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  exportableDependencyItem: ModuleDependencyItem.Exportable,
  itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
): OrderEntryBridge(rootModel, index, exportableDependencyItem, itemUpdater), ExportableOrderEntry {
  private val exportableItem
    get() = item as ModuleDependencyItem.Exportable

  override fun isExported() = exportableItem.exported
  override fun setExported(value: Boolean) {
    if (isExported == value) return
    updater(index) { (it as ModuleDependencyItem.Exportable).withExported(value) }
    item = (item as ModuleDependencyItem.Exportable).withExported(value)
  }

  override fun getScope() = exportableItem.scope.toDependencyScope()
  override fun setScope(scope: DependencyScope) {
    if (getScope() == scope) return
    updater(index) { (it as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope()) }
    item = (item as ModuleDependencyItem.Exportable).withScope(scope.toEntityDependencyScope())
  }
}

internal class ModuleOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  moduleDependencyItem: ModuleDependencyItem.Exportable.ModuleDependency,
  itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryBridge(rootModel, index, moduleDependencyItem, itemUpdater), ModuleOrderEntry, ClonableOrderEntry {

  private val moduleDependencyItem
    get() = item as ModuleDependencyItem.Exportable.ModuleDependency

  override fun getModule(): Module? {
    val storage = getRootModel().storage
    val moduleEntity = storage.resolve(moduleDependencyItem.module)
    val module = moduleEntity?.let {
      storage.findModuleByEntity(it)
    }
    return getRootModel().accessor.getModule(module, moduleName)
  }

  override fun getModuleName() = moduleDependencyItem.module.name

  override fun isProductionOnTestDependency() = moduleDependencyItem.productionOnTest

  override fun setProductionOnTestDependency(productionOnTestDependency: Boolean) {
    if (isProductionOnTestDependency == productionOnTestDependency) return
    updater(index) { item -> (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency) }
    item = (item as ModuleDependencyItem.Exportable.ModuleDependency).copy(productionOnTest = productionOnTestDependency)
  }

  override fun getFiles(type: OrderRootType): Array<VirtualFile> = getEnumerator(type)?.roots ?: VirtualFile.EMPTY_ARRAY

  override fun getUrls(rootType: OrderRootType): Array<String> = getEnumerator(rootType)?.urls ?: ArrayUtil.EMPTY_STRING_ARRAY

  private fun getEnumerator(rootType: OrderRootType) = ownerModuleBridge.let {
    ModuleRootManagerImpl.getCachingEnumeratorForType(rootType, it)
  }

  override fun getPresentableName() = moduleName

  override fun isValid(): Boolean = module != null

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitModuleOrderEntry(this, initialValue)

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
  item: ModuleDependencyItem
) : OrderEntryBridge(rootModel, index, item, null), LibraryOrSdkOrderEntry {

  protected abstract val rootProvider: RootProvider?

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)
}

internal class LibraryOrderEntryBridge(
  rootModel: ModuleRootModelBridge,
  index: Int,
  libraryDependencyItem: ModuleDependencyItem.Exportable.LibraryDependency,
  itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
) : ExportableOrderEntryBridge(rootModel, index, libraryDependencyItem, itemUpdater), LibraryOrderEntry, ClonableOrderEntry {

  override fun getPresentableName(): String = libraryName ?: getPresentableNameForUnnamedLibrary()
  internal val libraryDependencyItem
    get() = item as ModuleDependencyItem.Exportable.LibraryDependency

  @Nls
  private fun getPresentableNameForUnnamedLibrary(): String {
    val url = getUrls(OrderRootType.CLASSES).firstOrNull()
    return if (url != null) PathUtil.toPresentableUrl(url) else ProjectModelBundle.message("empty.library.title")
  }

  private val rootProvider: RootProvider?
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

  override fun getRootFiles(type: OrderRootType): Array<VirtualFile> = rootProvider?.getFiles(type) ?: VirtualFile.EMPTY_ARRAY

  override fun getRootUrls(type: OrderRootType): Array<String> = rootProvider?.getUrls(type) ?: ArrayUtil.EMPTY_STRING_ARRAY

  override fun getFiles(type: OrderRootType) = getRootFiles(type)

  override fun getUrls(rootType: OrderRootType) = getRootUrls(rootType)

  override fun isValid(): Boolean = library != null

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
) : SdkOrderEntryBaseBridge(rootModel, index, sdkDependencyItem), ModuleJdkOrderEntry, ClonableOrderEntry {

  override val rootProvider: RootProvider?
    get() = jdk?.rootProvider

  override fun getPresentableName() = "< ${jdk?.name ?: sdkDependencyItem.sdkName} >"

  override fun isValid(): Boolean = jdk != null

  override fun getJdk(): Sdk? {
    val sdkType = sdkDependencyItem.sdkType
    val sdkName = sdkDependencyItem.sdkName
    val sdk = findSdk(sdkName, sdkType)
    return getRootModel().accessor.getSdk(sdk, sdkName)
  }

  override fun getJdkName() = sdkDependencyItem.sdkName

  override fun getJdkTypeName() = sdkDependencyItem.sdkType

  override fun <R : Any?> accept(policy: RootPolicy<R>, initialValue: R?): R? = policy.visitJdkOrderEntry(this, initialValue)

  override fun cloneEntry(rootModel: ModifiableRootModel,
                          projectRootManager: ProjectRootManagerImpl,
                          filePointerManager: VirtualFilePointerManager
  ): OrderEntry = SdkOrderEntryBridge(rootModel as ModuleRootModelBridge, index, sdkDependencyItem.copy())

  override fun isSynthetic(): Boolean = true

  companion object {
    @JvmStatic
    internal fun findSdk(sdkName: String, sdkType: String): Sdk? {
      for (finder in SdkFinder.EP_NAME.extensions) {
        val sdk = finder.findSdk(sdkName, sdkType)
        if (sdk != null) return sdk
      }

      return ProjectJdkTable.getInstance().findJdk(sdkName, sdkType)
    }
  }
}

internal class InheritedSdkOrderEntryBridge(rootModel: ModuleRootModelBridge, index: Int, item: ModuleDependencyItem.InheritedSdkDependency)
  : SdkOrderEntryBaseBridge(rootModel, index, item), InheritedJdkOrderEntry, ClonableOrderEntry {

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
