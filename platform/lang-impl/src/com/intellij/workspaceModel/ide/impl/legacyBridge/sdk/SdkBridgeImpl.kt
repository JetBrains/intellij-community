// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.projectRoots.*
import com.intellij.openapi.projectRoots.impl.ProjectJdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.PersistentOrderRootType
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot

class SdkBridgeImpl(private val sdkEntityBuilder: SdkMainEntity.Builder): ProjectJdk, Sdk {
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

  override fun getRootProvider(): RootProvider = SdkRootProviderBridge(sdkEntityBuilder)

  override fun getSdkModificator(): SdkModificator {
    return SdkModificatorBridgeImpl(sdkEntityBuilder)
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? {
    val additionalDataElement = JDOMUtil.load(sdkEntityBuilder.additionalData)
    return sdkEntityBuilder.getSdkType().loadAdditionalData(this, additionalDataElement)
  }

  override fun clone(): Any {
    val sdkEntityClone = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
    sdkEntityClone.applyChangesFrom(sdkEntityBuilder)
    return SdkBridgeImpl(sdkEntityClone)
  }

  internal fun applyChangesFrom(sdkBridge: SdkBridgeImpl) {
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