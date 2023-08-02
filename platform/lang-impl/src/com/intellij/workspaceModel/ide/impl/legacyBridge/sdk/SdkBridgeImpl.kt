// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.ProjectJdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.PersistentOrderRootType
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.util.EventDispatcher


// SdkBridgeImpl.clone called from com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.reset
// So I need to have such implementation and can't use implementation with SymbolicId and searching in storage
class SdkBridgeImpl(private var sdkEntityBuilder: SdkMainEntity.Builder) : ProjectJdk, RootProvider, Sdk {

  private val dispatcher = EventDispatcher.create(RootProvider.RootSetChangedListener::class.java)

  //var sdkId: SdkId = entityId

  //private val sdkEntityCachedValue = CachedValue { storage -> storage.resolve(sdkId) ?: error("Unexpected state") }
  //
  //private val sdkEntity: SdkMainEntity
  //  get() {
  //    return GlobalWorkspaceModel.getInstance().entityStorage.cachedValue(sdkEntityCachedValue)
  //  }

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    TODO("Not yet implemented")
  }

  override fun getSdkType(): SdkTypeId = sdkEntityBuilder.getSdkType()

  override fun getName(): String = sdkEntityBuilder.name

  override fun getVersionString(): String? = sdkEntityBuilder.version

  override fun getHomePath(): String = sdkEntityBuilder.homePath.url

  override fun getHomeDirectory(): VirtualFile? {
    val homePath = getHomePath()
    return StandardFileSystems.local().findFileByPath(homePath)
  }

  override fun getSdkModificator(): SdkModificator {
    return SdkModificatorBridgeImpl(sdkEntityBuilder)
  }

  override fun getRootProvider(): RootProvider = this

  override fun getUrls(rootType: OrderRootType): Array<String> {
    return sdkEntityBuilder.roots.filter { it.type.name == rootType.customName }
      .map { it.url.url }
      .toTypedArray()
  }

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    return sdkEntityBuilder.roots.filter { it.type.name == rootType.customName }
      .mapNotNull { it.url.virtualFile }
      .toTypedArray()
  }


  override fun addRootSetChangedListener(listener: RootSetChangedListener) {
    dispatcher.addListener(listener)
  }

  override fun removeRootSetChangedListener(listener: RootSetChangedListener) {
    dispatcher.removeListener(listener)
  }

  override fun addRootSetChangedListener(listener: RootSetChangedListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  internal fun fireRootSetChanged() {
    dispatcher.multicaster.rootSetChanged(this)
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? {
    val additionalData = sdkEntityBuilder.additionalData
    if (additionalData.isBlank()) return null
    val additionalDataElement = JDOMUtil.load(additionalData)
    return sdkEntityBuilder.getSdkType().loadAdditionalData(this, additionalDataElement)
  }

  override fun clone(): Any {
    // Called from com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.reset
    // So I need to have such implementation and can't use implementation with SymbolicId and searching in storage
    val sdkEntityClone = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
    sdkEntityClone.applyChangesFrom(sdkEntityBuilder)
    return SdkBridgeImpl(sdkEntityClone)
  }

  internal fun applyChangesFrom(sdkBridge: SdkBridgeImpl) {
    val modifiableEntity = sdkEntityBuilder as ModifiableWorkspaceEntityBase<*, *>
    if (modifiableEntity.diff != null && !modifiableEntity.modifiable.get()) {
      sdkEntityBuilder = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
    }
    sdkEntityBuilder.applyChangesFrom(sdkBridge.sdkEntityBuilder)
  }

  internal fun applyChangesTo(sdkEntity: SdkMainEntity.Builder) {
    sdkEntity.applyChangesFrom(sdkEntityBuilder)
  }

  internal fun getEntity(): SdkMainEntity = sdkEntityBuilder
}

internal fun SdkMainEntity.Builder.getSdkType(): SdkTypeId {
  return SdkType.findByName(type) ?: error("SDK type ${type} not found")
}

// At serialization, we have access only to `sdkRootName` so our roots contains only this names
// that's why we need to associate such names with the actual root type
internal val OrderRootType.customName: String
  get() {
    if (this is PersistentOrderRootType) {
      // Only `NativeLibraryOrderRootType` don't have rootName all other elements with it
      return sdkRootName ?: name()
    }
    else {
      // It's only for `DocumentationRootType` this is the only class that doesn't extend `PersistentOrderRootType`
      return name()
    }
  }

internal fun SdkMainEntity.Builder.applyChangesFrom(fromSdk: SdkMainEntity) {
  name = fromSdk.name
  type = fromSdk.type
  version = fromSdk.version
  homePath = fromSdk.homePath
  roots = fromSdk.roots as MutableList<SdkRoot>
  additionalData = fromSdk.additionalData
  entitySource = fromSdk.entitySource
}