// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Comparing
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.jps.serialization.impl.JpsGlobalEntitiesSerializers
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly

//TODO::
// [] Different tag name for library root and SDK roots e.g sources, classes
// [] Old version `2` constantly serialized for SDK
// [] `SdkConfigurationUtil.createSdk` broken API
// [] Strange to have type `SDK` but methods - `updateJDK`
// [] One more implementation of `ProjectJdkTable` => `JavaAwareProjectJdkTableImpl`
// [] SdkType.EP_NAME.addExtensionPointListener
// [] Additional data loading/saving
// [] The same problem as with FacetConfiguration we have 7 types of additional data for SDK so 7 entities
// [] Listener if we create entity manually, bridge has to be created
// [] Send events if entity created manually

internal val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }
class SdkTableBridgeImpl: SdkTableImplementationDelegate {

  override fun findSdkByName(name: String): Sdk? {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    val sdkEntity = currentSnapshot.entities(SdkEntity::class.java)
      .firstOrNull { Comparing.strEqual(name, it.name) } ?: return null
    return currentSnapshot.sdkMap.getDataByEntity(sdkEntity)
  }

  override fun getAllSdks(): List<Sdk> {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    return currentSnapshot.entities(SdkEntity::class.java)
      .mapNotNull { currentSnapshot.sdkMap.getDataByEntity(it) }
      .toList()
  }

  override fun createSdk(name: String, type: SdkTypeId, homePath: String?): Sdk {
    val emptySdkEntity = createEmptySdkEntity(name, type.name, homePath ?: "")
    return SdkBridgeImpl(emptySdkEntity)
  }

  override fun addNewSdk(sdk: Sdk) {
    sdk as SdkBridgeImpl
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val existingSdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk)

    if (existingSdkEntity != null) {
      throw IllegalStateException("SDK $sdk is already registered")
    }

    val sdkEntitySource = createEntitySourceForSdk()
    val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
    val homePathVfu = sdk.homePath.let { virtualFileUrlManager.fromUrl(it) }

    val roots = mutableListOf<SdkRoot>()
    for (type in OrderRootType.getAllPersistentTypes()) {
      sdk.rootProvider.getUrls(type).forEach { url ->
        roots.add(SdkRoot(virtualFileUrlManager.fromUrl(url), rootTypes[type.customName]!!))
      }
    }

    val additionalDataAsString = sdk.getRawSdkAdditionalData()
    val sdkEntity = SdkEntity(sdk.name, sdk.sdkType.name, homePathVfu, roots, additionalDataAsString, sdkEntitySource) {
      this.version = sdk.versionString
    }
    globalWorkspaceModel.updateModel("Adding SDK: ${sdk.name} ${sdk.sdkType}") {
      it.addEntity(sdkEntity)
      it.mutableSdkMap.addIfAbsent(sdkEntity, sdk)
    }
  }

  override fun removeSdk(sdk: Sdk) {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()

    // It's absolutely OK if we try to remove what does not yet exist in `ProjectJdkTable` SDK
    // E.g. org.jetbrains.idea.maven.actions.AddMavenDependencyQuickFixTest
    val sdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk as SdkBridgeImpl) ?: return
    globalWorkspaceModel.updateModel("Removing SDK: ${sdk.name} ${sdk.sdkType}") {
      it.removeEntity(sdkEntity)
    }
  }

  override fun updateSdk(originalSdk: Sdk, modifiedSdk: Sdk) {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val sdkEntity = (globalWorkspaceModel.currentSnapshot.entities(SdkEntity::class.java)
                           .firstOrNull { it.name == originalSdk.name && it.type == originalSdk.sdkType.name }
                     ?: error("SDK entity for bridge `${originalSdk.name}` `${originalSdk.sdkType.name}` doesn't exist"))


    globalWorkspaceModel.updateModel("Updating SDK ${originalSdk.name} ${originalSdk.sdkType.name}") {
      modifiedSdk as SdkBridgeImpl
      it.modifyEntity(sdkEntity) {
        this.applyChangesFrom(modifiedSdk.getEntity())
      }
      (originalSdk as SdkBridgeImpl).applyChangesFrom(modifiedSdk)
    }
    (originalSdk as SdkBridgeImpl).fireRootSetChanged()
  }

  @TestOnly
  @Suppress("RAW_RUN_BLOCKING")
  override fun saveOnDisk() {
    runBlocking {
      (JpsGlobalModelSynchronizer.getInstance() as JpsGlobalModelSynchronizerImpl).saveGlobalEntities()
    }
  }

  companion object {
    private const val SDK_BRIDGE_MAPPING_ID = "intellij.sdk.bridge"

    val EntityStorage.sdkMap: ExternalEntityMapping<SdkBridgeImpl>
      get() = getExternalMapping(SDK_BRIDGE_MAPPING_ID)
    val MutableEntityStorage.mutableSdkMap: MutableExternalEntityMapping<SdkBridgeImpl>
      get() = getMutableExternalMapping(SDK_BRIDGE_MAPPING_ID)

    internal fun createEmptySdkEntity(name: String, type: String, homePath: String): SdkEntity.Builder {
      val sdkEntitySource = createEntitySourceForSdk()
      val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
      val homePathVfu = virtualFileUrlManager.fromUrl(homePath)
      return SdkEntity(name, type, homePathVfu, emptyList(), "", sdkEntitySource) as SdkEntity.Builder
    }

    private fun createEntitySourceForSdk(): EntitySource {
      val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
      val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(JpsGlobalEntitiesSerializers.SDK_FILE_NAME).absolutePath)
      return JpsGlobalFileEntitySource(globalLibrariesFile)
    }
  }
}