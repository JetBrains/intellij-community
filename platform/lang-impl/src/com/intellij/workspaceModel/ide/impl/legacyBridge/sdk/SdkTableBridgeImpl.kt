// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.UnknownSdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.ELEMENT_ADDITIONAL
import com.intellij.platform.workspace.jps.serialization.impl.JpsGlobalEntitiesSerializers
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.sdk.GlobalSdkTableBridge
import org.jdom.Element

//TODO::
// [] Different tag name for library root and SDK roots e.g sources, classes
// [] Old version `2` constantly serialized for SDK
// [] Strange to have type `SDK` but methods - `updateJDK`
// [] One more implementation of `ProjectJdkTable` => `JavaAwareProjectJdkTableImpl`
// [] SdkType.EP_NAME.addExtensionPointListener
// [] Additional data loading/saving
// [] The same problem as with FacetConfiguration we have 7 types of additional data for SDK so 7 entities
internal val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }
class SdkTableBridgeImpl: GlobalSdkTableBridge() {

  private val cachedProjectJdks: MutableMap<String, SdkBridgeImpl> = HashMap()
  override fun initializeSdkBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                initialEntityStorage: VersionedEntityStorage): () -> Unit {
     val sdks = mutableStorage
      .entities(SdkMainEntity::class.java)
      .filter { mutableStorage.sdkMap.getDataByEntity(it) == null }
      .map { sdkEntity -> sdkEntity to SdkBridgeImpl(sdkEntity as SdkMainEntity.Builder) }
      .toList()
    thisLogger().debug("Initial load of SDKs")

    for ((entity, sdkBridge) in sdks) {
      mutableStorage.mutableSdkMap.addIfAbsent(entity, sdkBridge)
    }
    return {}
  }

  override fun findJdk(name: String): Sdk? {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    val sdkEntity = currentSnapshot.entities(SdkMainEntity::class.java)
      .firstOrNull { Comparing.strEqual(name, it.name) } ?: return null
    return currentSnapshot.sdkMap.getDataByEntity(sdkEntity)
  }

  override fun findJdk(name: String, type: String): Sdk? {
    var sdk = findJdk(name)
    if (sdk != null) return sdk

    val uniqueName = "$type.$name"
    sdk = cachedProjectJdks[uniqueName]
    if (sdk != null) return sdk

    val sdkPath = System.getProperty("jdk.$name")
    if (sdkPath == null) return null

    val sdkType = SdkType.findByName(type)
    if (sdkType != null && sdkType.isValidSdkHome(sdkPath)) {
      val createdSdk = createSdkBridge(name, sdkType.name, sdkPath)
      sdkType.setupSdkPaths(createdSdk)
      cachedProjectJdks[uniqueName] = createdSdk
      return createdSdk
    }
    return null
  }

  override fun getAllJdks(): Array<Sdk> {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    return currentSnapshot.entities(SdkMainEntity::class.java)
                      .mapNotNull { currentSnapshot.sdkMap.getDataByEntity(it) }
                      .toList()
                      .toTypedArray()
  }

  override fun getSdksOfType(type: SdkTypeId): List<Sdk> {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    return currentSnapshot.entities(SdkMainEntity::class.java).filter { it.type == type.name }
      .mapNotNull { currentSnapshot.sdkMap.getDataByEntity(it) }
      .toList()
  }

  override fun getSdkTypeByName(sdkTypeName: String): SdkTypeId {
    return SdkType.getAllTypes().firstOrNull { it.name == sdkTypeName } ?: UnknownSdkType.getInstance(sdkTypeName)
  }

  override fun getDefaultSdkType(): SdkTypeId = UnknownSdkType.getInstance("");

  @RequiresWriteLock
  override fun addJdk(sdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val existingSdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk as SdkBridgeImpl)

    if (existingSdkEntity != null) {
      throw IllegalStateException("SDK $sdk is already registered")
    }

    val sdkEntitySource = createEntitySourceForSdk()
    val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
    val homePathVfu = sdk.homePath.let { virtualFileUrlManager.fromUrl(it) }

    val roots = mutableListOf<SdkRoot>()
    for (type in OrderRootType.getAllPersistentTypes()) {
      sdk.rootProvider.getUrls(type).forEach { url ->
        roots.add(SdkRoot(virtualFileUrlManager.fromUrl(url), rootTypes[type.name()]!!))
      }
    }

    val additionalData = sdk.sdkAdditionalData
    val additionalDataAsString = if (additionalData != null) {
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      sdk.sdkType.saveAdditionalData(additionalData, additionalDataElement)
      JDOMUtil.write(additionalDataElement)
    } else ""

    val sdkEntity = SdkMainEntity(sdk.name, sdk.sdkType.name, homePathVfu, roots, additionalDataAsString, sdkEntitySource) {
      this.version = sdk.versionString
    }
    globalWorkspaceModel.updateModel("Adding SDK: ${sdk.name} ${sdk.sdkType}") {
      it.addEntity(sdkEntity)
      it.mutableSdkMap.addIfAbsent(sdkEntity, sdk)
    }
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkAdded(sdk)
  }

  @RequiresWriteLock
  override fun removeJdk(sdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    // Firing before the actual remove
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(sdk)

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val sdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk as SdkBridgeImpl)
                    ?: error("Can't find entity for SDK: $sdk")
    globalWorkspaceModel.updateModel("Removing SDK: ${sdk.name} ${sdk.sdkType}") {
      it.removeEntity(sdkEntity)
    }
    // For now SDK doesn't have disposable
    //if (sdk is Disposable) {
    //  Disposer.dispose(sdk)
    //}
  }

  @RequiresWriteLock
  override fun updateJdk(originalSdk: Sdk, modifiedSdk: Sdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val sdkMainEntity = (globalWorkspaceModel.currentSnapshot.entities(SdkMainEntity::class.java)
                           .firstOrNull { it.name == originalSdk.name && it.type == originalSdk.sdkType.name }
                         ?: error("SDK entity for bridge ${originalSdk.name} ${originalSdk.sdkType.name} doesn't exist"))

    val previousName: String = originalSdk.getName()
    val newName: String = modifiedSdk.getName()

    globalWorkspaceModel.updateModel("Updating SDK ${originalSdk.name} ${originalSdk.sdkType.name}") {
      modifiedSdk as SdkBridgeImpl
      it.modifyEntity(sdkMainEntity) {
        this.applyChangesFrom(modifiedSdk.getEntity())
      }
      (originalSdk as SdkBridgeImpl).applyChangesFrom(modifiedSdk)
    }

    if (previousName != newName) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      ApplicationManager.getApplication().getMessageBus().syncPublisher<Listener>(JDK_TABLE_TOPIC)
        .jdkNameChanged(originalSdk, previousName)
    }
  }

  override fun createSdk(name: String, sdkType: SdkTypeId): Sdk {
    return createSdkBridge(name, sdkType.name, "")
  }

  private fun createSdkBridge(name: String, type: String, homePath: String): SdkBridgeImpl {
    val emptySdkEntity = createEmptySdkEntity(name, type, homePath)
    return SdkBridgeImpl(emptySdkEntity)
  }


  companion object {
    private const val SDK_BRIDGE_MAPPING_ID = "intellij.sdk.bridge"

    val EntityStorage.sdkMap: ExternalEntityMapping<SdkBridgeImpl>
      get() = getExternalMapping(SDK_BRIDGE_MAPPING_ID)
    val MutableEntityStorage.mutableSdkMap: MutableExternalEntityMapping<SdkBridgeImpl>
      get() = getMutableExternalMapping(SDK_BRIDGE_MAPPING_ID)

    internal fun createEmptySdkEntity(name: String, type: String, homePath: String): SdkMainEntity.Builder {
      val sdkEntitySource = createEntitySourceForSdk()
      val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
      val homePathVfu = virtualFileUrlManager.fromUrl(homePath)
      return SdkMainEntity(name, type, homePathVfu, emptyList(), "", sdkEntitySource) as SdkMainEntity.Builder
    }

    private fun createEntitySourceForSdk(): EntitySource {
      val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
      val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(JpsGlobalEntitiesSerializers.SDK_FILE_NAME).absolutePath)
      return JpsGlobalFileEntitySource(globalLibrariesFile)
    }
  }
}