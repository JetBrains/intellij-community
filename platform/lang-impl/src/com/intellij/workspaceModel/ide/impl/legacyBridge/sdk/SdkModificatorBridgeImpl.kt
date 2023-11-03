// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SdkMainEntity
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.jps.serialization.impl.ELEMENT_ADDITIONAL
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkTableBridgeImpl.Companion.sdkMap
import org.jdom.Element

class SdkModificatorBridgeImpl(private val originalEntity: SdkMainEntity.Builder,
                               private val originalSdkBridge: SdkBridgeImpl) : SdkModificator {

  private var isCommitted = false
  private var additionalData: SdkAdditionalData? = null
  private val modifiedSdkEntity: SdkMainEntity.Builder = SdkTableBridgeImpl.createEmptySdkEntity("", "", "")

  init {
    modifiedSdkEntity.applyChangesFrom(originalEntity)
    if (modifiedSdkEntity.additionalData.isNotEmpty()) {
      val additionalDataElement = JDOMUtil.load(modifiedSdkEntity.additionalData)
      additionalData = originalSdkBridge.getSdkType().loadAdditionalData(originalSdkBridge, additionalDataElement)
    }
  }

  override fun getName(): String = modifiedSdkEntity.name

  override fun setName(name: String) {
    modifiedSdkEntity.name = name
  }

  override fun getHomePath(): String = modifiedSdkEntity.homePath.url

  override fun setHomePath(path: String?) {
    val globalInstance = VirtualFileUrlManager.getGlobalInstance()
    val homePath = globalInstance.fromUrl(path ?: "")
    modifiedSdkEntity.homePath = homePath
  }

  // TODO:: I don't use versionDefined `com.intellij.openapi.projectRoots.impl.ProjectJdkImpl.myVersionDefined`
  override fun getVersionString(): String? {
    //if (modifiedSdkEntity.version == null) {
    //  val homePath = getHomePath()
    //  if (homePath != null && !homePath.isEmpty()) {
    //    versionString = modifiedSdkEntity.getSdkType().getVersionString(this)
    //  }
    //}
    return modifiedSdkEntity.version
  }

  override fun setVersionString(versionString: String?) {
    modifiedSdkEntity.version = versionString
  }

  override fun getSdkAdditionalData(): SdkAdditionalData? {
    return additionalData
    //val additionalDataElement = JDOMUtil.load(modifiedSdkEntity.additionalData)
    //return modifiedSdkEntity.getSdkType().loadAdditionalData(modifiedSdk, additionalDataElement)
  }

  override fun setSdkAdditionalData(additionalData: SdkAdditionalData?) {
    this.additionalData =  additionalData
    //val additionalDataAsString = if (additionalData != null) {
    //  val additionalDataElement = Element(ELEMENT_ADDITIONAL)
    //  modifiedSdkEntity.getSdkType().saveAdditionalData(additionalData, additionalDataElement)
    //  JDOMUtil.write(additionalDataElement)
    //} else ""
    //modifiedSdkEntity.additionalData = additionalDataAsString
  }

  override fun getRoots(rootType: OrderRootType): Array<VirtualFile> {
    return modifiedSdkEntity.roots.filter { it.type.name == rootType.customName }
      .mapNotNull { it.url.virtualFile }
      .toTypedArray()
  }

  override fun addRoot(root: VirtualFile, rootType: OrderRootType) {
    val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
    modifiedSdkEntity.roots.add(
      SdkRoot(virtualFileUrlManager.fromUrl(root.url), rootTypes[rootType.customName]!!)
    )
  }

  override fun removeRoot(root: VirtualFile, rootType: OrderRootType) {
    val roots = modifiedSdkEntity.roots
    roots.removeIf { it.url.url == root.url && it.type.name == rootType.customName }
  }

  override fun removeRoots(rootType: OrderRootType) {
    val roots = modifiedSdkEntity.roots
    roots.removeIf { it.type.name == rootType.customName }
  }

  override fun removeAllRoots() {
    modifiedSdkEntity.roots = mutableListOf()
  }

  @RequiresWriteLock
  override fun commitChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (isCommitted) error("Modification already completed")

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    // In some cases we create SDK in air and need to modify it somehow e.g
    // com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.createSdkInternal
    // so it's OK that entity may not be in storage

    modifiedSdkEntity.additionalData = if (additionalData != null) {
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      modifiedSdkEntity.getSdkType().saveAdditionalData(additionalData!!, additionalDataElement)
      JDOMUtil.write(additionalDataElement)
    } else ""

    //globalWorkspaceModel.currentSnapshot.resolve(originalEntity.symbolicId)
    // Update only entity existing in the storage
    val existingEntity = globalWorkspaceModel.currentSnapshot.sdkMap.getFirstEntity(originalSdkBridge) as? SdkMainEntity
    existingEntity?.let { entity ->
      globalWorkspaceModel.updateModel("Modifying SDK ${originalEntity.symbolicId}") {
        it.modifyEntity(entity) {
          this.applyChangesFrom(modifiedSdkEntity)
        }
      }
    }

    originalEntity.applyChangesFrom(modifiedSdkEntity)
    originalSdkBridge.reloadAdditionalData()
    if (existingEntity != null) originalSdkBridge.fireRootSetChanged()
    isCommitted = true
  }

  override fun applyChangesWithoutWriteAction() {
    if (isCommitted) error("Modification already completed")

    modifiedSdkEntity.additionalData = if (additionalData != null) {
      val additionalDataElement = Element(ELEMENT_ADDITIONAL)
      modifiedSdkEntity.getSdkType().saveAdditionalData(additionalData!!, additionalDataElement)
      JDOMUtil.write(additionalDataElement)
    } else ""

    originalEntity.applyChangesFrom(modifiedSdkEntity)
    originalSdkBridge.reloadAdditionalData()
    isCommitted = true
  }

  override fun isWritable(): Boolean = !isCommitted
}