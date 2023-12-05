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
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus


// SdkBridgeImpl.clone called from com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.reset
// So I need to have such implementation and can't use implementation with SymbolicId and searching in storage
class SdkBridgeImpl(private var sdkEntityBuilder: SdkEntity.Builder) : UserDataHolderBase(), ProjectJdk, RootProvider, Sdk {

  private var additionalData: SdkAdditionalData? = null
  private val dispatcher = EventDispatcher.create(RootSetChangedListener::class.java)

  init {
    reloadAdditionalData()
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
    return SdkModificatorBridgeImpl(sdkEntityBuilder, this)
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
    if (dispatcher.hasListeners()) {
      dispatcher.multicaster.rootSetChanged(this)
    }
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? = additionalData

  override fun clone(): SdkBridgeImpl {
    val sdkEntityClone = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
    sdkEntityClone.applyChangesFrom(sdkEntityBuilder)
    return SdkBridgeImpl(sdkEntityClone)
  }

  @ApiStatus.Internal
  override fun changeType(newType: SdkTypeId, additionalDataElement: Element?) {
    ThreadingAssertions.assertWriteAccess()
    sdkModificator.let {
      it as SdkModificatorBridgeImpl
      it.setType(newType.name)
      it.sdkAdditionalData = if (additionalDataElement != null) newType.loadAdditionalData(this, additionalDataElement) else null
      it.commitChanges()
    }
  }

  internal fun getRawSdkAdditionalData(): String = sdkEntityBuilder.additionalData

  internal fun applyChangesFrom(sdkBridge: SdkBridgeImpl) {
    val modifiableEntity = sdkEntityBuilder as ModifiableWorkspaceEntityBase<*, *>
    if (modifiableEntity.diff != null && !modifiableEntity.modifiable.get()) {
      sdkEntityBuilder = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")
    }
    sdkEntityBuilder.applyChangesFrom(sdkBridge.sdkEntityBuilder)
    reloadAdditionalData()
  }

  internal fun reloadAdditionalData() {
    val rawAdditionalData = sdkEntityBuilder.additionalData
    if (rawAdditionalData.isNotBlank()) {
      val additionalDataElement = JDOMUtil.load(rawAdditionalData)
      additionalData = sdkEntityBuilder.getSdkType().loadAdditionalData(this, additionalDataElement)
    }
  }

  internal fun applyChangesTo(sdkEntity: SdkEntity.Builder) {
    sdkEntity.applyChangesFrom(sdkEntityBuilder)
  }

  internal fun getEntity(): SdkEntity = sdkEntityBuilder

  override fun toString(): String {
    return "$name $versionString ($homePath )"
  }
}

internal fun SdkEntity.Builder.getSdkType(): SdkTypeId {
  return ProjectJdkTable.getInstance().getSdkTypeByName(type)
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

internal fun SdkEntity.Builder.applyChangesFrom(fromSdk: SdkEntity) {
  name = fromSdk.name
  type = fromSdk.type
  version = fromSdk.version
  homePath = fromSdk.homePath
  val sdkRoots = fromSdk.roots.mapTo(mutableListOf()) { SdkRoot(it.url, it.type) }
  roots = sdkRoots
  additionalData = fromSdk.additionalData
  entitySource = fromSdk.entitySource
}