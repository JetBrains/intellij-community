// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.getResolvedEelMachine
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.jps.entities.modifySdkEntity
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.JpsGlobalModelSynchronizer
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.getInternalEnvironmentName
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.mutableSdkMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

//TODO::
// [] Different tag name for library root and SDK roots e.g sources, classes
// [] Old version `2` constantly serialized for SDK
// [] `SdkConfigurationUtil.createSdk` broken API
// [] Strange to have type `SDK` but methods - `updateJDK`

private val rootTypes = ConcurrentFactoryMap.createMap<String, SdkRootTypeId> { SdkRootTypeId(it) }
@ApiStatus.Internal
class SdkTableBridgeImpl: SdkTableImplementationDelegate {

  override fun findSdkByName(name: String): Sdk? {
    val globalWorkspaceModels = GlobalWorkspaceModel.getInstancesBlocking()
    for (globalWorkspaceModel in globalWorkspaceModels) {
      val currentSnapshot = globalWorkspaceModel.currentSnapshot
      val sdkEntity = currentSnapshot.entities(SdkEntity::class.java)
                        .firstOrNull { Comparing.strEqual(name, it.name) } ?: continue
      return currentSnapshot.sdkMap.getDataByEntity(sdkEntity)
    }
    return null
  }

  override fun findSdkByName(name: String, eelMachine: EelMachine): Sdk? {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(eelMachine)
    val currentSnapshot = globalWorkspaceModel.currentSnapshot
    val sdkEntity = currentSnapshot.entities(SdkEntity::class.java)
                      .firstOrNull { Comparing.strEqual(name, it.name) } ?: return null
    return currentSnapshot.sdkMap.getDataByEntity(sdkEntity)
  }

  override fun getAllSdks(): List<Sdk> {
    val globalWorkspaceModels = GlobalWorkspaceModel.getInstancesBlocking()
    return globalWorkspaceModels.flatMap {
      val snapshot = it.currentSnapshot
      snapshot.entities(SdkEntity::class.java).mapNotNull { sdkEntity -> snapshot.sdkMap.getDataByEntity(sdkEntity) }
    }
  }

  override fun createSdk(name: String, type: SdkTypeId, homePath: String?): Sdk {
    val descriptor = homePath?.toNioPathOrNull()?.getEelDescriptor() ?: LocalEelDescriptor
    val environmentName = (descriptor.getResolvedEelMachine() ?: LocalEelMachine).getInternalEnvironmentName()
    return ProjectJdkImpl(name, type, homePath ?: "", null, environmentName)
  }

  override fun createSdk(name: String, type: SdkTypeId, environmentName: InternalEnvironmentName): Sdk {
    return ProjectJdkImpl(name, type, "", null, environmentName)
  }

  override fun addNewSdk(sdk: Sdk) {
    val delegateSdk = (sdk as ProjectJdkImpl).delegate as SdkBridgeImpl
    val machine = delegateSdk.homeDirectory?.toNioPath()?.getEelDescriptor()?.getResolvedEelMachine() ?: LocalEelMachine
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(machine)
    val existingSdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk)

    if (existingSdkEntity != null) {
      throw IllegalStateException("SDK $sdk is already registered")
    }

    val environmentName = machine.getInternalEnvironmentName()
    val sdkEntitySource = SdkBridgeImpl.createEntitySourceForSdk(environmentName)
    val virtualFileUrlManager = globalWorkspaceModel.getVirtualFileUrlManager()
    val homePathVfu = delegateSdk.homePath?.let { virtualFileUrlManager.getOrCreateFromUrl(it) }

    val roots = mutableListOf<SdkRoot>()
    for (type in OrderRootType.getSortedRootTypes()) {
      sdk.rootProvider.getUrls(type).forEach { url ->
        roots.add(SdkRoot(virtualFileUrlManager.getOrCreateFromUrl(url), rootTypes[type.customName]!!))
      }
    }

    val additionalDataAsString = delegateSdk.getRawSdkAdditionalData()
    val sdkEntity = SdkEntity(sdk.name, sdk.sdkType.name, roots, additionalDataAsString, sdkEntitySource) {
      this.homePath = homePathVfu
      this.version = sdk.versionString
    }
    globalWorkspaceModel.updateModel("Adding SDK: ${sdk.name} ${sdk.sdkType}") {
      val addedEntity = it.addEntity(sdkEntity)
      it.mutableSdkMap.addIfAbsent(addedEntity, sdk)
    }
  }

  override fun removeSdk(sdk: Sdk) {
    val descriptor = sdk.homeDirectory?.toNioPath()?.getEelDescriptor() ?: LocalEelDescriptor
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(descriptor.getResolvedEelMachine() ?: LocalEelMachine)

    // It's absolutely OK if we try to remove what does not yet exist in `ProjectJdkTable` SDK
    // E.g. org.jetbrains.idea.maven.actions.AddMavenDependencyQuickFixTest
    val sdkEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(sdk as ProjectJdkImpl) ?: return
    globalWorkspaceModel.updateModel("Removing SDK: ${sdk.name} ${sdk.sdkType}") {
      it.removeEntity(sdkEntity)
    }
  }

  override fun updateSdk(originalSdk: Sdk, modifiedSdk: Sdk) {
    modifiedSdk as ProjectJdkImpl
    originalSdk as ProjectJdkImpl
    val descriptor = modifiedSdk.homeDirectory?.toNioPath()?.getEelDescriptor() ?: LocalEelDescriptor
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(descriptor.getResolvedEelMachine() ?: LocalEelMachine)
    val sdkEntity = (globalWorkspaceModel.currentSnapshot.entities(SdkEntity::class.java)
                       .firstOrNull { it.name == originalSdk.name && it.type == originalSdk.sdkType.name }
                     ?: error("SDK entity for bridge `${originalSdk.name}` `${originalSdk.sdkType.name}` doesn't exist"))


    val modifiedSdkBridge = modifiedSdk.delegate as SdkBridgeImpl
    val originalSdkBridge = originalSdk.delegate as SdkBridgeImpl
    globalWorkspaceModel.updateModel("Updating SDK ${originalSdk.name} ${originalSdk.sdkType.name}") {
      it.modifySdkEntity(sdkEntity) {
        this.applyChangesFrom(modifiedSdkBridge.getEntityBuilder())
      }
      originalSdkBridge.applyChangesFrom(modifiedSdkBridge)
    }
    originalSdkBridge.fireRootSetChanged()
  }

  @TestOnly
  @Suppress("RAW_RUN_BLOCKING")
  override fun saveOnDisk() {
    runBlocking(Dispatchers.Default) {
      (serviceAsync<JpsGlobalModelSynchronizer>() as JpsGlobalModelSynchronizerImpl).saveSdkEntities()
    }
  }
}