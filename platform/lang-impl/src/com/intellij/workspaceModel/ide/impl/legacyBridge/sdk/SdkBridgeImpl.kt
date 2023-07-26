// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot

class SdkBridgeImpl(private val sdkEntityBuilder: SdkMainEntity.Builder): Sdk {
  override fun <T : Any?> getUserData(key: Key<T>): T? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    TODO("Not yet implemented")
  }

  override fun getSdkType(): SdkTypeId {
    TODO("Not yet implemented")
  }

  override fun getName(): String {
    TODO("Not yet implemented")
  }

  override fun getVersionString(): String {
    TODO("Not yet implemented")
  }

  override fun getHomePath(): String {
    TODO("Not yet implemented")
  }

  override fun getHomeDirectory(): VirtualFile {
    TODO("Not yet implemented")
  }

  override fun getRootProvider(): RootProvider {
    TODO("Not yet implemented")
  }

  override fun getSdkModificator(): SdkModificator {
    TODO("Not yet implemented")
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? {
    TODO("Not yet implemented")
  }

  override fun clone(): Any {
    TODO("Not yet implemented")
  }

  internal fun applyChangesFrom(sdkBridge: SdkBridgeImpl) {
    sdkEntityBuilder.applyChangesFrom(sdkBridge.sdkEntityBuilder)
  }

  internal fun applyChangesTo(sdkEntity: SdkMainEntity.Builder) {
    sdkEntity.applyChangesFrom(sdkEntityBuilder)
  }

  internal fun getEntity(): SdkMainEntity = sdkEntityBuilder
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